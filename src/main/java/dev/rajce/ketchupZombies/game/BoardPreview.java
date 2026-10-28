package dev.rajce.ketchupZombies.game;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake Hypixel-layout match board for `/kz scoreboard` tests. Hides hub while on. */
public final class BoardPreview {

    private final KetchupZombies plugin;
    private final Map<UUID, Sidebar> boards = new ConcurrentHashMap<>();

    public BoardPreview(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    public boolean showing(Player player) {
        return player != null && boards.containsKey(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (boards.containsKey(player.getUniqueId())) {
            clear(player);
            if (plugin.getHubBoard() != null) {
                plugin.getHubBoard().show(player);
            }
            return false;
        }
        show(player);
        return true;
    }

    public void show(Player player) {
        clear(player);
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().hide(player);
        }
        Sidebar sidebar = plugin.getScoreboards().createSidebar();
        sidebar.addPlayer(player);
        boards.put(player.getUniqueId(), sidebar);
        PluginConfig.Scoreboard cfg = plugin.config().scoreboard;
        BoardLayout.paint(sidebar, cfg, sample(player.getName()));
    }

    public void clear(Player player) {
        Sidebar sidebar = boards.remove(player.getUniqueId());
        if (sidebar == null) {
            return;
        }
        synchronized (sidebar) {
            sidebar.close();
        }
    }

    public void clearAll() {
        for (UUID id : List.copyOf(boards.keySet())) {
            Sidebar sidebar = boards.remove(id);
            if (sidebar != null) {
                synchronized (sidebar) {
                    sidebar.close();
                }
            }
        }
    }

    private static BoardLayout.View sample(String self) {
        return new BoardLayout.View(
                12,
                26,
                47,
                "12:34",
                "Hotel",
                List.of(
                        new BoardLayout.Mate(self, 1840, BoardLayout.MateStatus.ALIVE),
                        new BoardLayout.Mate("Player2", 0, BoardLayout.MateStatus.REVIVE),
                        new BoardLayout.Mate("Player3", 0, BoardLayout.MateStatus.DEAD),
                        new BoardLayout.Mate("Player4", 0, BoardLayout.MateStatus.QUIT)
                )
        );
    }
}
