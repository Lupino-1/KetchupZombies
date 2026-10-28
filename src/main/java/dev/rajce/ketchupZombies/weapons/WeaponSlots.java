package dev.rajce.ketchupZombies.weapons;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.shop.Profiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Set;

public final class WeaponSlots {

    /** Knife is hotbar slot 0. Guns start at 1. */
    public static final int KNIFE = 0;
    public static final int GUN_START = 1;
    /** Perk icons / empty white dye: hotbar 6–8. */
    public static final int PERK_START = 6;
    public static final int PERK_COUNT = 3;

    private WeaponSlots() {
    }

    public static int gunCount(KetchupZombies plugin, Player player) {
        return plugin.getProfiles().perk(player.getUniqueId(), Profiles.EXTRA_WEAPON) ? 3 : 2;
    }

    public static boolean isGunSlot(int slot, int gunCount) {
        return slot >= GUN_START && slot < GUN_START + gunCount;
    }

    public static boolean isPerkSlot(int slot) {
        return slot >= PERK_START && slot < PERK_START + PERK_COUNT;
    }

    public static boolean isLockedSlot(KetchupZombies plugin, Player player, int slot) {
        return slot == KNIFE || isGunSlot(slot, gunCount(plugin, player)) || isPerkSlot(slot);
    }

    public static NamespacedKey emptyKey(KetchupZombies plugin) {
        return new NamespacedKey(plugin, "gun-slot");
    }

    public static NamespacedKey perkEmptyKey(KetchupZombies plugin) {
        return new NamespacedKey(plugin, "perk-slot");
    }

    public static NamespacedKey perkIdKey(KetchupZombies plugin) {
        return new NamespacedKey(plugin, "perk-id");
    }

    public static boolean isEmptySlot(KetchupZombies plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(emptyKey(plugin), PersistentDataType.BYTE);
    }

    public static boolean isPerkEmpty(KetchupZombies plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(perkEmptyKey(plugin), PersistentDataType.BYTE);
    }

    public static boolean isPerkItem(KetchupZombies plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(perkIdKey(plugin), PersistentDataType.STRING);
    }

    public static boolean isSlotFiller(KetchupZombies plugin, ItemStack item) {
        return isEmptySlot(plugin, item) || isPerkEmpty(plugin, item) || isPerkItem(plugin, item);
    }

