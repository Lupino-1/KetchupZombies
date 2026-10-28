package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JoinTabListener implements Listener {

    private final KetchupZombies plugin;

    public JoinTabListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTab(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null) {
            return;
        }
        String trimmed = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        String[] parts = trimmed.split(" ", -1);
        if (parts.length < 2) {
            return;
        }
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!root.equals("kz") && !root.equals("ketchupzombies")) {
            return;
        }
        if (!parts[1].equalsIgnoreCase("join")) {
            return;
        }
        String token = parts[parts.length - 1];
        List<String> suggestions = new ArrayList<>();
        if (parts.length == 3) {
            for (String arena : plugin.getEditor().completeArenas()) {
                if (prefix(arena, token)) {
                    suggestions.add(arena);
                }
            }
        } else if (parts.length == 4) {
            if (prefix("normal", token)) {
                suggestions.add("normal");
            }
        } else if (parts.length == 5) {
            for (String count : List.of("1", "2", "3", "4")) {
                if (prefix(count, token)) {
                    suggestions.add(count);
                }
            }
        } else {
            return;
        }
        event.setCompletions(suggestions);
    }

    private static boolean prefix(String value, String token) {
        return token == null || token.isEmpty() || value.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT));
    }
}
