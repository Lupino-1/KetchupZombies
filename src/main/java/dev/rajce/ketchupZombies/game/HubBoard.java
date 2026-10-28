package dev.rajce.ketchupZombies.game;

import dev.lupino1.messages.ColorParser;
import dev.lupino1.placeholder.Placeholders;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet sidebar for players outside a match. Title = {@code scoreboard.title} (same header as match).
 */
public final class HubBoard {

    private final KetchupZombies plugin;
    private final Map<UUID, Sidebar> boards = new ConcurrentHashMap<>();

    public HubBoard(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    public void show(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin.getGames() != null && (plugin.getGames().inMatch(player) || plugin.getGames().inQueue(player))) {
            return;
        }
        if (plugin.getBoardPreview() != null && plugin.getBoardPreview().showing(player)) {
            return;
        }
        paint(player);
    }

    public void hide(Player player) {
        if (player == null) {
            return;
        }
        drop(player.getUniqueId());
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                if (plugin.getGames() != null && (plugin.getGames().inMatch(player) || plugin.getGames().inQueue(player))) {
                    return;
                }
                show(player);
            }, null);
        }
    }

    public void clearAll() {
        for (UUID id : List.copyOf(boards.keySet())) {
            drop(id);
        }
    }

    private void paint(Player player) {
        PluginConfig.Scoreboard matchCfg = plugin.config().scoreboard;
        PluginConfig.HubScoreboard hubCfg = plugin.config().hubScoreboard;
        Sidebar sidebar = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Sidebar created = plugin.getScoreboards().createSidebar();
            created.addPlayer(player);
            return created;
        });
        try {
            sidebar.addPlayer(player);
        } catch (RuntimeException ignored) {
        }
        List<String> lines = hubCfg.lines == null || hubCfg.lines.isEmpty()
                ? List.of("", "", "&#F18F0E&lLobby", "", "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>")
                : hubCfg.lines;
        synchronized (sidebar) {
            sidebar.title(line(matchCfg.title == null ? "&f" : matchCfg.title, player));
            sidebar.clearLines();
            int slot = 0;
            for (String raw : lines) {
                if (slot >= sidebar.maxLines()) {
                    break;
                }
                sidebar.line(slot++, line(raw == null ? "" : raw, player));
            }
        }
    }

    private static Component line(String raw, Player player) {
        String text = Placeholders.apply(raw, player, Map.of());
        if (text == null || text.isBlank()) {
            return Component.empty();
        }
        return ColorParser.translateColors(text);
    }

    private void drop(UUID id) {
        Sidebar sidebar = boards.remove(id);
        if (sidebar == null) {
            return;
        }
        synchronized (sidebar) {
            sidebar.close();
        }
    }
}