    public static ItemStack emptySlot(KetchupZombies plugin) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Weapon Slot", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Empty gun slot. Select it to buy a wall gun.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Select a gun to refill it, or to replace it when every gun slot is full.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(emptyKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack emptyPerkSlot(KetchupZombies plugin) {
        ItemStack item = new ItemStack(Material.WHITE_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Perk Slot", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Empty perk slot. Buy at the perk machine.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(perkEmptyKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack perkItem(KetchupZombies plugin, String id, String title, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Active perk.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(perkIdKey(plugin), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public static Material perkMaterial(String id) {
        return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
            case Profiles.FAST_REVIVE -> Material.COOKIE;
            case Profiles.SPEED -> Material.SUGAR;
            case Profiles.QUICK_FIRE -> Material.REDSTONE;
            case Profiles.EXTRA_HEALTH -> Material.GOLD_NUGGET;
            case Profiles.EXTRA_WEAPON -> Material.IRON_INGOT;
            case Profiles.FROZEN_BULLETS -> Material.GHAST_TEAR;
            case Profiles.FLAME_BULLETS -> Material.BLAZE_POWDER;
            default -> Material.PAPER;
        };
    }

    public static String perkTitle(String id) {
        return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
            case Profiles.FAST_REVIVE -> "Fast revive";
            case Profiles.SPEED -> "Speed";
            case Profiles.QUICK_FIRE -> "Quick fire";
            case Profiles.EXTRA_HEALTH -> "Extra health";
            case Profiles.EXTRA_WEAPON -> "Extra weapon";
            case Profiles.FROZEN_BULLETS -> "Frozen bullets";
            case Profiles.FLAME_BULLETS -> "Flame bullets";
            default -> id == null ? "Perk" : id;
        };
    }

    public static void fillEmpties(KetchupZombies plugin, Player player) {
        int guns = gunCount(plugin, player);
        PlayerInventory inventory = player.getInventory();
        for (int slot = GUN_START; slot < GUN_START + guns; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                inventory.setItem(slot, emptySlot(plugin));
            }
        }
        for (int slot = GUN_START + guns; slot < GUN_START + 3; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmptySlot(plugin, item)) {
                inventory.setItem(slot, null);
            }
        }
        fillPerkEmpties(plugin, player);
    }

    public static void fillPerkEmpties(KetchupZombies plugin, Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = PERK_START; slot < PERK_START + PERK_COUNT; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                inventory.setItem(slot, emptyPerkSlot(plugin));
            }
        }
    }

    /** Rewrite perk hotbar from profile (empty dyes + owned icons). */
    public static void syncPerks(KetchupZombies plugin, Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = PERK_START; slot < PERK_START + PERK_COUNT; slot++) {
            inventory.setItem(slot, emptyPerkSlot(plugin));
        }
        Set<String> owned = plugin.getProfiles().perks(player.getUniqueId());
        int slot = PERK_START;
        for (String id : owned) {
            if (slot >= PERK_START + PERK_COUNT) {
                break;
            }
            inventory.setItem(slot++, perkItem(plugin, id, perkTitle(id), perkMaterial(id)));
        }
    }

    public static void placePerk(KetchupZombies plugin, Player player, String id, String title, Material material) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = PERK_START; slot < PERK_START + PERK_COUNT; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isPerkEmpty(plugin, item)) {
                inventory.setItem(slot, perkItem(plugin, id, title, material));
                return;
            }
        }
    }

    public static boolean heldBuyable(KetchupZombies plugin, Player player) {
        int held = player.getInventory().getHeldItemSlot();
        if (!isGunSlot(held, gunCount(plugin, player))) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isEmptySlot(plugin, item)) {
            return true;
        }
        WeaponId id = plugin.getWeaponItems().id(item);
        return id != null && !id.meleeSlot();
    }

    /** First free gun slot (empty pane / air), or -1. */
    public static int emptyGunSlot(KetchupZombies plugin, Player player) {
        int guns = gunCount(plugin, player);
        PlayerInventory inventory = player.getInventory();
        for (int slot = GUN_START; slot < GUN_START + guns; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isEmptySlot(plugin, item)) {
                return slot;
            }
        }
        return -1;
    }

    /** Held hotbar slot is a gun slot and still the empty marker. */
    public static boolean holdingEmptyGun(KetchupZombies plugin, Player player) {
        int held = player.getInventory().getHeldItemSlot();
        if (!isGunSlot(held, gunCount(plugin, player))) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        return item == null || item.getType().isAir() || isEmptySlot(plugin, item);
    }

    /** Held hotbar slot is this gun. */
    public static boolean holdingGun(KetchupZombies plugin, Player player, WeaponId weapon) {
        int held = player.getInventory().getHeldItemSlot();
        if (!isGunSlot(held, gunCount(plugin, player))) {
            return false;
        }
        return plugin.getWeaponItems().id(player.getInventory().getItemInMainHand()) == weapon;
    }

    /**
     * Buy only into the selected gun slot.
     * Empty slot while a free one exists, or a gun when every gun slot is already filled.
     */
    public static boolean canBuyGun(KetchupZombies plugin, Player player) {
        if (holdingEmptyGun(plugin, player)) {
            return true;
        }
        if (!heldBuyable(plugin, player)) {
            return false;
        }
        return emptyGunSlot(plugin, player) < 0;
    }

    /** Puts the gun into the selected gun slot. Caller already checked {@link #canBuyGun}. */
    public static void placeGun(KetchupZombies plugin, Player player, ItemStack gun) {
        int held = player.getInventory().getHeldItemSlot();
        if (!isGunSlot(held, gunCount(plugin, player))) {
            return;
        }
        player.getInventory().setItem(held, gun);
        enforce(plugin, player);
    }

    /** Melee hotbar (slot 0): knife, or Puncher when it replaces the knife. */
    public static void placeMelee(KetchupZombies plugin, Player player, ItemStack melee) {
        player.getInventory().setItem(KNIFE, melee);
        enforce(plugin, player);
    }

    /**
     * Keeps at most {@link #gunCount} guns in gun slots. Strips guns from other slots.
     * Slot 0 holds knife or Puncher; duplicates outside are removed.
     */
    public static void enforce(KetchupZombies plugin, Player player) {
        int guns = gunCount(plugin, player);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isPerkEmpty(plugin, item) || isPerkItem(plugin, item)) {
                if (!isPerkSlot(slot)) {
                    inventory.setItem(slot, null);
                }
                continue;
            }
            WeaponId id = plugin.getWeaponItems().id(item);
            if (id == null) {
                continue;
            }
            if (id.meleeSlot()) {
                if (slot != KNIFE) {
                    inventory.setItem(slot, null);
                }
                continue;
            }
            if (!isGunSlot(slot, guns)) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        WeaponId offId = plugin.getWeaponItems().id(off);
        if (offId != null || isSlotFiller(plugin, off)) {
            inventory.setItemInOffHand(null);
        }
        int seen = 0;
        for (int slot = GUN_START; slot < GUN_START + 3; slot++) {
            ItemStack item = inventory.getItem(slot);
            WeaponId id = plugin.getWeaponItems().id(item);
            if (id == null || id.meleeSlot() || isEmptySlot(plugin, item)) {
                continue;
            }
            seen++;
            if (seen > guns || slot >= GUN_START + guns) {
                inventory.setItem(slot, null);
            }
        }
        fillEmpties(plugin, player);
    }
}
