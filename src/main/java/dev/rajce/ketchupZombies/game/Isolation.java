package dev.rajce.ketchupZombies.game;

import dev.rajce.ketchupZombies.KetchupZombies;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake-server bubbles: hub / queue lobby / match see only players in the same bubble.
 * Uses {@link Player#hidePlayer}/{@link Player#showPlayer} (tab + entities).
 */
public final class Isolation {

    public static final String HUB = "hub";

    private final KetchupZombies plugin;
    private final Map<UUID, String> bubble = new ConcurrentHashMap<>();

    public Isolation(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    public static String queue(UUID slotId) {
        return "queue:" + slotId;
    }

    public static String match(String matchId) {
        return "match:" + matchId;
    }

    public void hub(Player player) {
        set(player, HUB);
    }

    public void set(Player player, String id) {
        if (player == null) {
            return;
        }
        bubble.put(player.getUniqueId(), id == null ? HUB : id);
        refresh(player);
    }

    public void remove(Player player) {
        if (player == null) {
            return;
        }
        bubble.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
            apply(other, player);
            apply(player, other);
        }
    }

    public String of(UUID id) {
        return bubble.getOrDefault(id, HUB);
    }

    public boolean same(Player a, Player b) {
        if (a == null || b == null) {
            return false;
        }
        return of(a.getUniqueId()).equals(of(b.getUniqueId()));
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            apply(player, other);
            apply(other, player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void apply(Player viewer, Player target) {
        if (same(viewer, target)) {
            viewer.showPlayer(plugin, target);
        } else {
            viewer.hidePlayer(plugin, target);
        }
    }
}
