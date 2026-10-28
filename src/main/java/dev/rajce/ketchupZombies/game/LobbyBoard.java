package dev.rajce.ketchupZombies.game;

import dev.lupino1.messages.ColorParser;
import dev.lupino1.placeholder.Placeholders;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Start-lobby sidebar on an arena copy (queue / countdown). Same header/footer as match.
 */
public final class LobbyBoard {

    private final KetchupZombies plugin;
    private final Map<UUID, Sidebar> boards = new ConcurrentHashMap<>();

    public LobbyBoard(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, int seconds, int count, int needed) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().hide(player);
        }
        if (plugin.getBoardPreview() != null) {
            plugin.getBoardPreview().clear(player);
        }
        paint(player, seconds, count, needed);
    }

    public void hide(Player player) {
        if (player == null) {
            return;
        }
        drop(player.getUniqueId());
    }

    public void clearAll() {
        for (UUID id : List.copyOf(boards.keySet())) {
            drop(id);
        }
    }

    private void paint(Player player, int seconds, int count, int needed) {
        PluginConfig.Scoreboard matchCfg = plugin.config().scoreboard;
        PluginConfig.LobbyScoreboard lobbyCfg = plugin.config().lobbyScoreboard;
        Sidebar sidebar = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Sidebar created = plugin.getScoreboards().createSidebar();
            created.addPlayer(player);
            return created;
        });
        Map<String, String> values = Map.of(
                "time", seconds < 0 ? "—" : Integer.toString(seconds),
                "count", Integer.toString(count),
                "needed", Integer.toString(Math.max(1, needed))
        );
        List<String> lines = lobbyCfg.lines == null || lobbyCfg.lines.isEmpty()
                ? List.of("", "", "&#F18F0E&lStart", "&7• <font:rajce:rajcefont>#FEF4E7Start:</font> &#F18F0E%time%s", "", "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>")
                : lobbyCfg.lines;
        synchronized (sidebar) {
            sidebar.title(line(matchCfg.title == null ? "&f" : matchCfg.title, player, values));
            sidebar.clearLines();
            int slot = 0;
            for (String raw : lines) {
                if (slot >= sidebar.maxLines()) {
                    break;
                }
                sidebar.line(slot++, line(raw == null ? "" : raw, player, values));
            }
        }
    }

    private static Component line(String raw, Player player, Map<String, String> values) {
        String text = Placeholders.apply(raw, player, values);
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
