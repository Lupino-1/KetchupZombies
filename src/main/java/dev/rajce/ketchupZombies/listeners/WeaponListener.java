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

    // ignoreCancelled=false: RIGHT/LEFT_CLICK_AIR is often pre-cancelled by Bukkit — that was
    // why guns only fired when clicking a block (ground), never into air.
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        // event.getItem() is often null on clicks — always read main hand.
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponId id = plugin.getWeaponItems().id(item);
        if (id == null) {
            return;
        }
        // Knife = vanilla iron sword. No custom interact damage.
        if (id == WeaponId.KNIFE) {
            return;
        }
        if (plugin.getGames().inQueue(player)) {
            event.setCancelled(true);
            return;
        }
        // Only defer RMB shoot when really aiming at a nearby shop/door hologram.
        if (event.getAction().isRightClick()
                && plugin.getHolograms().looking(player, 3.5) != null) {
            return;
        }
        event.setCancelled(true);
        if (id.melee()) {
            if (event.getAction().isLeftClick()) {
                plugin.getWeaponCombat().use(player, item, false);
            }
            return;
        }
        if (event.getAction().isLeftClick()) {
            plugin.getWeaponCombat().gunLeftClick(player, item);
            return;
        }
        if (event.getAction().isRightClick()) {
            plugin.getWeaponCombat().use(player, item, false);
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
        // Knife: leave vanilla iron-sword damage; gold is throttled separately.
        if (id == WeaponId.KNIFE) {
            if (event.getEntity() instanceof LivingEntity living) {
                plugin.getWeaponCombat().knifeGold(player, living, event.isCritical(), event.getFinalDamage());
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (id.melee()) {
            plugin.getWeaponCombat().meleeHit(player, living);
            return;
        }
        plugin.getWeaponCombat().gunBash(player, living);
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
        player.getScheduler().run(plugin, task -> {
            plugin.getWeaponCombat().hud(player);
            plugin.getWeaponCombat().held(player);
        }, null);
    }
}
