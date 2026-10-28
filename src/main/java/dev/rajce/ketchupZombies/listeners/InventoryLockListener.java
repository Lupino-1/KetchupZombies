package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import dev.rajce.ketchupZombies.weapons.WeaponSlots;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class InventoryLockListener implements Listener {

    private final KetchupZombies plugin;

    public InventoryLockListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.getGames().inMatch(player)) {
            return;
        }
        int guns = WeaponSlots.gunCount(plugin, player);
        Integer hotbar = event.getHotbarButton() >= 0 ? event.getHotbarButton() : null;
        if (hotbar != null && WeaponSlots.isLockedSlot(plugin, player, hotbar)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            int slot = event.getSlot();
            if (WeaponSlots.isLockedSlot(plugin, player, slot)) {
                event.setCancelled(true);
                return;
            }
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            if (locked(current) || locked(cursor)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (locked(current)) {
                event.setCancelled(true);
            }
        }
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            ItemStack cursor = event.getCursor();
            if (locked(cursor)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.getGames().inMatch(player)) {
            return;
        }
        ItemStack old = event.getOldCursor();
        if (locked(old)) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()) {
                int inv = slot - event.getView().getTopInventory().getSize();
                if (WeaponSlots.isLockedSlot(plugin, player, inv)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!plugin.getGames().inMatch(event.getPlayer())) {
            return;
        }
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();
        if (locked(main) || locked(off)) {
            event.setCancelled(true);
        }
    }

    private boolean locked(ItemStack item) {
        return isGunItem(item) || WeaponSlots.isSlotFiller(plugin, item);
    }

    private boolean isGunItem(ItemStack item) {
        WeaponId id = plugin.getWeaponItems().id(item);
        return id != null;
    }
}
