package dev.rajce.ketchupZombies.weapons;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class WeaponItems {

    public static final String LEVEL = "level";

    private final KetchupZombies plugin;
    private final NamespacedKey weapon;
    private final NamespacedKey magazine;
    private final NamespacedKey reserve;
    private final NamespacedKey level;
    private final NamespacedKey nextShot;
    private final NamespacedKey reloadUntil;
    private final NamespacedKey knockUntil;

    public WeaponItems(KetchupZombies plugin) {
        this.plugin = plugin;
        weapon = new NamespacedKey(plugin, "weapon");
        magazine = new NamespacedKey(plugin, "magazine");
        reserve = new NamespacedKey(plugin, "reserve");
        level = new NamespacedKey(plugin, LEVEL);
        nextShot = new NamespacedKey(plugin, "next-shot");
        reloadUntil = new NamespacedKey(plugin, "reload-until");
        knockUntil = new NamespacedKey(plugin, "knock-until");
    }

    public ItemStack create(WeaponId id, PluginConfig.Weapon ignored, int upgradeLevel) {
        PluginConfig.Weapon base = WeaponCatalog.base(plugin, id);
        PluginConfig.Weapon stats = WeaponCatalog.effective(plugin, id, upgradeLevel);
        ItemStack item = new ItemStack(id.material());
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        WeaponCatalog.applyModel(meta, base.modelData(upgradeLevel));
        meta.getPersistentDataContainer().set(weapon, PersistentDataType.STRING, id.id());
        if (upgradeLevel > 0) {
            meta.getPersistentDataContainer().set(level, PersistentDataType.INTEGER, upgradeLevel);
        }
        if (stats.magazine >= 0) {
            int clip = Math.max(0, stats.magazine);
            // config ammo = total rounds (clip + reserve). Full gun starts at that total.
            int spare = Math.max(0, stats.ammo - clip);
            meta.getPersistentDataContainer().set(magazine, PersistentDataType.INTEGER, clip);
            meta.getPersistentDataContainer().set(reserve, PersistentDataType.INTEGER, spare);
            meta.setMaxStackSize(99);
        }
        paint(meta, id, upgradeLevel);
        item.setItemMeta(meta);
        stack(item, stats.magazine >= 0 ? stats.magazine : 1);
        return item;
    }

    public void paint(ItemStack item) {
        WeaponId id = id(item);
        if (id == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        int upgradeLevel = level(item);
        WeaponCatalog.applyModel(meta, WeaponCatalog.base(plugin, id).modelData(upgradeLevel));
        paint(meta, id, upgradeLevel);
        item.setItemMeta(meta);
    }

    private void paint(ItemMeta meta, WeaponId id, int level) {
        NamedTextColor color = level > 0 ? NamedTextColor.GREEN : NamedTextColor.WHITE;
        meta.displayName(Component.text(WeaponCatalog.displayName(plugin, id, level), color).decoration(TextDecoration.ITALIC, false));
        meta.lore(WeaponCatalog.lore(plugin, id, level));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        // Ultimate / digger upgrade: same CustomModelData, enchant glow only.
        meta.removeEnchantments();
        if (level > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
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
        if (meta != null && (!meta.hasMaxStackSize() || meta.getMaxStackSize() < 99)) {
            meta.setMaxStackSize(99);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, Math.min(99, Math.max(1, magazineCount))));
    }

    /** Progress 0 = empty/broken bar, 1 = full. Used while reloading. */
    public void reloadBar(ItemStack item, float progress) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        short max = item.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        float clamped = Math.max(0f, Math.min(1f, progress));
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        damageable.setDamage(Math.round((1f - clamped) * max));
        item.setItemMeta(meta);
    }

    public void clearReloadBar(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
    }
}
