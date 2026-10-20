package dev.rajce.ketchupZombies.shop;

import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import dev.rajce.ketchupZombies.weapons.WeaponItems;
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

    public ShopMenu(KetchupZombies plugin, WeaponItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void weapons(Player player) {
        weapons(player, null);
    }

    public void weapons(Player player, List<WeaponId> stock) {
        Gui gui = new Gui(plugin, 6, "&8Weapon shop");
        int slot = 0;
        for (WeaponId weapon : WeaponId.values()) {
            if (stock != null && !stock.contains(weapon)) {
                continue;
            }
            PluginConfig.Weapon stats = stats(weapon);
            if (stats.price < 0 || slot >= 45) {
                continue;
            }
            int price = stats.price;
            WeaponId sold = weapon;
            gui.setItem(slot++, ItemBuilder.of(weapon.material()).name("&e" + weapon.title()).lore("&7Price: &f" + price, "&7Damage: &f" + stats.damage, "&aLeft click: &7buy.").build(), event -> buyWeapon(player, sold, price, stock));
        }
        gui.setItem(49, ItemBuilder.of(Material.GOLD_NUGGET).name("&6Gold").lore("&7You have &f" + plugin.getProfiles().gold(player.getUniqueId())).build(), event -> weapons(player, stock));
        gui.open(player);
    }

    public List<WeaponId> defaultWeapons() {
        List<WeaponId> weapons = new ArrayList<>();
        for (WeaponId weapon : WeaponId.values()) {
            if (stats(weapon).price >= 0) {
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
        Gui gui = new Gui(plugin, 3, "&8Perks");
        PluginConfig.Perks perks = plugin.config().perks;
        boolean power = plugin.getProfiles().power(player.getUniqueId());
        gui.setItem(4, ItemBuilder.of(power ? Material.REDSTONE_LAMP : Material.LEVER).name("&ePower").lore(power ? "&aOn" : "&cOff. Perks stay locked.", "&7Price: &f" + plugin.config().power.price, "&aLeft click: &7flip.").build(), event -> flipPower(player));
        button(gui, player, 10, Material.COOKIE, "Fast revive", Profiles.FAST_REVIVE, perks.fastRevive);
        button(gui, player, 11, Material.SUGAR, "Speed", Profiles.SPEED, perks.speed);
        button(gui, player, 12, Material.REDSTONE, "Quick fire", Profiles.QUICK_FIRE, perks.quickFire);
        button(gui, player, 13, Material.GOLD_NUGGET, "Extra health", Profiles.EXTRA_HEALTH, perks.extraHealth);
        button(gui, player, 14, Material.IRON_INGOT, "Extra weapon", Profiles.EXTRA_WEAPON, perks.extraWeapon);
        button(gui, player, 15, Material.GHAST_TEAR, "Frozen bullets", Profiles.FROZEN_BULLETS, perks.frozenBullets);
        button(gui, player, 16, Material.BLAZE_POWDER, "Flame bullets", Profiles.FLAME_BULLETS, perks.flameBullets);
        gui.open(player);
    }

    private void button(Gui gui, Player player, int slot, Material material, String title, String id, int price) {
        boolean owned = plugin.getProfiles().perk(player.getUniqueId(), id);
        gui.setItem(slot, ItemBuilder.of(material).name((owned ? "&a" : "&e") + title).lore("&7Price: &f" + price, owned ? "&aOwned." : "&aLeft click: &7buy.").build(), event -> buyPerk(player, id, title, price));
    }

    private void buyWeapon(Player player, WeaponId weapon, int price, List<WeaponId> stock) {
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        player.getInventory().addItem(items.create(weapon, stats(weapon), 0));
        plugin.getWeaponCombat().hud(player);
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", weapon.title(), "price", Integer.toString(price)));
        plugin.getGames().gold(player);
        weapons(player, stock);
    }

    private void buyArmor(Player player, ArmorOffer offer, List<ArmorOffer> offers) {
        List<ItemStack> pieces = pieces(offer);
        if (pieces.isEmpty()) {
            plugin.getMessageManager().send(player, "shop.armor-empty");
            return;
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
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), offer.price())) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        for (ItemStack piece : pieces) {
            if (rank(piece) > rank(worn(player, piece))) {
                equip(player, piece);
            }
        }
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", offer.label(), "price", Integer.toString(offer.price())));
        plugin.getGames().gold(player);
        armor(player, offers);
    }

    private void buyPerk(Player player, String id, String title, int price) {
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
        if (Profiles.SPEED.equals(id)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
        if (Profiles.EXTRA_HEALTH.equals(id)) {
            extraHealth(player, true);
        }
        plugin.getMessageManager().send(player, "shop.bought", Map.of("item", title, "price", Integer.toString(price)));
        plugin.getGames().gold(player);
        perks(player);
    }

    private void flipPower(Player player) {
        int price = plugin.config().power.price;
        if (!plugin.getProfiles().power(player.getUniqueId()) && !plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        boolean on = !plugin.getProfiles().power(player.getUniqueId());
        plugin.getProfiles().power(player.getUniqueId(), on);
        plugin.getMessageManager().send(player, on ? "shop.power-on" : "shop.power-off-done");
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

    private PluginConfig.Weapon stats(WeaponId weapon) {
        PluginConfig.Weapons weapons = plugin.config().weapons;
        return switch (weapon) {
            case KNIFE -> weapons.knife;
            case PISTOL -> weapons.pistol;
            case RIFLE -> weapons.rifle;
            case SHOTGUN -> weapons.shotgun;
            case SNIPER -> weapons.sniper;
            case ROCKET -> weapons.rocketLauncher;
            case FLAMETHROWER -> weapons.flamethrower;
            case DART -> weapons.blowDart;
            case SOAKER -> weapons.zombieSoaker;
            case ZAPPER -> weapons.zombieZapper;
            case PUNCHER -> weapons.puncher;
            case ELDER -> weapons.elderGun;
            case DIGGER -> weapons.goldDigger;
        };
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
        String set = offer.set().toLowerCase(Locale.ROOT);
        for (String piece : offer.pieces()) {
            Material material = material(set, piece.toLowerCase(Locale.ROOT));
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
            default -> Material.LEATHER_CHESTPLATE;
        };
    }

    private static Material material(String set, String piece) {
        String name = switch (set) {
            case "gold" -> "GOLDEN_";
            case "iron" -> "IRON_";
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
