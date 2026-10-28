package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Stops match / queue players from breaking the map (blocks, chests, pressure plates).
 */
public final class ArenaProtectListener implements Listener {

    private final KetchupZombies plugin;

    public ArenaProtectListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Player player) {
        return plugin.getGames().busy(player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!locked(player)) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (container(type) || plate(type)) {
            event.setCancelled(true);
        }
    }

    private static boolean plate(Material type) {
        return Tag.PRESSURE_PLATES.isTagged(type)
                || type == Material.STONE_PRESSURE_PLATE
                || type.name().endsWith("_PRESSURE_PLATE");
    }

    private static boolean container(Material type) {
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.ENDER_CHEST
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type.name().endsWith("_SHULKER_BOX")
                || Tag.SHULKER_BOXES.isTagged(type);
    }
}
