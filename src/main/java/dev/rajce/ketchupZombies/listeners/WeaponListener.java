package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WeaponListener implements Listener {

    private final KetchupZombies plugin;

    public WeaponListener(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        WeaponId id = plugin.getWeaponItems().id(item);
        if (id == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getHolograms().looking(event.getPlayer(), 3.5) != null) {
            return;
        }
        event.setCancelled(true);
        if (id.melee()) {
            if (event.getAction().isLeftClick()) {
                plugin.getWeaponCombat().use(event.getPlayer(), item, false);
            }
            return;
        }
        if (event.getAction().isLeftClick()) {
            plugin.getWeaponCombat().use(event.getPlayer(), item, true);
            return;
        }
        if (event.getAction().isRightClick()) {
            plugin.getWeaponCombat().use(event.getPlayer(), item, false);
        }
    }

    @EventHandler
    public void onEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getHolograms().tag(event.getRightClicked()) != null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        WeaponId id = plugin.getWeaponItems().id(item);
        if (id == null || id.melee()) {
            return;
        }
        event.setCancelled(true);
        plugin.getWeaponCombat().use(event.getPlayer(), item, false);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (plugin.getWeaponCombat().dealing(event.getEntity().getUniqueId())) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponId id = plugin.getWeaponItems().id(item);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (id.melee() && event.getEntity() instanceof LivingEntity living) {
            plugin.getWeaponCombat().meleeHit(player, living);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getWeaponItems().id(event.getPlayer().getInventory().getItemInMainHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> plugin.getWeaponCombat().hud(player), null);
    }
}
