package dev.rajce.ketchupZombies.game;

import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the configurable Hypixel-style sidebar from {@link PluginConfig.Scoreboard}.
 */
public final class BoardLayout {

    public enum MateStatus {
        ALIVE,
        REVIVE,
        DEAD,
        QUIT
    }

    public record Mate(String name, int gold, MateStatus status) {
    }

    public record View(int round, int zombies, int kills, String time, String area, List<Mate> mates) {
    }

    private BoardLayout() {
    }

    public static void paint(Sidebar sidebar, PluginConfig.Scoreboard cfg, View view) {
        List<Component> built = build(cfg, view);
        synchronized (sidebar) {
            sidebar.title(parse(cfg.title, globals(view), null));
            // null clears a slot — do not pad with empty Component (that keeps the board max height)
            sidebar.clearLines();
            int line = 0;
            for (Component component : built) {
                if (line >= sidebar.maxLines()) {
                    break;
                }
                sidebar.line(line++, component);
            }
        }
    }

    public static List<Component> build(PluginConfig.Scoreboard cfg, View view) {
        Map<String, String> globals = globals(view);
        List<Component> out = new ArrayList<>();
        List<String> lines = cfg.lines == null || cfg.lines.isEmpty() ? defaultLines() : cfg.lines;
        for (String raw : lines) {
            if (raw == null) {
                out.add(Component.empty());
                continue;
            }
            int playersAt = raw.indexOf("%players%");
            if (playersAt >= 0) {
                // Wrap each mate line: prefix + player-line + suffix (e.g. <font…>%players%</font>)
                String prefix = raw.substring(0, playersAt);
                String suffix = raw.substring(playersAt + "%players%".length());
                for (Mate mate : view.mates()) {
                    out.add(parse(prefix + playerLine(cfg, mate) + suffix, globals, mate));
                }
                continue;
            }
            if (raw.isEmpty()) {
                out.add(Component.empty());
                continue;
            }
            out.add(parse(raw, globals, null));
        }
        return out;
    }

    private static String playerLine(PluginConfig.Scoreboard cfg, Mate mate) {
        String template = cfg.playerLine == null || cfg.playerLine.isBlank() ? "&#FEF4E7%player%: %status%" : cfg.playerLine;
        return apply(template, Map.of(
                "player", mate.name(),
                "gold", Integer.toString(mate.gold()),
                "status", statusText(cfg, mate)
        ));
    }

    /** Same status string as the sidebar (for downed corpse hologram). */
    public static String statusLabel(PluginConfig.Scoreboard cfg, MateStatus status, String playerName, int gold) {
        return statusText(cfg, new Mate(playerName == null ? "" : playerName, gold, status));
    }

    private static String statusText(PluginConfig.Scoreboard cfg, Mate mate) {
        String template = switch (mate.status()) {
            case ALIVE -> blank(cfg.statusAlive, "&#F18F0E%gold%");
            case REVIVE -> blank(cfg.statusRevive, "&#FFFB00Oživit");
            case DEAD -> blank(cfg.statusDead, "&#E61A1AMrtvý");
            case QUIT -> blank(cfg.statusQuit, "&#E61A1AOdešel");
        };
        return apply(template, Map.of(
                "player", mate.name(),
                "gold", Integer.toString(mate.gold())
        ));
    }

    private static Map<String, String> globals(View view) {
        return Map.of(
                "round", Integer.toString(view.round()),
                "zombies", Integer.toString(view.zombies()),
                "kills", Integer.toString(view.kills()),
                "time", view.time() == null ? "0:00" : view.time(),
                "area", view.area() == null ? "-" : view.area()
        );
    }

    private static Component parse(String raw, Map<String, String> globals, Mate mate) {
        String text = apply(raw, globals);
        if (mate != null) {
            text = apply(text, Map.of(
                    "player", mate.name(),
                    "gold", Integer.toString(mate.gold())
            ));
        }
        if (text.isBlank()) {
            return Component.empty();
        }
        return ColorParser.translateColors(text);
    }

    private static String apply(String raw, Map<String, String> values) {
        String out = raw;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return out;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> defaultLines() {
        return List.of(
                "",
                "",
                "&#F18F0E&lKolo %round%",
                "&7• <font:rajce:rajcefont>#FEF4E7Zbývající zombíci:</font> &#F18F0E%zombies%",
                "",
                "&7 - %players%",
                "",
                "&7• <font:rajce:rajcefont>#FEF4E7Killy:</font> &#F18F0E%kills%",
                "&7• <font:rajce:rajcefont>#FEF4E7Čas:</font> &#F18F0E%time%",
                "",
                "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>"
        );
    }
}
