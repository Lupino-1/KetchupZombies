package dev.rajce.ketchupZombies.weapons;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.shop.Profiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WeaponCombat {

    private final KetchupZombies plugin;
    private final WeaponItems items;
    private final Set<UUID> dealing = ConcurrentHashMap.newKeySet();
    /** Own bash cooldown — vanilla attackCooldown never fills under Mining Fatigue III. */
    private final Map<UUID, Long> bashUntil = new ConcurrentHashMap<>();
    /** Knife gold throttle only — vanilla sword damage is never rate-limited. */
    private final Map<UUID, Long> knifeGoldUntil = new ConcurrentHashMap<>();
    /** Fire delay on player — not item PDC (meta rewrite was stuttering / eating shots). */
    private final Map<UUID, Long> nextShotAt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastDelaySec = new ConcurrentHashMap<>();

    public WeaponCombat(KetchupZombies plugin, WeaponItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public boolean dealing(UUID mob) {
        return dealing.contains(mob);
    }

    public void use(Player player, ItemStack item, boolean reload) {
        if (plugin.getGames().downed(player)) {
            return;
        }
        WeaponId id = items.id(item);
        if (id == null) {
            return;
        }
        if (id.melee()) {
            if (!reload) {
                melee(player, item, id);
            }
            return;
        }
        if (reload) {
            startReload(player, item, id);
            return;
        }
        shoot(player, item, id);
    }

    public void hud(Player player) {
        hud(player, true);
    }

    /** @param syncAmmo when false, only paint XP/level (safe every tick during fire-charge). */
    public void hud(Player player, boolean syncAmmo) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponId id = items.id(item);
        if (id == null || id.melee()) {
            player.setLevel(0);
            player.setExp(0f);
            return;
        }
        PluginConfig.Weapon stats = stats(player, id, item);
        if (stats.magazine < 0) {
            player.setLevel(0);
            player.setExp(0f);
            return;
        }
        long until = items.reloadUntil(item);
        long now = System.currentTimeMillis();
        if (syncAmmo) {
            syncAmmoFromPlayer(player, item, id);
            player.getInventory().setItemInMainHand(item);
        }
        int magazine = Math.max(0, items.magazine(item));
        int reserve = Math.max(0, items.reserve(item));
        int[] profile = plugin.getProfiles().ammo(player.getUniqueId(), id);
        if (profile != null) {
            magazine = Math.max(0, profile[0]);
            reserve = Math.max(0, profile[1]);
        }
        int total = magazine + reserve;
        if (until > now && stats.reload > 0) {
            float progress = 1f - Math.min(1f, (until - now) / (float) (stats.reload * 1000));
            player.setLevel(total);
            player.setExp(Math.min(0.999f, Math.max(0f, progress)));
            return;
        }
        if (syncAmmo) {
            items.stack(item, magazine);
            player.getInventory().setItemInMainHand(item);
        }
        player.setLevel(total);
        long next = nextShotAt.getOrDefault(player.getUniqueId(), 0L);
        if (next > now) {
            float delayMs = (float) Math.max(50L, Math.round(lastDelaySec.getOrDefault(player.getUniqueId(), stats.delay) * 1000));
            float progress = 1f - Math.min(1f, (next - now) / delayMs);
            player.setExp(Math.min(0.999f, Math.max(0f, progress)));
            return;
        }
        player.setExp(0.999f);
    }

    /** Seed / overwrite profile ammo from a gun item (buy, refill, loadout). */
    public void bindAmmo(Player player, ItemStack item) {
        WeaponId id = items.id(item);
        if (id == null || id.melee()) {
            return;
        }
        plugin.getProfiles().ammo(player.getUniqueId(), id, Math.max(0, items.magazine(item)), Math.max(0, items.reserve(item)));
    }

    public void held(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (plugin.getGames().inMatch(player)) {
            WeaponSlots.enforce(plugin, player);
            WeaponId held = items.id(player.getInventory().getItemInMainHand());
            // Guns: Mining Fatigue (no digs / gun feel). Knife = vanilla iron sword — needs normal attack cooldown.
            if (held != null && !held.melee()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 2, false, false, false));
            } else {
                player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }
        } else {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        }
    }

    public void gunBash(Player player, LivingEntity target) {
        if (plugin.getGames().downed(player) || target == null || !hostile(player, target)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < bashUntil.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        bashUntil.put(player.getUniqueId(), now + 400L);
        dealing.add(target.getUniqueId());
        try {
            target.damage(1.0, player);
        } finally {
            dealing.remove(target.getUniqueId());
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.8f, 1f);
        player.resetCooldown();
    }

    /** LMB: bash if a mob is in melee reach, otherwise reload. */
    public void gunLeftClick(Player player, ItemStack item) {
        if (plugin.getGames().downed(player)) {
            return;
        }
        LivingEntity target = meleeTarget(player, 3.0);
        if (target != null) {
            gunBash(player, target);
            return;
        }
        use(player, item, true);
    }

    private LivingEntity meleeTarget(Player player, double reach) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestDist = reach;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(reach, reach, reach)) {
            if (!(entity instanceof LivingEntity living) || !hostile(player, living)) {
                continue;
            }
            Vector to = living.getBoundingBox().getCenter().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 0.01 || dist > bestDist) {
                continue;
            }
            if (look.dot(to.clone().normalize()) < 0.75) {
                continue;
            }
            bestDist = dist;
            best = living;
        }
        return best;
    }

    /**
     * Knife damage is vanilla (no swing throttle). Gold only — at most once per
     * {@code weapons.knife-gold-seconds}, and only when the hit actually dealt damage
     * (spam swings that deal 0 are ignored).
     */
    public void knifeGold(Player player, LivingEntity target, boolean critical, double finalDamage) {
        if (!plugin.getGames().inMatch(player) || plugin.getGames().downed(player)) {
            return;
        }
        if (target == null || !hostile(player, target) || finalDamage <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = Math.max(0L, Math.round(plugin.config(player).weapons.knifeGoldSeconds * 1000));
        if (now < knifeGoldUntil.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        PluginConfig.Weapon stats = stats(player, WeaponId.KNIFE, player.getInventory().getItemInMainHand());
        int gold = Math.max(0, stats.gold);
        if (critical) {
            gold = (int) Math.ceil(gold * 1.5);
        }
        if (gold <= 0) {
            return;
        }
        knifeGoldUntil.put(player.getUniqueId(), now + wait);
        int awarded = gold;
        FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            playHit(player);
            plugin.getProfiles().addGold(player.getUniqueId(), awarded);
            plugin.getGames().gold(player);
            plugin.getMessageManager().send(player, critical ? "weapon.crit" : "weapon.hit", Map.of("gold", Integer.toString(awarded)));
        });
    }

    public void meleeHit(Player player, LivingEntity target) {
        if (plugin.getGames().downed(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponId id = items.id(item);
        // Knife uses vanilla EntityDamage — never custom apply here.
        if (id == null || !id.melee() || id == WeaponId.KNIFE) {
            return;
        }
        PluginConfig.Weapon stats = stats(player, id, item);
        if (!ready(player)) {
            return;
        }
        markDelay(player, stats);
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(target);
        if (id.knocks() || stats.targets > 1) {
            targets = cone(player, Math.max(1, stats.targets));
            if (targets.isEmpty()) {
                targets = List.of(target);
            }
        }
        long now = System.currentTimeMillis();
        boolean knock = id.knocks() && now >= items.knockUntil(player);
        if (knock) {
            items.knockUntil(player, now + 5000L);
        }
        playShoot(player, stats);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        for (LivingEntity mob : targets) {
            apply(player, id, stats, mob, false, knock, player.getEyeLocation().getDirection());
        }
    }

    private void melee(Player player, ItemStack item, WeaponId id) {
        if (id == WeaponId.KNIFE) {
            return;
        }
        PluginConfig.Weapon stats = stats(player, id, item);
        if (!ready(player)) {
            return;
        }
        List<LivingEntity> targets = cone(player, id.knocks() ? Math.max(1, stats.targets) : 1);
        if (targets.isEmpty()) {
            return;
        }
        markDelay(player, stats);
        long now = System.currentTimeMillis();
        boolean knock = id.knocks() && now >= items.knockUntil(player);
        if (knock) {
            items.knockUntil(player, now + 5000L);
        }
        Vector look = player.getEyeLocation().getDirection();
        playShoot(player, stats);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getEyeLocation().add(look.clone().multiply(1.2)), 1, 0, 0, 0, 0);
        for (LivingEntity mob : targets) {
            apply(player, id, stats, mob, false, knock, look);
        }
    }

    private void shoot(Player player, ItemStack item, WeaponId id) {
        PluginConfig.Weapon stats = stats(player, id, item);
        if (!ready(player)) {
            return;
        }
        syncAmmoFromPlayer(player, item, id);
        long now = System.currentTimeMillis();
        if (now < items.reloadUntil(item)) {
            return;
        }
        if (stats.magazine >= 0 && items.magazine(item) <= 0) {
            startReload(player, item, id);
            return;
        }
        if (stats.magazine >= 0) {
            items.write(item, items.magazine(item) - 1, null, null, null);
            pushAmmoToPlayer(player, item, id);
            player.getInventory().setItemInMainHand(item);
        }
        markDelay(player, stats);
        playShoot(player, stats);
        int pellets = Math.max(1, stats.pellets);
        double range = shotRange(player, stats);
        double spreadYaw = stats.spreadYaw > 0 ? stats.spreadYaw : 14;
        double spreadPitch = stats.spreadPitch > 0 ? stats.spreadPitch : 8;
        Vector look = player.getEyeLocation().getDirection().normalize();
        for (int pellet = 0; pellet < pellets; pellet++) {
            Vector direction = look.clone();
            if (pellets > 1) {
                direction.rotateAroundY(Math.toRadians((Math.random() - 0.5) * spreadYaw));
                direction.rotateAroundX(Math.toRadians((Math.random() - 0.5) * spreadPitch));
                direction.normalize();
            }
            Location start = player.getEyeLocation().clone();
            // Fresh hit set per pellet so one mob can take every pellet (reset i-frames in apply).
            trace(player, id, stats, start, direction, range, false, new HashSet<>());
        }
        hud(player, false);
        pulseFireBar(player, id);
        // Empty clip → reload now (Hypixel-style); do not wait for another fire click.
        if (stats.magazine >= 0 && items.magazine(item) <= 0 && items.reserve(item) > 0) {
            startReload(player, item, id);
        }
    }

    /** Tick XP fire-charge bar until next shot is ready (no item meta writes). */
    private void pulseFireBar(Player player, WeaponId id) {
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            if (items.id(player.getInventory().getItemInMainHand()) != id) {
                task.cancel();
                return;
            }
            hud(player, false);
            if (System.currentTimeMillis() >= nextShotAt.getOrDefault(player.getUniqueId(), 0L)) {
                task.cancel();
            }
        }, null, 1L, 1L);
    }

    private double shotRange(Player player, PluginConfig.Weapon stats) {
        if (stats.range > 0) {
            return stats.range;
        }
        return Math.max(1, plugin.config(player).shot.range);
    }

    /** Profile ammo is source of truth; keep the held item in sync. */
    private void syncAmmoFromPlayer(Player player, ItemStack item, WeaponId id) {
        if (id == null || id.melee()) {
            return;
        }
        int[] stored = plugin.getProfiles().ammo(player.getUniqueId(), id);
        if (stored == null) {
            int mag = items.magazine(item);
            int res = items.reserve(item);
            // -1 = missing PDC. Do not Math.max to 0 — that seeded empty ammo and blocked shooting.
            if (mag < 0 && res < 0) {
                return;
            }
            plugin.getProfiles().ammo(player.getUniqueId(), id, Math.max(0, mag), Math.max(0, res));
            return;
        }
        items.write(item, stored[0], stored[1], null, null);
    }

    private void pushAmmoToPlayer(Player player, ItemStack item, WeaponId id) {
        if (id == null || id.melee()) {
            return;
        }
        plugin.getProfiles().ammo(player.getUniqueId(), id, Math.max(0, items.magazine(item)), Math.max(0, items.reserve(item)));
    }

    private void startReload(Player player, ItemStack item, WeaponId id) {
        PluginConfig.Weapon stats = stats(player, id, item);
        if (stats.magazine < 0 || stats.reload < 0) {
            return;
        }
        syncAmmoFromPlayer(player, item, id);
        int magazine = items.magazine(item);
        int reserve = items.reserve(item);
        if (magazine >= stats.magazine || reserve <= 0) {
            if (magazine <= 0 && reserve <= 0) {
                plugin.getMessageManager().send(player, "weapon.empty");
            }
            return;
        }
        if (System.currentTimeMillis() < items.reloadUntil(item)) {
            return;
        }
        int slot = weaponSlot(player, item, id);
        if (slot < 0) {
            return;
        }
        long durationMs = Math.max(50L, Math.round(stats.reload * 1000));
        long until = System.currentTimeMillis() + durationMs;
        items.write(item, null, null, null, until);
        items.reloadBar(item, 0f);
        player.getInventory().setItem(slot, item);
        hud(player);
        plugin.getMessageManager().send(player, "weapon.reload");
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            int found = weaponSlot(player, null, id);
            if (found < 0) {
                task.cancel();
                return;
            }
            ItemStack gun = player.getInventory().getItem(found);
            if (gun == null || items.id(gun) != id) {
                task.cancel();
                return;
            }
            long left = items.reloadUntil(gun) - System.currentTimeMillis();
            if (left <= 0) {
                task.cancel();
                finishReload(player, id, found);
                return;
            }
            float progress = 1f - Math.min(1f, left / (float) durationMs);
            items.reloadBar(gun, progress);
            player.getInventory().setItem(found, gun);
            hud(player);
        }, null, 1L, 1L);
    }

    private void finishReload(Player player, WeaponId id, int slot) {
        if (!player.isOnline()) {
            return;
        }
        int found = slot >= 0 ? slot : weaponSlot(player, null, id);
        if (found < 0) {
            return;
        }
        ItemStack item = player.getInventory().getItem(found);
        if (items.id(item) != id) {
            return;
        }
        PluginConfig.Weapon stats = stats(player, id, item);
        syncAmmoFromPlayer(player, item, id);
        int magazine = items.magazine(item);
        int reserve = items.reserve(item);
        int need = Math.max(0, stats.magazine - magazine);
        int taken = Math.min(need, Math.max(0, reserve));
        items.write(item, magazine + taken, reserve - taken, null, 0L);
        pushAmmoToPlayer(player, item, id);
        items.clearReloadBar(item);
        player.getInventory().setItem(found, item);
        hud(player);
        plugin.getMessageManager().send(player, "weapon.reloaded");
    }

    private int weaponSlot(Player player, ItemStack preferred, WeaponId id) {
        ItemStack[] contents = player.getInventory().getContents();
        if (preferred != null) {
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] == preferred) {
                    return slot;
                }
            }
            int held = player.getInventory().getHeldItemSlot();
            if (items.id(player.getInventory().getItem(held)) == id) {
                return held;
            }
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (items.id(stack) == id) {
                return slot;
            }
        }
        return -1;
    }

    private void trace(Player player, WeaponId id, PluginConfig.Weapon stats, Location at, Vector direction, double left, boolean wallshot, Set<UUID> hit) {
        if (!player.isOnline() || left <= 0) {
            if (id.explodes() && left <= 0) {
                explode(player, id, stats, at);
            }
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(at)) {
            Location next = at.clone();
            double remain = left;
            boolean opened = wallshot;
            FoliaManager.runAtLocation(next, () -> trace(player, id, stats, next, direction, remain, opened, hit));
            return;
        }
        double step = 0.25;
        int steps = 0;
        while (left > 0 && Bukkit.isOwnedByCurrentRegion(at)) {
            at.add(direction.clone().multiply(step));
            left -= step;
            if (++steps % 2 == 0) {
                spark(id, at);
            }
            Block block = at.getBlock();
            if (opensWallshot(block.getBlockData())) {
                wallshot = true;
            } else if (!block.getType().isAir()) {
                if (!wallshot) {
                    if (id.explodes()) {
                        explode(player, id, stats, at);
                    }
                    return;
                }
            }
            LivingEntity mob = mobAt(player, at, hit);
            if (mob == null) {
                continue;
            }
            if (id.explodes()) {
                explode(player, id, stats, at);
                return;
            }
            double headFrom = Math.min(0.95, Math.max(0.5, plugin.config(player).shot.headshotHeight));
            boolean head = at.getY() >= mob.getY() + mob.getHeight() * headFrom;
            hit.add(mob.getUniqueId());
            apply(player, id, stats, mob, head, false, direction);
            if (id.chains()) {
                chain(player, id, stats, mob, hit);
                return;
            }
            boolean pierce = id.piercesMobs() || (wallshot && id.piercesAfterWallshot());
            if (!pierce) {
                return;
            }
            int cap = stats.targets > 0 ? stats.targets : Integer.MAX_VALUE;
            if (!id.piercesMobs() && !id.piercesAfterWallshot() && hit.size() >= cap) {
                return;
            }
        }
        if (left > 0) {
            Location next = at.clone();
            double remaining = left;
            boolean opened = wallshot;
            FoliaManager.runAtLocation(next, () -> trace(player, id, stats, next, direction, remaining, opened, hit));
        } else if (id.explodes()) {
            explode(player, id, stats, at);
        }
    }

    private void chain(Player player, WeaponId id, PluginConfig.Weapon stats, LivingEntity first, Set<UUID> hit) {
        int cap = Math.max(1, stats.targets);
        List<LivingEntity> extra = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : first.getNearbyEntities(6, 4, 6)) {
            if (entity instanceof LivingEntity living && hostile(player, living) && hit.add(living.getUniqueId())) {
                extra.add(living);
            }
        }
        extra.sort(Comparator.comparingDouble(mob -> mob.getLocation().distanceSquared(first.getLocation())));
        int left = cap - 1;
        for (LivingEntity mob : extra) {
            if (left-- <= 0) {
                break;
            }
            apply(player, id, stats, mob, false, false, first.getLocation().getDirection());
        }
    }

    private void explode(Player player, WeaponId id, PluginConfig.Weapon stats, Location at) {
        if (!Bukkit.isOwnedByCurrentRegion(at)) {
            Location next = at.clone();
            FoliaManager.runAtLocation(next, () -> explode(player, id, stats, next));
            return;
        }
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        for (org.bukkit.entity.Entity entity : at.getWorld().getNearbyEntities(at, 3, 3, 3)) {
            if (entity instanceof LivingEntity living && hostile(player, living)) {
                apply(player, id, stats, living, false, false, new Vector());
            }
        }
    }

    private void apply(Player player, WeaponId id, PluginConfig.Weapon stats, LivingEntity mob, boolean head, boolean knock, Vector look) {
        mob.getScheduler().run(plugin, task -> {
            if (!mob.isValid() || mob.isDead()) {
                return;
            }
            double damage = stats.damage;
            if (id.soaks() && (mob.getFireTicks() > 0 || mob.getScoreboardTags().contains("kz-fire"))) {
                damage = 15;
            }
            boolean crit = head && id.crits();
            if (crit) {
                damage = Math.ceil(damage * 1.5);
            }
            // Pellet weapons hit the same tick — vanilla i-frames would eat later pellets.
            mob.setNoDamageTicks(0);
            dealing.add(mob.getUniqueId());
            try {
                mob.damage(damage, player);
            } finally {
                dealing.remove(mob.getUniqueId());
            }
            if (id.burns() || plugin.getProfiles().perk(player.getUniqueId(), Profiles.FLAME_BULLETS)) {
                mob.setFireTicks(Math.max(mob.getFireTicks(), 60));
            }
            if (id.poison()) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, false, false));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, false));
            }
            if (id.stuns()) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10, false, false));
                mob.setVelocity(new Vector());
            }
            if (plugin.getProfiles().perk(player.getUniqueId(), Profiles.FROZEN_BULLETS)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, false, false));
            }
            if (knock) {
                Vector push = look.clone().setY(0).normalize().multiply(1.05).setY(0.25);
                mob.setVelocity(push);
            }
            int gold = stats.gold;
            if (crit) {
                gold = (int) Math.ceil(gold * 1.5);
            }
            int awarded = gold;
            boolean critical = crit;
            FoliaManager.runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                playHit(player);
                plugin.getProfiles().addGold(player.getUniqueId(), awarded);
                plugin.getGames().gold(player);
                plugin.getMessageManager().send(player, critical ? "weapon.crit" : "weapon.hit", Map.of("gold", Integer.toString(awarded)));
            });
        }, null);
    }

    private void playShoot(Player player, PluginConfig.Weapon stats) {
        Sound sound = sound(stats.shootSound, Sound.UI_BUTTON_CLICK);
        player.getWorld().playSound(player.getLocation(), sound, Math.max(0.05f, stats.shootVolume), Math.max(0.05f, stats.shootPitch));
    }

    private void playHit(Player player) {
        PluginConfig.Shot shot = plugin.config(player).shot;
        Sound sound = sound(shot.hitSound, Sound.ENTITY_ARROW_HIT_PLAYER);
        player.playSound(player.getLocation(), sound, Math.max(0.05f, shot.hitVolume), Math.max(0.05f, shot.hitPitch));
    }

    private static Sound sound(String name, Sound fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void spark(WeaponId id, Location at) {
        Particle particle = switch (id) {
            case FLAMETHROWER, ROCKET -> Particle.FLAME;
            case ZAPPER -> Particle.ELECTRIC_SPARK;
            case DART -> Particle.WITCH;
            case SOAKER -> Particle.SPLASH;
            case ELDER -> Particle.SOUL_FIRE_FLAME;
            case SHOTGUN -> Particle.CRIT;
            default -> Particle.CRIT;
        };
        int count = id == WeaponId.FLAMETHROWER ? 6 : 4;
        // force=true so clients on Decreased particles still see the trail
        at.getWorld().spawnParticle(particle, at, count, 0.06, 0.06, 0.06, 0.01, null, true);
    }

    private LivingEntity mobAt(Player player, Location at, Set<UUID> hit) {
        double radius = Math.max(0.05, plugin.config(player).shot.hitRadius);
        for (org.bukkit.entity.Entity entity : at.getWorld().getNearbyEntities(at, radius, radius * 1.25, radius)) {
            if (!(entity instanceof LivingEntity living) || !hostile(player, living) || hit.contains(living.getUniqueId())) {
                continue;
            }
            if (living.getBoundingBox().expand(radius * 0.35, radius * 0.5, radius * 0.35).contains(at.getX(), at.getY(), at.getZ())) {
                return living;
            }
        }
        return null;
    }

    private List<LivingEntity> cone(Player player, int limit) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        List<LivingEntity> found = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(3.2, 2.5, 3.2)) {
            if (!(entity instanceof LivingEntity living) || !hostile(player, living)) {
                continue;
            }
            Vector to = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.05 || to.lengthSquared() > 10.5 || look.angle(to) > Math.toRadians(40)) {
                continue;
            }
            found.add(living);
        }
        found.sort(Comparator.comparingDouble(mob -> mob.getLocation().distanceSquared(eye)));
        if (found.size() > limit) {
            return new ArrayList<>(found.subList(0, limit));
        }
        return found;
    }

    private boolean hostile(Player player, LivingEntity entity) {
        return entity != player && !(entity instanceof Player) && entity.isValid() && !entity.isDead();
    }

    private boolean ready(Player player) {
        return System.currentTimeMillis() >= nextShotAt.getOrDefault(player.getUniqueId(), 0L);
    }

    private void markDelay(Player player, PluginConfig.Weapon stats) {
        double delay = stats.delay;
        if (plugin.getProfiles().perk(player.getUniqueId(), Profiles.QUICK_FIRE)) {
            delay *= 0.75;
        }
        lastDelaySec.put(player.getUniqueId(), delay);
        nextShotAt.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(50L, Math.round(delay * 1000)));
    }

    private PluginConfig.Weapon stats(Player player, WeaponId id, ItemStack item) {
        return WeaponCatalog.effective(plugin.config(player), id, item, items);
    }

    private static boolean opensWallshot(BlockData data) {
        return Tag.STAIRS.isTagged(data.getMaterial())
                || Tag.SLABS.isTagged(data.getMaterial())
                || Tag.WALLS.isTagged(data.getMaterial())
                || Tag.TRAPDOORS.isTagged(data.getMaterial())
                || data.getMaterial() == org.bukkit.Material.IRON_BARS;
    }

}
