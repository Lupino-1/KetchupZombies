package dev.rajce.ketchupZombies.config;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundKit {

    private SoundKit() {
    }

    public static void play(Location at, PluginConfig.SoundFx fx) {
        if (at == null || at.getWorld() == null || fx == null) {
            return;
        }
        Sound sound = resolve(fx.sound);
        if (sound == null) {
            return;
        }
        at.getWorld().playSound(at, sound, fx.volume, fx.pitch);
    }

    public static void play(Player player, Location at, PluginConfig.SoundFx fx) {
        if (player == null || !player.isOnline() || fx == null) {
            return;
        }
        Sound sound = resolve(fx.sound);
        if (sound == null) {
            return;
        }
        Location where = at != null ? at : player.getLocation();
        player.playSound(where, sound, fx.volume, fx.pitch);
    }

    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim();
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        key = key.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
