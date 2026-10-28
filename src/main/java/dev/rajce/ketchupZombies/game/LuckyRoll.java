package dev.rajce.ketchupZombies.game;

import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.SoundKit;
import dev.rajce.ketchupZombies.weapons.WeaponCatalog;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import dev.rajce.ketchupZombies.weapons.WeaponSlots;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One lucky-chest spin for the whole match. 5s of 0.5s item flashes, then 10s to take the gun.
 */
public final class LuckyRoll {

    private static final int SPIN_STEPS = 10;
    private static final int CLAIM_STEPS = 20;

    private final Match match;
    private final KetchupZombies plugin;
    private boolean busy;
    private boolean claiming;
    private UUID owner;
    private WeaponId prize;
    private Location chest;
    private ItemDisplay display;
    private int step;

    public LuckyRoll(Match match) {
        this.match = match;
        this.plugin = match.plugin();
    }

    public void click(Player player, Location at) {
        if (busy && claiming && owner.equals(player.getUniqueId())) {
            claim(player);
            return;
        }
        if (busy) {
            plugin.getMessageManager().send(player, "game.lucky-wait");
            return;
        }
        int price = plugin.config().luckyChest.price;
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        match.gold(player);
        SoundKit.play(player, player.getLocation(), plugin.config().sounds.shopBuy);
        List<WeaponId> pool = pool();
        if (pool.isEmpty()) {
            plugin.getProfiles().gold(player.getUniqueId(), plugin.getProfiles().gold(player.getUniqueId()) + price);
            return;
        }
        prize = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        owner = player.getUniqueId();
        chest = at.clone();
        busy = true;
        claiming = false;
        step = 0;
        plugin.getMessageManager().send(player, "game.lucky-spin");
        org.bukkit.Bukkit.getRegionScheduler().runAtFixedRate(plugin, chest, this::tick, 1L, 10L);
    }

    public void stop() {
        Location at = chest;
        busy = false;
        claiming = false;
        owner = null;
        prize = null;
        chest = null;
        clearDisplay();
        if (at != null && at.getWorld() != null) {
            org.bukkit.Bukkit.getRegionScheduler().run(plugin, at, task -> plugin.getHolograms().restoreLucky(at));
        }
    }

    private void tick(ScheduledTask scheduled) {
        if (!busy) {
            scheduled.cancel();
            return;
        }
        step++;
        if (step <= SPIN_STEPS) {
            List<WeaponId> pool = pool();
            WeaponId shown = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            show(shown);
            caption(WeaponCatalog.displayName(plugin, shown, 0));
            float pitch = 0.5f + (step % 6) * 0.15f;
            chest.getWorld().playSound(chest, Sound.BLOCK_NOTE_BLOCK_HARP, 1f, pitch);
            return;
        }
        int left = SPIN_STEPS + 1 + CLAIM_STEPS - step;
        if (left > 0) {
            if (step == SPIN_STEPS + 1) {
                claiming = true;
                show(prize);
                Player player = org.bukkit.Bukkit.getPlayer(owner);
                if (player != null) {
                    tell(player, "game.lucky-claim", java.util.Map.of(
                            "item", WeaponCatalog.displayName(plugin, prize, 0)
                    ));
                }
            }
            caption(String.format(Locale.US, "&e%.1fs", left * 0.5));
            return;
        }
        if (step >= SPIN_STEPS + 1 + CLAIM_STEPS) {
            Player player = org.bukkit.Bukkit.getPlayer(owner);
            if (player != null) {
                tell(player, "game.lucky-missed", java.util.Map.of());
            }
            stop();
            scheduled.cancel();
        }
    }

    private void claim(Player player) {
        if (prize.meleeSlot()) {
            if (player.getInventory().getHeldItemSlot() != WeaponSlots.KNIFE) {
                plugin.getMessageManager().send(player, "shop.gun-full");
                return;
            }
        } else if (!WeaponSlots.canBuyGun(plugin, player)) {
            plugin.getMessageManager().send(player, "shop.gun-full");
            return;
        }
        plugin.getShopMenu().giveWeapon(player, prize);
        plugin.getMessageManager().send(player, "game.lucky-taken", java.util.Map.of(
                "item", WeaponCatalog.displayName(plugin, prize, 0)
        ));
        stop();
    }

    private void caption(String raw) {
        if (chest == null) {
            return;
        }
        plugin.getHolograms().luckyCaption(chest, plugin.getMessageManager().parse(raw, null, java.util.Map.of("item", "")));
    }

    private void show(WeaponId weapon) {
        ItemStack stack = WeaponCatalog.stack(plugin, weapon, 0);
        if (display == null || !display.isValid()) {
            display = plugin.getHolograms().rollItem(chest, stack);
            return;
        }
        display.setItemStack(stack);
    }

    private void tell(Player player, String key, java.util.Map<String, String> values) {
        player.getScheduler().run(plugin, task -> plugin.getMessageManager().send(player, key, values), null);
    }

    private void clearDisplay() {
        ItemDisplay shown = display;
        display = null;
        if (shown == null || !shown.isValid()) {
            return;
        }
        shown.getScheduler().run(plugin, task -> {
            if (shown.isValid()) {
                shown.remove();
            }
        }, null);
    }

    private List<WeaponId> pool() {
        List<WeaponId> weapons = new ArrayList<>();
        for (WeaponId weapon : WeaponId.values()) {
            if (WeaponCatalog.base(plugin, weapon).price < 0) {
                weapons.add(weapon);
            }
        }
        return weapons;
    }
}
