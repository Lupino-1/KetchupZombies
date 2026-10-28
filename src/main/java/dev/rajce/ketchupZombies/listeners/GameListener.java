package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.game.SoftCollision;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public final class GameListener implements Listener {

    private final KetchupZombies plugin;
    private final NamespacedKey mobKey;

    public GameListener(KetchupZombies plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "match");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHologram(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getGames().inQueue(event.getPlayer())) {
            event.setCancelled(true);
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
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getGames().inQueue(player)) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.getGames().inMatch(player)) {
            return;
        }
        if (event.getClickedBlock() != null
                && (event.getAction().isRightClick() || event.getAction().isLeftClick())
                && plugin.getGames().clickDoorBlock(player, event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (!event.getAction().isRightClick()) {
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
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by
                && by.getDamager() instanceof Player attacker
                && attacker != player) {
            // No PVP in waiting lobby or match.
            if (plugin.getGames().inQueue(player) || plugin.getGames().inQueue(attacker)
                    || (plugin.getGames().inMatch(player) && plugin.getGames().inMatch(attacker))) {
                event.setCancelled(true);
                return;
            }
        }
        if (plugin.getGames().inQueue(player) || plugin.getGames().preparing(player)) {
            // Waiting lobby / copy prep: never hurt, never die. Void → back to lobby spawn.
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                plugin.getGames().rescue(player);
            }
            return;
        }
        if (!plugin.getGames().inMatch(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            if (!plugin.getGames().downed(player)) {
                plugin.getGames().rescue(player);
            }
            return;
        }
        if (plugin.getGames().downed(player) || event.getFinalDamage() >= player.getHealth()) {
            event.setCancelled(true);
            plugin.getGames().hurt(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        // Cross-world melee throws on Folia. Match mobs also drop players who already left.
        boolean cross = player.getWorld() == null
                || event.getEntity().getWorld() == null
                || !player.getWorld().equals(event.getEntity().getWorld());
        boolean ours = event.getEntity().getPersistentDataContainer().has(mobKey, PersistentDataType.STRING);
        if (cross || plugin.getGames().downed(player) || (ours && !plugin.getGames().inMatch(player))) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event.getEntity().getPersistentDataContainer().has(mobKey, PersistentDataType.STRING)) {
            SoftCollision.leaveZombie(plugin, event.getEntity());
        }
        plugin.getGames().killed(event.getEntity());
    }

    /** Real death (/kill, plugin damage) while busy: never drop hub or game items. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getGames().busy(event.getEntity())) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setShouldDropExperience(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGames().busy(player)) {
            return;
        }
        Location spot = plugin.getGames().respawnSpot(player);
        if (spot != null) {
            event.setRespawnLocation(spot);
        }
        player.getScheduler().runDelayed(plugin, task -> {
            SoftCollision.player(player, false);
            plugin.getGames().afterRespawn(player);
        }, null, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getGames().busy(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getGames().busy(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.getGames().busy(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void hideJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void hideQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getGames().partyReconnect(player);
            plugin.getGames().returnOnJoin(player);
            SoftCollision.player(player, false);
            if (!plugin.getGames().busy(player)) {
                org.bukkit.Location hub = plugin.hubSpawn();
                if (hub != null) {
                    player.teleportAsync(hub);
                }
            }
            if (plugin.getHubBoard() != null) {
                plugin.getHubBoard().show(player);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        // leave() covers match quit and queue lobby exit
        plugin.getGames().leave(player);
        plugin.getGames().partyDisconnect(player);
        SoftCollision.player(player, true);
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().hide(player);
        }
        plugin.clearBoardPreview(player);
    }
}
