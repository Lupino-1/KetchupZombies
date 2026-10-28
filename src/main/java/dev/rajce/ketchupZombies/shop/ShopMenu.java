package dev.rajce.ketchupZombies.shop;

import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.config.SoundKit;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.weapons.WeaponCatalog;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import dev.rajce.ketchupZombies.weapons.WeaponItems;
import dev.rajce.ketchupZombies.weapons.WeaponSlots;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopMenu {

    private final KetchupZombies plugin;
    private final WeaponItems items;
    private final PerkMenu perks;

    public ShopMenu(KetchupZombies plugin, WeaponItems items) {
        this.plugin = plugin;
        this.items = items;
        this.perks = new PerkMenu(plugin);
    }

    private PluginConfig cfg(Player player) {
        return plugin.config(player);
    }

    public void weapons(Player player) {
        weapons(player, null);
    }

    public void weapons(Player player, List<WeaponId> stock) {
        PluginConfig config = cfg(player);
        Gui gui = new Gui(plugin, 6, "&8Weapon shop");
        int slot = 0;
        for (WeaponId weapon : WeaponId.values()) {
            if (stock != null && !stock.contains(weapon)) {
                continue;
            }
            PluginConfig.Weapon stats = WeaponCatalog.base(config, weapon);
            if (stats.price < 0 || slot >= 45) {
                continue;
            }
            int price = stats.price;
            WeaponId sold = weapon;
            String label = WeaponCatalog.displayName(config, weapon, 0);
            ItemStack icon = WeaponCatalog.stack(plugin, weapon, 0);
            gui.setItem(slot++, ItemBuilder.of(icon).name("&e" + label).lore("&7Price: &f" + price, "&7Damage: &f" + stats.damage, "&aLeft click: &7buy.").build(), event -> buyWeapon(player, sold, price, stock));
        }
        gui.setItem(49, ItemBuilder.of(Material.GOLD_NUGGET).name("&6Gold").lore("&7You have &f" + plugin.getProfiles().gold(player.getUniqueId())).build(), event -> weapons(player, stock));
        gui.open(player);
    }

    public void wallWeapon(Player player, WeaponId weapon) {
        PluginConfig config = cfg(player);
        boolean onWeapon = WeaponSlots.holdingGun(plugin, player, weapon);
        boolean onEmpty = WeaponSlots.holdingEmptyGun(plugin, player);
        if (owns(player, weapon)) {
            if (!onWeapon && !onEmpty) {
                plugin.getMessageManager().send(player, "shop.gun-full");
                return;
            }
            PluginConfig.Weapon stats = WeaponCatalog.base(config, weapon);
            if (stats.magazine < 0) {
                plugin.getMessageManager().send(player, "shop.owned");
                return;
            }
            int price = config.shops.ammoRefill;
            plugin.getMessageManager().send(player, "shop.owned-refill", Map.of(
                    "item", WeaponCatalog.displayName(config, weapon, 0),
                    "price", Integer.toString(price)
            ));
            refill(player, weapon);
            return;
        }
        if (!canTakeGun(player, weapon)) {
            plugin.getMessageManager().send(player, "shop.gun-full");
            return;
        }
        PluginConfig.Weapon stats = WeaponCatalog.base(config, weapon);
        int price = Math.max(0, stats.price);
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        giveWeapon(player, weapon);
        SoundKit.play(player, player.getLocation(), config.sounds.shopBuy);
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", WeaponCatalog.displayName(config, weapon, 0), "price", Integer.toString(price)));
        plugin.getGames().gold(player);
    }

    public void wallArmor(Player player, ArmorOffer offer) {
        buyArmorOffer(player, offer);
    }

    public List<WeaponId> defaultWeapons() {
        List<WeaponId> weapons = new ArrayList<>();
        for (WeaponId weapon : WeaponId.values()) {
            if (WeaponCatalog.base(plugin, weapon).price >= 0) {
                weapons.add(weapon);
            }
        }
        return weapons;
    }

    public void armor(Player player, List<ArmorOffer> offers) {
        Gui gui = new Gui(plugin, 6, "&8Armor shop");
        int slot = 0;
        for (ArmorOffer offer : offers) {
            if (slot >= 45) {
                break;
            }
            ArmorOffer chosen = offer;
            gui.setItem(slot++, ItemBuilder.of(icon(offer.set())).name("&e" + offer.label()).lore("&7Price: &f" + offer.price(), "&7Better armor stays on.", "&aLeft click: &7buy.").build(), event -> buyArmor(player, chosen, offers));
        }
        gui.setItem(49, ItemBuilder.of(Material.GOLD_NUGGET).name("&6Gold").lore("&7You have &f" + plugin.getProfiles().gold(player.getUniqueId())).build(), event -> armor(player, offers));
        gui.open(player);
    }

    public List<ArmorOffer> defaults() {
        PluginConfig.Armor armor = plugin.config().armor;
        List<ArmorOffer> offers = new ArrayList<>();
        add(offers, armor.leatherTop);
        add(offers, armor.leatherBottom);
        add(offers, armor.goldTop);
        add(offers, armor.ironTop);
        add(offers, armor.ironBottom);
        return offers;
    }

    public void perks(Player player) {
        perks.open(player);
    }

    private static Material materialOf(String id) {
        return WeaponSlots.perkMaterial(id);
    }

    private void buyWeapon(Player player, WeaponId weapon, int price, List<WeaponId> stock) {
        if (!canTakeGun(player, weapon)) {
            plugin.getMessageManager().send(player, "shop.gun-full");
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        giveWeapon(player, weapon);
        SoundKit.play(player, player.getLocation(), cfg(player).sounds.shopBuy);
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", WeaponCatalog.displayName(cfg(player), weapon, 0), "price", Integer.toString(price)));
        plugin.getGames().gold(player);
        weapons(player, stock);
    }

    private boolean canTakeGun(Player player, WeaponId weapon) {
        if (weapon.meleeSlot()) {
            return true;
        }
        return WeaponSlots.canBuyGun(plugin, player);
    }

    public void giveWeapon(Player player, WeaponId weapon) {
        ItemStack created = items.create(weapon, WeaponCatalog.base(cfg(player), weapon), 0);
        // Knife or Puncher → slot 0 (Puncher replaces the sword).
        if (weapon.meleeSlot()) {
            WeaponSlots.placeMelee(plugin, player, created);
            WeaponSlots.fillEmpties(plugin, player);
            plugin.getWeaponCombat().hud(player);
            plugin.getWeaponCombat().held(player);
            return;
        }
        if (!WeaponSlots.canBuyGun(plugin, player)) {
            plugin.getMessageManager().send(player, "shop.gun-full");
            return;
        }
        WeaponSlots.placeGun(plugin, player, created);
        WeaponSlots.enforce(plugin, player);
        plugin.getWeaponCombat().bindAmmo(player, created);
        plugin.getWeaponCombat().hud(player);
        plugin.getWeaponCombat().held(player);
    }

    private void refill(Player player, WeaponId weapon) {
        int slot = findSlot(player, weapon);
        if (slot < 0) {
            return;
        }
        ItemStack held = player.getInventory().getItem(slot);
        PluginConfig config = cfg(player);
        PluginConfig.Weapon stats = WeaponCatalog.effective(config, weapon, held, items);
        if (stats.magazine < 0) {
            plugin.getMessageManager().send(player, "shop.owned");
            return;
        }
        int price = config.shops.ammoRefill;
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        int clip = Math.max(0, stats.magazine);
        items.write(held, clip, Math.max(0, stats.ammo - clip), null, 0L);
        items.paint(held);
        player.getInventory().setItem(slot, held);
        plugin.getWeaponCombat().bindAmmo(player, held);
        plugin.getWeaponCombat().hud(player);
        SoundKit.play(player, player.getLocation(), config.sounds.ammoRefill);
        plugin.getMessageManager().send(player, "shop.refilled", Map.of("item", WeaponCatalog.displayName(config, weapon, 0), "price", Integer.toString(price)));
        plugin.getGames().gold(player);
    }

    public boolean owns(Player player, WeaponId weapon) {
        return findSlot(player, weapon) >= 0;
    }

    /** Gun slot that already holds this weapon, or -1. Selected hotbar slot does not matter. */
    private int findSlot(Player player, WeaponId weapon) {
        var inventory = player.getInventory();
        int guns = WeaponSlots.gunCount(plugin, player);
        for (int slot = WeaponSlots.GUN_START; slot < WeaponSlots.GUN_START + guns; slot++) {
            if (items.id(inventory.getItem(slot)) == weapon) {
                return slot;
            }
        }
        if (items.id(inventory.getItem(WeaponSlots.KNIFE)) == weapon) {
            return WeaponSlots.KNIFE;
        }
        return -1;
    }

    private void buyArmor(Player player, ArmorOffer offer, List<ArmorOffer> offers) {
        if (buyArmorOffer(player, offer)) {
            armor(player, offers);
        }
    }

    private boolean buyArmorOffer(Player player, ArmorOffer offer) {
        List<ItemStack> pieces = pieces(offer);
        if (pieces.isEmpty()) {
            plugin.getMessageManager().send(player, "shop.armor-empty");
            return false;
        }
        boolean upgrade = false;
        for (ItemStack piece : pieces) {
            if (rank(piece) > rank(worn(player, piece))) {
                upgrade = true;
                break;
            }
        }
        if (!upgrade) {
            plugin.getMessageManager().send(player, "shop.armor-kept");
            return false;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), offer.price())) {
            plugin.getMessageManager().send(player, "shop.poor");
            return false;
        }
        for (ItemStack piece : pieces) {
            if (rank(piece) > rank(worn(player, piece))) {
                equip(player, piece);
            }
        }
        SoundKit.play(player, player.getLocation(), cfg(player).sounds.shopBuy);
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", offer.label(), "price", Integer.toString(offer.price())));
        plugin.getGames().gold(player);
        return true;
    }

    public void buyPerk(Player player, String id, String title, int price) {
        if (!plugin.getProfiles().power(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "shop.power-off");
            return;
        }
        if (plugin.getProfiles().perk(player.getUniqueId(), id)) {
            plugin.getMessageManager().send(player, "shop.owned");
            return;
        }
        if (plugin.getProfiles().perks(player.getUniqueId()).size() >= 3) {
            plugin.getMessageManager().send(player, "shop.perk-full");
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        plugin.getProfiles().addPerk(player.getUniqueId(), id);
        WeaponSlots.placePerk(plugin, player, id, title, materialOf(id));
        if (Profiles.SPEED.equals(id)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
        if (Profiles.EXTRA_HEALTH.equals(id)) {
            extraHealth(player, true);
        }
        if (Profiles.EXTRA_WEAPON.equals(id)) {
            WeaponSlots.fillEmpties(plugin, player);
            plugin.getMessageManager().send(player, "shop.gun-slot");
        }
        SoundKit.play(player, player.getLocation(), cfg(player).sounds.shopBuy);
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", title, "price", Integer.toString(price)));
        plugin.getGames().gold(player);
        perks(player);
    }

    public void sellPerk(Player player, String id, String title, int price) {
        if (!plugin.getProfiles().perk(player.getUniqueId(), id)) {
            return;
        }
        plugin.getProfiles().removePerk(player.getUniqueId(), id);
        plugin.getProfiles().addGold(player.getUniqueId(), price);
        if (Profiles.SPEED.equals(id)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        }
        if (Profiles.EXTRA_HEALTH.equals(id)) {
            extraHealth(player, false);
        }
        WeaponSlots.syncPerks(plugin, player);
        if (Profiles.EXTRA_WEAPON.equals(id)) {
            WeaponSlots.enforce(plugin, player);
        }
        SoundKit.play(player, player.getLocation(), cfg(player).sounds.shopBuy);
        plugin.getMessageManager().send(player, "shop.sold", Map.of("item", title, "price", Integer.toString(price)));
        plugin.getGames().gold(player);
        perks(player);
    }

    private void extraHealth(Player player, boolean grant) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "extra-health");
        attribute.getModifiers().stream().filter(modifier -> key.equals(modifier.getKey())).forEach(attribute::removeModifier);
        if (grant) {
            attribute.addModifier(new org.bukkit.attribute.AttributeModifier(key, 10, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
    }

    private static void add(List<ArmorOffer> offers, PluginConfig.Bundle bundle) {
        List<String> pieces = new ArrayList<>();
        for (String piece : bundle.pieces.split(",")) {
            if (!piece.isBlank()) {
                pieces.add(piece.trim().toLowerCase(Locale.ROOT));
            }
        }
        offers.add(new ArmorOffer(bundle.set, pieces, bundle.price));
    }

    public static List<ItemStack> pieces(ArmorOffer offer) {
        List<ItemStack> items = new ArrayList<>();
        for (String slot : ArmorOffer.SLOT_ORDER) {
            String set = offer.set(slot);
            if (set == null) {
                continue;
            }
            Material material = material(set, slot);
            if (material != null) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
                items.add(item);
            }
        }
        return items;
    }

    private static ItemStack worn(Player player, ItemStack offer) {
        String name = offer.getType().name();
        if (name.endsWith("_HELMET")) {
            return player.getInventory().getHelmet();
        }
        if (name.endsWith("_CHESTPLATE")) {
            return player.getInventory().getChestplate();
        }
        if (name.endsWith("_LEGGINGS")) {
            return player.getInventory().getLeggings();
        }
        if (name.endsWith("_BOOTS")) {
            return player.getInventory().getBoots();
        }
        return null;
    }

    private static void equip(Player player, ItemStack offer) {
        String name = offer.getType().name();
        if (name.endsWith("_HELMET")) {
            player.getInventory().setHelmet(offer);
        } else if (name.endsWith("_CHESTPLATE")) {
            player.getInventory().setChestplate(offer);
        } else if (name.endsWith("_LEGGINGS")) {
            player.getInventory().setLeggings(offer);
        } else if (name.endsWith("_BOOTS")) {
            player.getInventory().setBoots(offer);
        }
    }

    private static int rank(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        String name = item.getType().name();
        if (name.startsWith("LEATHER_")) {
            return 1;
        }
        if (name.startsWith("GOLDEN_") || name.startsWith("CHAINMAIL_")) {
            return 2;
        }
        if (name.startsWith("IRON_")) {
            return 3;
        }
        if (name.startsWith("DIAMOND_")) {
            return 4;
        }
        if (name.startsWith("NETHERITE_")) {
            return 5;
        }
        return 0;
    }

    private static Material icon(String set) {
        return switch (set.toLowerCase(Locale.ROOT)) {
            case "gold" -> Material.GOLDEN_CHESTPLATE;
            case "iron" -> Material.IRON_CHESTPLATE;
            case "diamond" -> Material.DIAMOND_CHESTPLATE;
            case "netherite" -> Material.NETHERITE_CHESTPLATE;
            default -> Material.LEATHER_CHESTPLATE;
        };
    }

    private static Material material(String set, String piece) {
        String name = switch (set) {
            case "gold" -> "GOLDEN_";
            case "iron" -> "IRON_";
            case "diamond" -> "DIAMOND_";
            case "netherite" -> "NETHERITE_";
            default -> "LEATHER_";
        } + switch (piece) {
            case "helmet" -> "HELMET";
            case "chestplate" -> "CHESTPLATE";
            case "leggings" -> "LEGGINGS";
            case "boots" -> "BOOTS";
            default -> "";
        };
        if (name.endsWith("_")) {
            return null;
        }
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
