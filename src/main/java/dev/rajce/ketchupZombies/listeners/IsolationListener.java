package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class IsolationListener implements Listener {

    private final KetchupZombies plugin;

    public IsolationListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            if (!plugin.getGames().inMatch(player) && !plugin.getGames().inQueue(player)) {
                plugin.getGames().isolation().hub(player);
            } else {
                plugin.getGames().isolation().refresh(player);
            }
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGames().isolation().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                return false;
            }
            return !plugin.getGames().isolation().same(sender, viewer);
        });
    }
}
