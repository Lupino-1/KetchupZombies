package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GameListener implements Listener {

    private final KetchupZombies plugin;

    public GameListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHologram(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String tag = plugin.getHolograms().tag(event.getRightClicked());
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getGames().click(event.getPlayer(), event.getRightClicked().getLocation(), tag);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getGames().inMatch(player)) {
            return;
        }
        Entity hologram = plugin.getHolograms().looking(player, 3.5);
        if (hologram == null) {
            return;
        }
        String tag = plugin.getHolograms().tag(hologram);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getGames().click(player, hologram.getLocation(), tag);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.getGames().inMatch(player)) {
            return;
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by
                && by.getDamager() instanceof Player attacker
                && attacker != player
                && plugin.getGames().inMatch(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getGames().downed(player) || event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            plugin.getGames().hurt(player);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getGames().killed(event.getEntity());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getGames().inMatch(event.getEntity())) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getGames().inMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGames().leave(event.getPlayer());
    }
}
