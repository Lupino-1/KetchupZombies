package dev.rajce.ketchupZombies.weapons;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.shop.Profiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
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
        PluginConfig.Weapon stats = stats(id, item);
        if (stats.magazine < 0) {
            player.setLevel(0);
            player.setExp(0f);
            return;
        }
        int magazine = Math.max(0, items.magazine(item));
        int reserve = Math.max(0, items.reserve(item));
        items.stack(item, magazine);
        player.setLevel(magazine + reserve);
        player.setExp(stats.magazine <= 0 ? 0f : Math.min(0.999f, magazine / (float) stats.magazine));
    }

    public void meleeHit(Player player, LivingEntity target) {
        if (plugin.getGames().downed(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponId id = items.id(item);
        if (id == null || !id.melee()) {
            return;
        }
        if (!ready(player, item, stats(id, item))) {
            return;
        }
        PluginConfig.Weapon stats = stats(id, item);
        markDelay(player, item, stats);
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
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        for (LivingEntity mob : targets) {
            apply(player, id, stats, mob, false, knock, player.getEyeLocation().getDirection());
        }
    }

    private void melee(Player player, ItemStack item, WeaponId id) {
        PluginConfig.Weapon stats = stats(id, item);
        if (!ready(player, item, stats)) {
            return;
        }
        List<LivingEntity> targets = cone(player, id.knocks() ? Math.max(1, stats.targets) : 1);
        if (targets.isEmpty()) {
            return;
        }
        markDelay(player, item, stats);
        long now = System.currentTimeMillis();
        boolean knock = id.knocks() && now >= items.knockUntil(player);
        if (knock) {
            items.knockUntil(player, now + 5000L);
        }
        Vector look = player.getEyeLocation().getDirection();
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getEyeLocation().add(look.clone().multiply(1.2)), 1, 0, 0, 0, 0);
        for (LivingEntity mob : targets) {
            apply(player, id, stats, mob, false, knock, look);
        }
    }

    private void shoot(Player player, ItemStack item, WeaponId id) {
        PluginConfig.Weapon stats = stats(id, item);
        if (!ready(player, item, stats)) {
            return;
        }
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
            player.getInventory().setItemInMainHand(item);
            hud(player);
        }
        markDelay(player, item, stats);
        int pellets = Math.max(1, stats.pellets);
        Vector look = player.getEyeLocation().getDirection().normalize();
        for (int pellet = 0; pellet < pellets; pellet++) {
            Vector direction = look.clone();
            if (pellets > 1) {
                direction.rotateAroundY(Math.toRadians((Math.random() - 0.5) * 14));
                direction.rotateAroundX(Math.toRadians((Math.random() - 0.5) * 8));
                direction.normalize();
            }
            Location start = player.getEyeLocation().clone();
            trace(player, id, stats, start, direction, plugin.config().shot.range, false, new HashSet<>());
        }
    }

    private void startReload(Player player, ItemStack item, WeaponId id) {
        PluginConfig.Weapon stats = stats(id, item);
        if (stats.magazine < 0 || stats.reload < 0) {
            return;
        }
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
        long until = System.currentTimeMillis() + (long) (stats.reload * 1000);
        items.write(item, null, null, null, until);
        player.getInventory().setItemInMainHand(item);
        plugin.getMessageManager().send(player, "weapon.reload");
        long ticks = Math.max(1L, Math.round(stats.reload * 20));
        player.getScheduler().runDelayed(plugin, task -> finishReload(player, id), null, ticks);
    }

    private void finishReload(Player player, WeaponId id) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (items.id(item) != id) {
            return;
        }
        PluginConfig.Weapon stats = stats(id, item);
        int magazine = items.magazine(item);
        int reserve = items.reserve(item);
        int need = Math.max(0, stats.magazine - magazine);
        int taken = Math.min(need, Math.max(0, reserve));
        items.write(item, magazine + taken, reserve - taken, null, 0L);
        player.getInventory().setItemInMainHand(item);
        hud(player);
        plugin.getMessageManager().send(player, "weapon.reloaded");
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
            boolean head = at.getY() >= mob.getY() + mob.getHeight() * 0.72;
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
                plugin.getProfiles().addGold(player.getUniqueId(), awarded);
                plugin.getGames().gold(player);
                plugin.getMessageManager().send(player, critical ? "weapon.crit" : "weapon.hit", Map.of("gold", Integer.toString(awarded)));
            });
        }, null);
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
        at.getWorld().spawnParticle(particle, at, id == WeaponId.FLAMETHROWER ? 2 : 1, 0.02, 0.02, 0.02, 0);
    }

    private LivingEntity mobAt(Player player, Location at, Set<UUID> hit) {
        double radius = Math.max(0.05, plugin.config().shot.hitRadius);
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

    private boolean ready(Player player, ItemStack item, PluginConfig.Weapon stats) {
        return System.currentTimeMillis() >= items.nextShot(item);
    }

    private void markDelay(Player player, ItemStack item, PluginConfig.Weapon stats) {
        double delay = stats.delay;
        if (plugin.getProfiles().perk(player.getUniqueId(), Profiles.QUICK_FIRE)) {
            delay *= 0.75;
        }
        items.write(item, null, null, System.currentTimeMillis() + (long) (delay * 1000), null);
        player.getInventory().setItemInMainHand(item);
    }

    private PluginConfig.Weapon stats(WeaponId id, ItemStack item) {
        PluginConfig.Weapons weapons = plugin.config().weapons;
        PluginConfig.Weapon base = switch (id) {
            case KNIFE -> weapons.knife;
            case PISTOL -> weapons.pistol;
            case RIFLE -> weapons.rifle;
            case SHOTGUN -> weapons.shotgun;
            case SNIPER -> weapons.sniper;
            case ROCKET -> weapons.rocketLauncher;
            case FLAMETHROWER -> weapons.flamethrower;
            case DART -> weapons.blowDart;
            case SOAKER -> weapons.zombieSoaker;
            case ZAPPER -> weapons.zombieZapper;
            case PUNCHER -> weapons.puncher;
            case ELDER -> weapons.elderGun;
            case DIGGER -> weapons.goldDigger;
        };
        if (id != WeaponId.DIGGER) {
            return base;
        }
        PluginConfig.GunLevel level = weapons.goldDigger.level(items.level(item));
        if (level == null) {
            return base;
        }
        return new PluginConfig.Weapon(base.price, base.gold, level.damage, level.magazine, level.ammo, level.delay, level.reload, base.pellets, base.targets);
    }

    private static boolean opensWallshot(BlockData data) {
        return Tag.STAIRS.isTagged(data.getMaterial())
                || Tag.SLABS.isTagged(data.getMaterial())
                || Tag.WALLS.isTagged(data.getMaterial())
                || Tag.TRAPDOORS.isTagged(data.getMaterial())
                || data.getMaterial() == org.bukkit.Material.IRON_BARS;
    }

}
