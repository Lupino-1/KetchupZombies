package dev.rajce.ketchupZombies.weapons;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WeaponCatalog {

    private WeaponCatalog() {
    }

    public static PluginConfig.Weapon base(KetchupZombies plugin, WeaponId id) {
        return base(plugin.config(), id);
    }

    public static PluginConfig.Weapon base(PluginConfig config, WeaponId id) {
        PluginConfig.Weapons weapons = config.weapons;
        return switch (id) {
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

    public static PluginConfig.Weapon effective(KetchupZombies plugin, WeaponId id, int level) {
        return effective(plugin.config(), id, level);
    }

    public static PluginConfig.Weapon effective(PluginConfig config, WeaponId id, int level) {
        PluginConfig.Weapon base = base(config, id);
        if (id == WeaponId.DIGGER) {
            PluginConfig.GunLevel step = config.weapons.goldDigger.level(level);
            if (step == null) {
                return base;
            }
            return copy(base, step);
        }
        if (level <= 0 || base.ultimate == null) {
            return base;
        }
        return copy(base, base.ultimate);
    }

    public static PluginConfig.Weapon effective(KetchupZombies plugin, WeaponId id, ItemStack item, WeaponItems items) {
        return effective(plugin.config(), id, items.level(item));
    }

    public static PluginConfig.Weapon effective(PluginConfig config, WeaponId id, ItemStack item, WeaponItems items) {
        return effective(config, id, items.level(item));
    }

    public static String displayName(KetchupZombies plugin, WeaponId id, int level) {
        return displayName(plugin.config(), id, level);
    }

    public static String displayName(PluginConfig config, WeaponId id, int level) {
        PluginConfig.Weapon base = base(config, id);
        String root = blank(base.name) ? id.title() : base.name;
        String ultimate = blank(base.ultimateName) ? "Ultimate " + root : base.ultimateName;
        if (level > 1) {
            return ultimate + " " + level;
        }
        if (level > 0) {
            return ultimate;
        }
        return root;
    }

    public static ItemStack stack(KetchupZombies plugin, WeaponId id, int level) {
        PluginConfig.Weapon base = base(plugin, id);
        ItemStack item = new ItemStack(id.material());
        ItemMeta meta = item.getItemMeta();
        applyModel(meta, base.modelData(level));
        item.setItemMeta(meta);
        return item;
    }

    /** Applies CMD as floats:[n] (1.21.4+ resource packs). 0 clears the component. */
    public static void applyModel(ItemMeta meta, int customModelData) {
        if (customModelData > 0) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(component);
        } else {
            meta.setCustomModelDataComponent(null);
        }
    }

    public static List<Component> lore(KetchupZombies plugin, WeaponId id, int level) {
        PluginConfig.Weapon base = base(plugin, id);
        PluginConfig.Weapon shown = effective(plugin, id, level);
        PluginConfig.WeaponLore labels = plugin.config().weaponLore;
        boolean upgraded = level > 0 && (id == WeaponId.DIGGER || base.ultimate != null);
        List<Component> lines = new ArrayList<>();
        lines.add(stat(labels.damage, format(base.damage), format(shown.damage), upgraded && base.damage != shown.damage));
        if (base.magazine >= 0) {
            lines.add(stat(labels.ammo, Integer.toString(base.ammo), Integer.toString(shown.ammo), upgraded && base.ammo != shown.ammo));
            lines.add(stat(labels.clip, Integer.toString(base.magazine), Integer.toString(shown.magazine), upgraded && base.magazine != shown.magazine));
            lines.add(stat(labels.fireRate, format(base.delay) + "s", format(shown.delay) + "s", upgraded && base.delay != shown.delay));
            if (base.reload >= 0) {
                lines.add(stat(labels.reload, format(base.reload) + "s", format(shown.reload) + "s", upgraded && base.reload != shown.reload));
            }
        } else {
            lines.add(stat(labels.fireRate, format(base.delay) + "s", format(shown.delay) + "s", upgraded && base.delay != shown.delay));
        }
        lines.add(Component.text(labels.gold + ": ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(Integer.toString(base.gold), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        // Pellets only when the weapon actually uses a multi-pellet cone (config pellets > 1).
        if (base.pellets > 1 || (upgraded && shown.pellets > 1)) {
            int basePellets = Math.max(1, base.pellets);
            int shownPellets = Math.max(1, shown.pellets > 0 ? shown.pellets : basePellets);
            lines.add(stat(labels.pellets, Integer.toString(basePellets), Integer.toString(shownPellets), upgraded && basePellets != shownPellets));
        }
        return lines;
    }

    private static Component stat(String label, String base, String upgraded, boolean showBoth) {
        Component line = Component.text(label + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        if (showBoth) {
            return line
                    .append(Component.text(base, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(upgraded, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        return line.append(Component.text(base, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
    }

    private static PluginConfig.Weapon copy(PluginConfig.Weapon base, PluginConfig.GunLevel level) {
        PluginConfig.Weapon copy = new PluginConfig.Weapon(
                base.price,
                base.gold,
                level.damage,
                level.magazine,
                level.ammo,
                level.delay,
                level.reload,
                level.pellets >= 0 ? level.pellets : base.pellets,
                level.targets >= 0 ? level.targets : base.targets
        );
        copy.name = base.name;
        copy.ultimateName = base.ultimateName;
        copy.customModelData = base.customModelData;
        copy.ultimateCustomModelData = base.ultimateCustomModelData;
        copy.shootSound = base.shootSound;
        copy.shootVolume = base.shootVolume;
        copy.shootPitch = base.shootPitch;
        copy.range = base.range;
        copy.spreadYaw = base.spreadYaw;
        copy.spreadPitch = base.spreadPitch;
        copy.ultimate = base.ultimate;
        return copy;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
