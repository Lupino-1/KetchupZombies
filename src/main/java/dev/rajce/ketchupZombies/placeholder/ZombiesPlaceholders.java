package dev.rajce.ketchupZombies.placeholder;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.db.SqlDatabase;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ZombiesPlaceholders extends PlaceholderExpansion {

    private final KetchupZombies plugin;

    public ZombiesPlaceholders(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ketchupzombies";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Lupino1";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] parts = params.toLowerCase().split("_");
        if (parts.length != 2) {
            return null;
        }
        SqlDatabase.Mode mode = switch (parts[0]) {
            case "normal" -> SqlDatabase.Mode.NORMAL;
            case "endless" -> SqlDatabase.Mode.ENDLESS;
            default -> null;
        };
        if (mode == null) {
            return null;
        }
        int place;
        try {
            place = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (place < 1) {
            return "";
        }
        List<SqlDatabase.Entry> top = plugin.getDatabase().top(mode);
        if (place > top.size()) {
            return "";
        }
        SqlDatabase.Entry entry = top.get(place - 1);
        if (mode == SqlDatabase.Mode.NORMAL) {
            long seconds = entry.timeMs() / 1000;
            return entry.name() + " " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }
        return entry.name() + " " + entry.round();
    }
}
