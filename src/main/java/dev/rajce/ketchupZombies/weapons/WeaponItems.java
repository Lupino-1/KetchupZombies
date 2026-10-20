package dev.rajce.ketchupZombies.weapons;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class WeaponItems {

    public static final String LEVEL = "level";

    private final NamespacedKey weapon;
    private final NamespacedKey magazine;
    private final NamespacedKey reserve;
    private final NamespacedKey level;
    private final NamespacedKey nextShot;
    private final NamespacedKey reloadUntil;
    private final NamespacedKey knockUntil;

    public WeaponItems(KetchupZombies plugin) {
        weapon = new NamespacedKey(plugin, "weapon");
        magazine = new NamespacedKey(plugin, "magazine");
        reserve = new NamespacedKey(plugin, "reserve");
        level = new NamespacedKey(plugin, LEVEL);
        nextShot = new NamespacedKey(plugin, "next-shot");
        reloadUntil = new NamespacedKey(plugin, "reload-until");
        knockUntil = new NamespacedKey(plugin, "knock-until");
    }

    public ItemStack create(WeaponId id, PluginConfig.Weapon stats, int diggerLevel) {
        ItemStack item = new ItemStack(id.material());
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&f" + id.title()));
        meta.getPersistentDataContainer().set(weapon, PersistentDataType.STRING, id.id());
        if (diggerLevel > 0) {
            meta.getPersistentDataContainer().set(level, PersistentDataType.INTEGER, diggerLevel);
        }
        if (stats.magazine >= 0) {
            meta.getPersistentDataContainer().set(magazine, PersistentDataType.INTEGER, stats.magazine);
            meta.getPersistentDataContainer().set(reserve, PersistentDataType.INTEGER, Math.max(0, stats.ammo));
            meta.setMaxStackSize(99);
        }
        item.setItemMeta(meta);
        stack(item, stats.magazine >= 0 ? stats.magazine : 1);
        return item;
    }

    public WeaponId id(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(weapon, PersistentDataType.STRING);
        return WeaponId.byId(stored);
    }

    public int level(ItemStack item) {
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(level, PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    public int magazine(ItemStack item) {
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(magazine, PersistentDataType.INTEGER);
        return stored == null ? -1 : stored;
    }

    public int reserve(ItemStack item) {
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(reserve, PersistentDataType.INTEGER);
        return stored == null ? -1 : stored;
    }

    public long nextShot(ItemStack item) {
        Long stored = item.getItemMeta().getPersistentDataContainer().get(nextShot, PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    public long reloadUntil(ItemStack item) {
        Long stored = item.getItemMeta().getPersistentDataContainer().get(reloadUntil, PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    public long knockUntil(Player player) {
        Long stored = player.getPersistentDataContainer().get(knockUntil, PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    public void knockUntil(Player player, long at) {
        player.getPersistentDataContainer().set(knockUntil, PersistentDataType.LONG, at);
    }

    public void write(ItemStack item, Integer magazineCount, Integer reserveCount, Long next, Long reload) {
        ItemMeta meta = item.getItemMeta();
        if (magazineCount != null) {
            meta.getPersistentDataContainer().set(magazine, PersistentDataType.INTEGER, magazineCount);
            meta.setMaxStackSize(99);
        }
        if (reserveCount != null) {
            meta.getPersistentDataContainer().set(reserve, PersistentDataType.INTEGER, reserveCount);
        }
        if (next != null) {
            meta.getPersistentDataContainer().set(nextShot, PersistentDataType.LONG, next);
        }
        if (reload != null) {
            meta.getPersistentDataContainer().set(reloadUntil, PersistentDataType.LONG, reload);
        }
        item.setItemMeta(meta);
        if (magazineCount != null) {
            stack(item, magazineCount);
        }
    }

    public void stack(ItemStack item, int magazineCount) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getMaxStackSize() < 99) {
            meta.setMaxStackSize(99);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, Math.min(99, magazineCount)));
    }
}
