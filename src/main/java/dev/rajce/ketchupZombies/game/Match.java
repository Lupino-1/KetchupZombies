package dev.rajce.ketchupZombies.game;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.config.SoundKit;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.UltimateMachine;
import dev.rajce.ketchupZombies.models.WindowBlock;
import dev.rajce.ketchupZombies.shop.Profiles;
import dev.rajce.ketchupZombies.util.ItemCopies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class Match {

    private final KetchupZombies plugin;
    /** Snapshot at match create. Reload swaps live config; this match keeps these values. */
    private final PluginConfig config;
    private final RoundBook book;
    private final NamespacedKey mobKey;
    private final NamespacedKey windowKey;
    /** Feet Y at spawn — below this, mob can no longer break windows. */
    private final NamespacedKey spawnYKey;
    private final String id;
    private final String arena;
    private final int index;
    private final ArenaData data;
    private final World world;
    private final String startSection;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    /** Join order for the sidebar — quit players stay listed as QUIT. */
    private final List<UUID> roster = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> rosterNames = new ConcurrentHashMap<>();
    private final Set<UUID> quit = ConcurrentHashMap.newKeySet();
    private final Set<UUID> downed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dead = ConcurrentHashMap.newKeySet();
    private final Set<UUID> cleaned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();
    private final Map<UUID, Sidebar> boards = new ConcurrentHashMap<>();
    private final Map<UUID, Location> positions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sneaking = new ConcurrentHashMap<>();
    private final Map<UUID, Long> downedAt = new ConcurrentHashMap<>();
    /** Downed player → when current sneak-revive hold started. */
    private final Map<UUID, Long> reviveHoldSince = new ConcurrentHashMap<>();
    /** Downed player → who is holding the revive. */
    private final Map<UUID, UUID> reviveBy = new ConcurrentHashMap<>();
    /** Downed player → DEAD-countdown ms left when revive started (frozen while held). */
    private final Map<UUID, Long> revivePausedLeft = new ConcurrentHashMap<>();
    /** Window index → broken block indices in break order (last = next repair). */
    private final Map<Integer, List<Integer>> windowBroken = new ConcurrentHashMap<>();
    private final Map<Integer, Long> windowNextBreak = new ConcurrentHashMap<>();
    private final Map<Integer, Long> windowNextRepair = new ConcurrentHashMap<>();
    /** Player → when they started holding sneak on a repairable window. */
    private final Map<UUID, Long> repairHoldSince = new ConcurrentHashMap<>();
    /** Player → window index they are charging repair on. */
    private final Map<UUID, Integer> repairHoldWindow = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> windowSpawns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    /** Mob UUID → last spot and time it was actually moving. */
    private final Map<UUID, Long> stuckSince = new ConcurrentHashMap<>();
    private final Map<UUID, Location> stuckAt = new ConcurrentHashMap<>();
    /** Idle long enough that steer keeps a fresh player target until the mob moves. */
    private final Set<UUID> unstick = ConcurrentHashMap.newKeySet();
    private final List<LivingEntity> mobs = new CopyOnWriteArrayList<>();
    private final List<RoundBook.Wave> waves = new ArrayList<>();
    private final List<RoundBook.Mob> queue = new ArrayList<>();
    private final Set<String> open = new HashSet<>();
    private final Set<String> openedDoors = ConcurrentHashMap.newKeySet();
    private final DownedBodies bodies;
    private final LuckyRoll lucky;
    private int waveIndex;
    private int cursor;
    private int alive;
    private int pending;
    private int round = 1;
    private int spawnCooldown;
    private int boardCooldown;
    private boolean power;
    private boolean ended;
    private boolean running;
    private long windowNote;
    private long nextWaveAt;
    private long startedAt;
    /** Wall-clock start of the current round (for cumulative Hypixel wave delays). */
    private long roundClock;

    public Match(KetchupZombies plugin, RoundBook book, NamespacedKey mobKey, String arena, int index, ArenaData data, World world) {
        this.plugin = plugin;
        this.config = plugin.config();
        this.book = book;
        this.mobKey = mobKey;
        this.windowKey = new NamespacedKey(plugin, "window");
        this.spawnYKey = new NamespacedKey(plugin, "spawn-y");
        this.id = UUID.randomUUID().toString();
        this.arena = arena;
        this.index = index;
        this.data = data;
        this.world = world;
        this.bodies = new DownedBodies(plugin);
        this.lucky = new LuckyRoll(this);
        startSection = data.sections().keySet().iterator().next();
        open.add(startSection);
    }

    public PluginConfig config() {
        return config;
    }

    KetchupZombies plugin() {
        return plugin;
    }

    public boolean contains(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean downed(Player player) {
        UUID id = player.getUniqueId();
        return downed.contains(id) || dead.contains(id);
    }

    public String id() {
        return id;
    }

    public String arena() {
        return arena;
    }

    public int index() {
        return index;
    }

    public void begin(List<Player> group) {
        begin(group, Map.of());
    }

    /**
     * @param returns optional hub snapshots from queue lobby (inventory + return location)
     */
    public void begin(List<Player> group, Map<UUID, Saved> returns) {
        List<Player> online = new ArrayList<>();
        for (Player player : group) {
            if (player != null && player.isOnline() && players.add(player.getUniqueId())) {
                roster.add(player.getUniqueId());
                rosterNames.put(player.getUniqueId(), player.getName());
                online.add(player);
            }
        }
        if (online.isEmpty()) {
            plugin.getCopies().reset(arena, index);
            return;
        }
        Location rough = playerSpawn();
        for (Player player : online) {
            player.getScheduler().run(plugin, task -> {
                if (!players.contains(player.getUniqueId()) || ended) {
                    return;
                }
                Saved preset = returns.get(player.getUniqueId());
                if (preset != null) {
                    saved.put(player.getUniqueId(), preset);
                } else {
                    saved.put(player.getUniqueId(), new Saved(
                            ItemCopies.of(player.getInventory().getContents()),
                            ItemCopies.of(player.getInventory().getArmorContents()),
                            player.getLocation().clone(),
                            player.getGameMode()
                    ));
                }
                place(player, rough);
            }, null);
        }
    }

    private Location playerSpawn() {
        Location configured = plugin.getGames().arenaSpawn(data, index);
        if (configured != null) {
            return configured;
        }
        ArenaSection section = data.sections().get(startSection);
        int offsetX = config.copies.offsetX * index;
        int offsetZ = config.copies.offsetZ * index;
        return new Location(world, (section.x1() + section.x2()) / 2.0 + offsetX + 0.5, section.y1() + 1, (section.z1() + section.z2()) / 2.0 + offsetZ + 0.5);
    }

    public void gold(Player player) {
        paint(player);
    }

    public boolean clickDoorBlock(Player player, Location block) {
        if (ended || !players.contains(player.getUniqueId()) || downed(player)) {
            return false;
        }
        ArenaDoor door = doorAtBlock(block);
        if (door != null) {
            buyDoor(player, door);
            return true;
        }
        if (machineAtBlock(block)) {
            upgradeGun(player);
            return true;
        }
        if (luckyAtBlock(block)) {
            lucky.click(player, block.clone().add(0.5, 0, 0.5));
            return true;
        }
        return false;
    }

    public void click(Player player, Location at) {
        if (ended || !players.contains(player.getUniqueId()) || downed(player)) {
            return;
        }
        if (nearestMachine(at) != null) {
            upgradeGun(player);
            return;
        }
        ArenaDoor door = nearestDoor(at);
        if (door != null) {
            buyDoor(player, door);
            return;
        }
        SpotKind kind = nearestSpot(at);
        if (kind == SpotKind.WEAPONS) {
            FinePoint point = nearestPoint(data.spots(SpotKind.WEAPONS), at);
            List<dev.rajce.ketchupZombies.weapons.WeaponId> stock = point != null && data.customWeapons(point)
                    ? data.weaponOffers(point)
                    : plugin.getShopMenu().defaultWeapons();
            if (stock.size() == 1) {
                plugin.getShopMenu().wallWeapon(player, stock.get(0));
            } else {
                plugin.getShopMenu().weapons(player, stock);
            }
            return;
        }
        if (kind == SpotKind.ARMOR) {
            FinePoint point = nearestPoint(data.spots(SpotKind.ARMOR), at);
            List<ArmorOffer> offers = point != null && data.customArmor(point) ? data.armorOffers(point) : plugin.getShopMenu().defaults();
            if (!offers.isEmpty() && !offers.get(0).empty()) {
                plugin.getShopMenu().wallArmor(player, offers.get(0));
            }
            return;
        }
        if (kind == SpotKind.LUCKY_CHESTS) {
            FinePoint point = nearestPoint(data.spots(SpotKind.LUCKY_CHESTS), at);
            if (point != null) {
                lucky.click(player, hologram(point));
            }
            return;
        }
        if (kind == SpotKind.UPGRADERS) {
            upgradeGun(player);
            return;
        }
        if (kind == SpotKind.PERKS) {
            if (!power) {
                plugin.getMessageManager().send(player, "shop.power-off");
                return;
            }
            plugin.getProfiles().power(player.getUniqueId(), true);
            plugin.getShopMenu().perks(player);
            return;
        }
        if (kind == SpotKind.POWER) {
            buyPower(player);
        }
    }

    public void hurt(Player player) {
        if (ended || !players.contains(player.getUniqueId())) {
            return;
        }
        UUID id = player.getUniqueId();
        if (dead.contains(id)) {
            checkWipe();
            return;
        }
        // Last person still up (others downed, mid-revive, or dead) — no knock, match ends.
        if (standing() <= 1) {
            finish(false);
            return;
        }
        if (!downed.add(id)) {
            // Already downed — still re-check wipe (previous hurt may have failed before finish).
            checkWipe();
            return;
        }
        Location death = player.getLocation().clone();
        positions.put(id, death);
        downedAt.put(id, System.currentTimeMillis());
        clearReviveHold(id);
        player.setHealth(1);
        bodies.spawn(player, death, () -> !ended && (downed.contains(id) || dead.contains(id)));
        plugin.getMessageManager().send(player, "game.downed");
        String section = areaName(player);
        broadcastKnockTitle(id, player.getName(), section);
        double leftSec = config.shops.deadSeconds;
        String time = String.format(java.util.Locale.US, "%.1f", leftSec);
        bodies.timerRaw(id, plugin.getMessageManager().getRaw("game.revive-timer", "&c%time%s").replace("%time%", time));
        // Stay at death — do not teleport to arena spawn (that shoved spectators sideways).
        retargetMobsAwayFrom(id);
        paintAll();
        // Spectator bind is async — re-paint after gamemode flip or megavex freezes the board.
        player.getScheduler().runDelayed(plugin, task -> {
            if (!ended && players.contains(id)) {
                paintAll();
            }
        }, null, 5L);
        checkWipe();
    }

    public synchronized void killed(LivingEntity mob, Player killer) {
        if (ended || !id.equals(mob.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING))) {
            return;
        }
        mobs.remove(mob);
        alive = Math.max(0, alive - 1);
        if (killer != null && players.contains(killer.getUniqueId())) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
        }
        paintAll();
        maybeAdvance();
    }

    public void quit(Player player) {
        plugin.getGames().unlink(player);
        UUID id = player.getUniqueId();
        players.remove(id);
        quit.add(id);
        rosterNames.putIfAbsent(id, player.getName());
        downed.remove(id);
        dead.remove(id);
        downedAt.remove(id);
        positions.remove(id);
        sneaking.remove(id);
        clearRepairHold(id);
        clearReviveHold(id);
        bodies.clear(id);
        plugin.getMessageManager().send(player, "game.left");
        releaseTargets(id);
        restore(player);
        if (players.isEmpty()) {
            if (!ended) {
                ended = true;
                scrapMobs(() -> {
                    plugin.getGames().forget(this);
                    plugin.getCopies().reset(arena, index);
                });
            }
            return;
        }
        paintAll();
        checkWipe();
    }

    private void place(Player player, Location rough) {
        FoliaManager.runAtLocation(rough, () -> {
            Location at = rough.clone();
            int guard = 0;
            while (guard++ < 6 && at.getBlock().getType().isSolid() && at.getY() < world.getMaxHeight() - 2) {
                at.add(0, 1, 0);
            }
            player.teleportAsync(at).thenRun(() -> player.getScheduler().run(plugin, task -> loadout(player), null));
        });
    }

    private void loadout(Player player) {
        if (!player.isOnline() || ended || !players.contains(player.getUniqueId()) || !saved.containsKey(player.getUniqueId())) {
            return;
        }
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setWalkSpeed(0.2f);
        plugin.getProfiles().gold(player.getUniqueId(), 0);
        plugin.getProfiles().power(player.getUniqueId(), false);
        applyStartHotbar(player);
        paint(player);
        positions.put(player.getUniqueId(), player.getLocation().clone());
        sneaking.put(player.getUniqueId(), player.isSneaking());
        track(player);
        attachMatchBoard(player);
        // Safety: lobby left inv empty — re-apply kit once after settle (teleport/region).
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || ended || !players.contains(player.getUniqueId())) {
                return;
            }
            applyStartHotbar(player);
            paint(player);
        }, null, 10L);
        boolean first;
        synchronized (this) {
            first = !running;
            running = true;
        }
        if (first) {
            tell("game.round", Map.of("round", "1"));
            showRoundTitle();
            fillQueue();
            loop();
        }
    }

    private void applyStartHotbar(Player player) {
        player.getInventory().setItem(dev.rajce.ketchupZombies.weapons.WeaponSlots.KNIFE,
                plugin.getWeaponItems().create(dev.rajce.ketchupZombies.weapons.WeaponId.KNIFE, config.weapons.knife, 0));
        var pistol = plugin.getWeaponItems().create(dev.rajce.ketchupZombies.weapons.WeaponId.PISTOL, config.weapons.pistol, 0);
        player.getInventory().setItem(dev.rajce.ketchupZombies.weapons.WeaponSlots.GUN_START, pistol);
        player.getInventory().setItem(dev.rajce.ketchupZombies.weapons.WeaponSlots.GUN_START + 1,
                dev.rajce.ketchupZombies.weapons.WeaponSlots.emptySlot(plugin));
        player.getInventory().setHeldItemSlot(dev.rajce.ketchupZombies.weapons.WeaponSlots.GUN_START);
        plugin.getProfiles().clearAmmo(player.getUniqueId());
        plugin.getWeaponCombat().bindAmmo(player, pistol);
        dev.rajce.ketchupZombies.weapons.WeaponSlots.enforce(plugin, player);
        dev.rajce.ketchupZombies.weapons.WeaponSlots.syncPerks(plugin, player);
        plugin.getWeaponCombat().hud(player);
        plugin.getWeaponCombat().held(player);
    }

    private void track(Player player) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || !players.contains(player.getUniqueId()) || ended) {
                return;
            }
            UUID id = player.getUniqueId();
            if (downed.contains(id) || dead.contains(id)) {
                bodies.keepSpectator(player);
                Location death = bodies.death(id);
                if (death != null) {
                    positions.put(id, death);
                }
            } else {
                positions.put(id, player.getLocation().clone());
            }
            sneaking.put(id, player.isSneaking());
            SoftCollision.apply(player);
            track(player);
        }, null, 5L);
    }

    private void loop() {
        if (ended) {
            return;
        }
        Player host = host();
        if (host == null) {
            finish(false);
            return;
        }
        // Retired = host left before the tick: loop() picks the next online player.
        host.getScheduler().runDelayed(plugin, task -> {
            try {
                pulse();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Match tick failed: " + ex.getMessage());
            }
            loop();
        }, this::loop, 5L);
    }

    /** Safe landing for void rescue / real respawn: last known ground spot, else arena spawn. */
    public Location respawnSpot(Player player) {
        Location death = bodies.death(player.getUniqueId());
        if (death != null) {
            return death;
        }
        return playerSpawn();
    }

    /** Vanilla death slipped through (/kill etc.): treat as knocked down at the respawn spot. */
    public void afterRespawn(Player player) {
        if (ended || !players.contains(player.getUniqueId()) || downed(player)) {
            return;
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        hurt(player);
    }

    /** Plugin disable: restore every online player synchronously, drop mobs and bodies. */
    public void shutdown() {
        ended = true;
        bodies.clearAll();
        for (LivingEntity mob : mobs) {
            try {
                mob.remove();
            } catch (RuntimeException ignored) {
            }
        }
        mobs.clear();
        for (UUID id : Set.copyOf(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            try {
                restore(player);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Restore on shutdown failed for " + player.getName() + ": " + ex.getMessage());
            }
        }
        players.clear();
    }

    private synchronized void pulse() {
        if (ended) {
            return;
        }
        // Safety net: team wipe must end even if a prior hurt/markDead missed finish.
        if (standing() == 0) {
            finish(false);
            return;
        }
        revive();
        crackWindows();
        repairWindows();
        guideMobs();
        boardCooldown++;
        if (boardCooldown >= 4) {
            boardCooldown = 0;
            paintAll();
        }
        long now = System.currentTimeMillis();
        if (nextWaveAt > 0 && now >= nextWaveAt) {
            spawnScheduledWave();
        }
        if (cursor < queue.size()) {
            spawnWaveNow();
            return;
        }
        maybeAdvance();
    }

    private void spawnWaveNow() {
        while (cursor < queue.size()) {
            ArenaWindow window = pickWindow();
            if (window == null) {
                if (System.currentTimeMillis() - windowNote > 5000) {
                    windowNote = System.currentTimeMillis();
                    tell("game.window");
                }
                return;
            }
            RoundBook.Mob mob = queue.get(cursor++);
            pending++;
            int windowIndex = data.windows().indexOf(window);
            windowSpawns.merge(windowIndex, 1, Integer::sum);
            Location at = shifted(window.spawn());
            FoliaManager.runAtLocation(at, () -> {
                windowBroken.computeIfAbsent(windowIndex, key -> new CopyOnWriteArrayList<>());
                windowNextBreak.putIfAbsent(windowIndex, 0L);
                List<Integer> broken = windowBroken.get(windowIndex);
                boolean open = window.blocks().isEmpty()
                        || (broken != null && broken.size() >= window.blocks().size());
                // Stay at the window spawn until boards are gone, then path out toward players.
                Location spot = open ? exit(at) : at.clone();
                try {
                    if ("ZOMBIE_PIGMAN".equalsIgnoreCase(mob.type())) {
                        spawn(mob, spot, PigZombie.class, true, windowIndex);
                    } else {
                        spawn(mob, spot, Zombie.class, false, windowIndex);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Zombie spawn failed: " + ex.getMessage());
                    synchronized (this) {
                        pending = Math.max(0, pending - 1);
                    }
                    paintAll();
                }
            });
        }
    }

    private static boolean isFireType(String type) {
        if (type == null) {
            return false;
        }
        String name = type.toUpperCase(java.util.Locale.ROOT);
        return name.contains("PIG") || name.contains("MAGMA") || name.contains("BLAZE") || name.equals("FIRE") || name.contains("INFERNO");
    }

    private void crackWindows() {
        long now = System.currentTimeMillis();
        long wait = Math.max(500L, Math.round(config.zombies.windowBreakSeconds * 1000));
        for (int index = 0; index < data.windows().size(); index++) {
            ArenaWindow window = data.windows().get(index);
            if (!windowOpen(window) || window.blocks().isEmpty()) {
                continue;
            }
            List<Integer> broken = windowBroken.get(index);
            if (broken != null && broken.size() >= window.blocks().size()) {
                continue;
            }
            if (!enemyNearWindow(window)) {
                continue;
            }
            long next = windowNextBreak.getOrDefault(index, 0L);
            if (now < next) {
                continue;
            }
            int blockIndex = pickBreakTarget(window, broken == null ? List.of() : broken);
            if (blockIndex < 0) {
                continue;
            }
            WindowBlock block = window.blocks().get(blockIndex);
            Location at = shifted(block.point(), 0.5);
            setBlock(block.point(), Material.AIR.createBlockData());
            List<Integer> list = windowBroken.computeIfAbsent(index, key -> new CopyOnWriteArrayList<>());
            list.add(blockIndex);
            windowNextBreak.put(index, now + wait);
            playMatchSound(at, config.sounds.windowBreak);
            // Last board gone → drop the window lock so they path out toward players.
            if (list.size() >= window.blocks().size()) {
                releaseWindowMobs(index);
            }
        }
    }

    /** After a window is fully open, stop pathing at the boards and chase players. */
    private void releaseWindowMobs(int windowIndex) {
        for (LivingEntity mob : mobs) {
            if (!mob.isValid() || mob.isDead()) {
                continue;
            }
            Integer bound = mob.getPersistentDataContainer().get(windowKey, PersistentDataType.INTEGER);
            if (bound == null || bound != windowIndex) {
                continue;
            }
            FoliaManager.runAtEntity(mob, () -> {
                mob.getPersistentDataContainer().remove(windowKey);
                if (mob instanceof Mob walker) {
                    clearBadTarget(walker);
                }
                steer(mob);
            });
        }
    }

    /**
     * Once a mob's feet Y drops below the Y it spawned at (climbed out of the window),
     * it permanently loses the ability to break windows.
     */
    private boolean canBreakWindows(LivingEntity mob) {
        Double spawnY = mob.getPersistentDataContainer().get(spawnYKey, PersistentDataType.DOUBLE);
        if (spawnY == null) {
            return false;
        }
        return mob.getLocation().getY() + 1.0E-4 >= spawnY;
    }

    /**
     * Prefer a board in front of a zombie within reach. Never break a board that is far from every mob
     * (old fallback wiped any remaining board once a mob was merely near the window spawn).
     */
    private int pickBreakTarget(ArenaWindow window, List<Integer> already) {
        Set<Integer> gone = new HashSet<>(already);
        double minDot = Math.min(0.95, Math.max(0.05, config.zombies.windowBreakDot));
        double maxDistSq = 2.5 * 2.5;
        int bestLook = -1;
        double bestDot = minDot;
        int bestNear = -1;
        double bestNearDist = maxDistSq;
        for (int i = 0; i < window.blocks().size(); i++) {
            if (gone.contains(i)) {
                continue;
            }
            Location center = shifted(window.blocks().get(i).point(), 0.5);
            for (LivingEntity mob : mobs) {
                if (!mob.isValid() || mob.isDead() || !canBreakWindows(mob)) {
                    continue;
                }
                Location eye = mob.getEyeLocation();
                if (eye.getWorld() == null || !eye.getWorld().equals(center.getWorld())) {
                    continue;
                }
                double dist = eye.distanceSquared(center);
                if (dist > maxDistSq) {
                    continue;
                }
                if (dist < bestNearDist) {
                    bestNearDist = dist;
                    bestNear = i;
                }
                Vector to = center.toVector().subtract(eye.toVector());
                if (to.lengthSquared() < 1.0E-4) {
                    return i;
                }
                double dot = eye.getDirection().normalize().dot(to.normalize());
                if (dot >= bestDot) {
                    bestDot = dot;
                    bestLook = i;
                }
            }
        }
        return bestLook >= 0 ? bestLook : bestNear;
    }

    private void clearRepairHold(UUID id) {
        repairHoldSince.remove(id);
        repairHoldWindow.remove(id);
    }

    private void repairWindows() {
        long now = System.currentTimeMillis();
        long holdMs = Math.max(100L, Math.round(config.zombies.windowRepairSeconds * 1000));
        int gold = Math.max(0, config.zombies.windowRepairGold);
        for (UUID id : players) {
            if (downed.contains(id) || dead.contains(id)) {
                clearRepairHold(id);
                continue;
            }
            if (!Boolean.TRUE.equals(sneaking.get(id))) {
                clearRepairHold(id);
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            Location at = positions.get(id);
            if (player == null || !player.isOnline() || at == null) {
                clearRepairHold(id);
                continue;
            }
            int index = nearestDamagedWindow(at);
            if (index < 0) {
                if (repairHoldSince.containsKey(id)) {
                    player.getScheduler().run(plugin, task -> {
                        String text = plugin.getMessageManager().getRaw("game.repair-cancel", "&7You walked away from the window.");
                        player.sendMessage(ColorParser.translateColors(text));
                    }, null);
                }
                clearRepairHold(id);
                continue;
            }
            ArenaWindow window = data.windows().get(index);
            List<Integer> broken = windowBroken.get(index);
            // Enemy at window: cancel charge — no glitchy half-repairs / spam.
            if (broken == null || broken.isEmpty() || enemyNearWindow(window)) {
                clearRepairHold(id);
                continue;
            }
            Integer held = repairHoldWindow.get(id);
            Long since = repairHoldSince.get(id);
            if (held == null || since == null || held != index) {
                repairHoldWindow.put(id, index);
                repairHoldSince.put(id, now);
                // One start cue for the repairing player only.
                SoundKit.play(player, at, config.sounds.windowRepairStart);
                continue;
            }
            if (now - since < holdMs) {
                continue;
            }
            int restored = broken.remove(broken.size() - 1);
            WindowBlock block = window.blocks().get(restored);
            Location repaired = shifted(block.point(), 0.5);
            setBlock(block.point(), windowBoardData(block));
            // One board per charge. Unsneak / leave zone / next board all reset the timer.
            clearRepairHold(id);
            if (broken.isEmpty()) {
                windowBroken.remove(index);
                windowNextBreak.remove(index);
                windowNextRepair.remove(index);
            }
            // Once to the repairing player (was world + player = double).
            SoundKit.play(player, repaired, config.sounds.windowRepair);
            if (gold > 0) {
                plugin.getProfiles().addGold(id, gold);
                int paid = gold;
                player.getScheduler().run(plugin, task -> {
                    plugin.getMessageManager().send(player, "game.repair", Map.of("gold", Integer.toString(paid)));
                    paint(player);
                }, null);
            }
        }
    }

    /** Inclusive block AABB from repair-1 / repair-2, shifted for this copy. */
    private boolean inRepairZone(Location playerAt, ArenaWindow window) {
        if (!window.hasRepairZone() || playerAt.getWorld() == null) {
            return false;
        }
        BlockPoint a = window.repair1();
        BlockPoint b = window.repair2();
        if (!a.world().equals(playerAt.getWorld().getName())) {
            return false;
        }
        int ox = index * config.copies.offsetX;
        int oz = index * config.copies.offsetZ;
        int minX = Math.min(a.x(), b.x()) + ox;
        int maxX = Math.max(a.x(), b.x()) + ox;
        int minY = Math.min(a.y(), b.y());
        int maxY = Math.max(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z()) + oz;
        int maxZ = Math.max(a.z(), b.z()) + oz;
        int px = playerAt.getBlockX();
        int py = playerAt.getBlockY();
        int pz = playerAt.getBlockZ();
        return px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ;
    }

    private void playMatchSound(Location at, PluginConfig.SoundFx fx) {
        FoliaManager.runAtLocation(at, () -> SoundKit.play(at, fx));
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.getScheduler().run(plugin, task -> SoundKit.play(player, at, fx), null);
            }
        }
    }

    private int nearestDamagedWindow(Location at) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int windowIndex = 0; windowIndex < data.windows().size(); windowIndex++) {
            List<Integer> broken = windowBroken.get(windowIndex);
            if (broken == null || broken.isEmpty()) {
                continue;
            }
            ArenaWindow window = data.windows().get(windowIndex);
            if (!windowOpen(window) || !window.hasRepairZone() || !inRepairZone(at, window)) {
                continue;
            }
            BlockPoint a = window.repair1();
            BlockPoint b = window.repair2();
            double midX = (Math.min(a.x(), b.x()) + Math.max(a.x(), b.x())) / 2.0 + 0.5 + index * config.copies.offsetX;
            double midY = (Math.min(a.y(), b.y()) + Math.max(a.y(), b.y())) / 2.0;
            double midZ = (Math.min(a.z(), b.z()) + Math.max(a.z(), b.z())) / 2.0 + 0.5 + index * config.copies.offsetZ;
            double distance = at.distanceSquared(new Location(at.getWorld(), midX, midY, midZ));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = windowIndex;
            }
        }
        return best;
    }

    private boolean enemyNearWindow(ArenaWindow window) {
        if (window.blocks().isEmpty()) {
            return false;
        }
        // 4 blocks — spawn sits outside boards; old 1.5 was too tight after exit() nudge.
        double near = 16.0;
        for (LivingEntity mob : mobs) {
            if (!mob.isValid() || mob.isDead()) {
                continue;
            }
            // Climbed out (Y below spawn) — does not contest repair / break.
            if (!canBreakWindows(mob)) {
                continue;
            }
            Location there = mob.getLocation();
            if (there.getWorld() == null) {
                continue;
            }
            if (window.spawn() != null) {
                Location spawn = shifted(window.spawn());
                if (there.getWorld().equals(spawn.getWorld()) && there.distanceSquared(spawn) <= near) {
                    return true;
                }
            }
            for (WindowBlock block : window.blocks()) {
                Location at = shifted(block.point(), 0.5);
                if (there.getWorld().equals(at.getWorld()) && there.distanceSquared(at) <= near) {
                    return true;
                }
            }
        }
        return false;
    }

    private Location exit(Location at) {
        Location spot = at.clone();
        int guard = 0;
        while (guard++ < 4 && spot.getBlock().getType().isSolid() && spot.getY() < world.getMaxHeight() - 2) {
            spot.add(0, 1, 0);
        }
        Player target = nearestPlayer(spot);
        Location there = target == null ? null : positions.get(target.getUniqueId());
        if (there != null && there.getWorld() != null && there.getWorld().equals(spot.getWorld())) {
            Vector out = there.toVector().subtract(spot.toVector());
            out.setY(0);
            if (out.lengthSquared() > 0.04) {
                spot.add(out.normalize().multiply(0.75));
            }
        }
        return clearOfMobs(spot);
    }

    /** Nudge off chickens / other living entities so zombies do not stack on them. */
    private Location clearOfMobs(Location spot) {
        if (!occupied(spot)) {
            return spot;
        }
        double[][] offsets = {
                {0.75, 0, 0}, {-0.75, 0, 0}, {0, 0, 0.75}, {0, 0, -0.75},
                {0.75, 0, 0.75}, {-0.75, 0, 0.75}, {0.75, 0, -0.75}, {-0.75, 0, -0.75},
                {0, 0.5, 0}, {0, 1.0, 0}
        };
        for (double[] offset : offsets) {
            Location tryAt = spot.clone().add(offset[0], offset[1], offset[2]);
            if (!tryAt.getBlock().getType().isSolid() && !occupied(tryAt)) {
                return tryAt;
            }
        }
        return spot;
    }

    private boolean occupied(Location at) {
        return !at.getWorld().getNearbyEntities(at, 0.45, 0.9, 0.45, entity ->
                entity instanceof LivingEntity
                        && !(entity instanceof Player)
                        && entity.isValid()
                        && !entity.isDead()).isEmpty();
    }

    private void spawn(RoundBook.Mob spec, Location at, Class<? extends Zombie> type, boolean pig, int windowIndex) {
        if (ended) {
            synchronized (this) {
                pending = Math.max(0, pending - 1);
            }
            return;
        }
        double speed = Math.max(0.05, spec.speed() * Math.max(0.1, config.zombies.speedScale));
        double follow = Math.max(config.zombies.followRange, spec.followRange());
        LivingEntity mob = world.spawn(at, type, entity -> {
            entity.setCustomNameVisible(false);
            entity.customName(null);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(false);
            entity.setShouldBurnInDay(false);
            entity.setCollidable(false);
            SoftCollision.joinZombie(plugin, entity);
            if (entity instanceof Zombie zombie) {
                zombie.setAdult();
            }
            entity.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, id);
            entity.getPersistentDataContainer().set(windowKey, PersistentDataType.INTEGER, windowIndex);
            entity.getPersistentDataContainer().set(spawnYKey, PersistentDataType.DOUBLE, at.getY());
            attribute(entity, Attribute.MAX_HEALTH, spec.health());
            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.setHealth(Math.min(spec.health(), entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
            }
            attribute(entity, Attribute.ATTACK_DAMAGE, spec.damage());
            attribute(entity, Attribute.ARMOR, spec.armor());
            attribute(entity, Attribute.MOVEMENT_SPEED, speed);
            attribute(entity, Attribute.FOLLOW_RANGE, follow);
            if (pig || isFireType(spec.type())) {
                entity.addScoreboardTag("kz-fire");
            }
            if (pig) {
                entity.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                entity.getEquipment().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
                entity.getEquipment().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
                entity.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
                ((PigZombie) entity).setAngry(true);
            } else {
                entity.getEquipment().setChestplate(leather(Material.LEATHER_CHESTPLATE));
                entity.getEquipment().setLeggings(leather(Material.LEATHER_LEGGINGS));
                entity.getEquipment().setItemInMainHand(new ItemStack(Material.WOODEN_AXE));
            }
            if (entity.getEquipment() != null) {
                entity.getEquipment().setHelmetDropChance(0f);
                entity.getEquipment().setChestplateDropChance(0f);
                entity.getEquipment().setLeggingsDropChance(0f);
                entity.getEquipment().setBootsDropChance(0f);
                entity.getEquipment().setItemInMainHandDropChance(0f);
            }
        });
        mobs.add(mob);
        steer(mob);
        synchronized (this) {
            alive++;
            pending = Math.max(0, pending - 1);
        }
        paintAll();
        maybeAdvance();
    }

    /** Window first (while boards remain), then nearest *standing* player. Still too long → fresh target. */
    private void guideMobs() {
        for (LivingEntity mob : mobs) {
            if (!mob.isValid() || mob.isDead()) {
                stuckSince.remove(mob.getUniqueId());
                stuckAt.remove(mob.getUniqueId());
                unstick.remove(mob.getUniqueId());
                continue;
            }
            FoliaManager.runAtEntity(mob, () -> steer(mob));
        }
    }

    private void steer(LivingEntity entity) {
        if (!(entity instanceof Mob walker) || !entity.isValid() || entity.isDead()) {
            return;
        }
        clearBadTarget(walker);
        SoftCollision.apply(entity);

        UUID id = entity.getUniqueId();
        if (atClosedWindowFloor(entity)) {
            // Standing at the boards to break them. That wait is not a stuck.
            stuckAt.put(id, entity.getLocation().clone());
            stuckSince.put(id, System.currentTimeMillis());
            unstick.remove(id);
        } else {
            int idle = idleState(entity);
            if (idle == 1) {
                unstick.remove(id);
            } else if (idle == 2) {
                unstick.add(id);
                try {
                    walker.setTarget(null);
                } catch (RuntimeException ignored) {
                }
                try {
                    walker.getPathfinder().stopPathfinding();
                } catch (RuntimeException ignored) {
                }
            }
        }
        // Wedged on the lip or in a corner: chase a player until the mob actually moves.
        if (unstick.contains(id)) {
            chase(walker, entity);
            return;
        }

        Integer windowIndex = entity.getPersistentDataContainer().get(windowKey, PersistentDataType.INTEGER);
        if (windowIndex != null && windowIndex >= 0 && windowIndex < data.windows().size()) {
            ArenaWindow window = data.windows().get(windowIndex);
            List<Integer> broken = windowBroken.get(windowIndex);
            boolean open = window.blocks().isEmpty()
                    || (broken != null && broken.size() >= window.blocks().size());
            if (!open) {
                Location goal = windowGoal(window, broken);
                if (goal != null) {
                    try {
                        walker.setTarget(null);
                    } catch (RuntimeException ignored) {
                    }
                    walker.getPathfinder().moveTo(apart(entity, goal), 1.15);
                    return;
                }
            }
            entity.getPersistentDataContainer().remove(windowKey);
        }
        chase(walker, entity);
    }

    private void chase(Mob walker, LivingEntity entity) {
        Player target = nearestPlayer(entity.getLocation());
        if (target == null) {
            try {
                walker.setTarget(null);
            } catch (RuntimeException ignored) {
            }
            try {
                walker.getPathfinder().stopPathfinding();
            } catch (RuntimeException ignored) {
            }
            return;
        }
        Location there = positions.get(target.getUniqueId());
        if (there == null) {
            there = target.getLocation();
        }
        if (target.getWorld() == null || entity.getWorld() == null || !target.getWorld().equals(entity.getWorld())) {
            try {
                walker.setTarget(null);
            } catch (RuntimeException ignored) {
            }
            return;
        }
        try {
            walker.setTarget(target);
        } catch (RuntimeException ignored) {
        }
        walker.getPathfinder().moveTo(apart(entity, there), 1.0);
    }

    /**
     * @return 1 if the mob moved, 0 if it is still inside the idle window, 2 once per {@code stuck-seconds}
     */
    private int idleState(LivingEntity entity) {
        long now = System.currentTimeMillis();
        long limit = Math.max(1000L, Math.round(Math.max(1.0, config.zombies.stuckSeconds) * 1000));
        UUID id = entity.getUniqueId();
        Location here = entity.getLocation();
        Location was = stuckAt.get(id);
        Long since = stuckSince.get(id);
        if (was == null || since == null || was.getWorld() == null || here.getWorld() == null
                || !was.getWorld().equals(here.getWorld())
                || was.distanceSquared(here) >= 0.2) {
            stuckAt.put(id, here.clone());
            stuckSince.put(id, now);
            return 1;
        }
        if (now - since >= limit) {
            stuckSince.put(id, now);
            return 2;
        }
        return 0;
    }

    /** Feet on the approach floor of a window that still has boards. Not the sill. */
    private boolean atClosedWindowFloor(LivingEntity entity) {
        Integer windowIndex = entity.getPersistentDataContainer().get(windowKey, PersistentDataType.INTEGER);
        if (windowIndex == null || windowIndex < 0 || windowIndex >= data.windows().size()) {
            return false;
        }
        ArenaWindow window = data.windows().get(windowIndex);
        List<Integer> broken = windowBroken.get(windowIndex);
        boolean open = window.blocks().isEmpty()
                || (broken != null && broken.size() >= window.blocks().size());
        if (open || window.spawn() == null) {
            return false;
        }
        Location spawn = shifted(window.spawn());
        Location feet = entity.getLocation();
        if (feet.getWorld() == null || !feet.getWorld().equals(spawn.getWorld())) {
            return false;
        }
        if (feet.getY() > spawn.getY() + 0.35) {
            return false;
        }
        return feet.distanceSquared(spawn) <= 16;
    }

    private void clearBadTarget(Mob walker) {
        LivingEntity current = walker.getTarget();
        if (!(current instanceof Player marked) || !downed(marked)) {
            return;
        }
        try {
            walker.setTarget(null);
        } catch (RuntimeException ignored) {
        }
        if (walker instanceof PigZombie pig) {
            pig.setAngry(false);
        }
    }

    /** Drop AI lock on a downed/dead player so mobs do not swarm the corpse. */
    private void retargetMobsAwayFrom(UUID lost) {
        for (LivingEntity mob : mobs) {
            if (!mob.isValid() || mob.isDead()) {
                continue;
            }
            FoliaManager.runAtEntity(mob, () -> {
                if (!(mob instanceof Mob walker) || !mob.isValid()) {
                    return;
                }
                LivingEntity current = walker.getTarget();
                if (current != null && current.getUniqueId().equals(lost)) {
                    try {
                        walker.setTarget(null);
                    } catch (RuntimeException ignored) {
                    }
                    if (walker instanceof PigZombie pig) {
                        pig.setAngry(false);
                    }
                }
                steer(mob);
            });
        }
    }

    private Location windowGoal(ArenaWindow window, List<Integer> broken) {
        for (int i = 0; i < window.blocks().size(); i++) {
            if (broken != null && broken.contains(i)) {
                continue;
            }
            return shifted(window.blocks().get(i).point(), 0.5);
        }
        if (window.spawn() != null) {
            return shifted(window.spawn());
        }
        return null;
    }

    /** Stable side offset so a horde does not path to one pixel. Tight gaps still allow overlap. */
    private Location lane(LivingEntity entity, Location spot) {
        if (entity == null || spot == null) {
            return spot;
        }
        Location face = spot;
        Vector forward = flatToward(entity.getLocation(), face);
        if (forward == null) {
            return spot;
        }
        int lane = Math.floorMod(entity.getUniqueId().hashCode(), 5) - 2;
        if (lane == 0) {
            return spot;
        }
        Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        return spot.clone().add(side.multiply(lane * 0.4));
    }

    private Location spread(LivingEntity entity, Location goal) {
        if (goal == null || entity.getLocation().getWorld() == null || goal.getWorld() == null) {
            return goal;
        }
        if (!entity.getLocation().getWorld().equals(goal.getWorld())) {
            return goal;
        }
        if (entity.getLocation().distanceSquared(goal) <= 6.25) {
            return goal;
        }
        return lane(entity, goal);
    }

    /** Keep a horde from choosing the same block. Tight holes still allow overlap. */
    private Location apart(LivingEntity entity, Location goal) {
        Location spaced = spread(entity, goal);
        if (spaced == null || entity.getLocation().getWorld() == null) {
            return spaced;
        }
        double reach = Math.max(0.0, config.zombies.crowdDistance);
        if (reach <= 0.0) {
            return spaced;
        }
        LivingEntity crowd = null;
        double best = reach * reach;
        Location here = entity.getLocation();
        for (LivingEntity other : mobs) {
            if (other == entity || !other.isValid() || other.isDead() || other.getLocation().getWorld() == null) {
                continue;
            }
            if (!other.getLocation().getWorld().equals(here.getWorld())) {
                continue;
            }
            double dist = other.getLocation().distanceSquared(here);
            if (dist < best) {
                best = dist;
                crowd = other;
            }
        }
        if (crowd == null) {
            return spaced;
        }
        Vector away = here.toVector().subtract(crowd.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(1, 0, 0);
        } else {
            away.normalize();
        }
        double push = Math.max(0.0, config.zombies.crowdApart);
        return spaced.clone().add(away.multiply(push));
    }

    /** Drop a player target before that player changes worlds. Same region only; the rest is next tick. */
    private void releaseTargets(UUID playerId) {
        for (LivingEntity mob : List.copyOf(mobs)) {
            if (!(mob instanceof Mob walker) || !mob.isValid()) {
                continue;
            }
            Runnable clear = () -> {
                if (!walker.isValid() || walker.getTarget() == null || !playerId.equals(walker.getTarget().getUniqueId())) {
                    return;
                }
                try {
                    walker.setTarget(null);
                } catch (RuntimeException ignored) {
                }
            };
            if (Bukkit.isOwnedByCurrentRegion(mob)) {
                clear.run();
            } else {
                mob.getScheduler().run(plugin, task -> clear.run(), null);
            }
        }
    }

    /** Remove every mob on its own region, then run {@code then}. A melee hit after a world change crashes Folia. */
    private void scrapMobs(Runnable then) {
        List<LivingEntity> crowd = new ArrayList<>(mobs);
        mobs.clear();
        if (crowd.isEmpty()) {
            then.run();
            return;
        }
        AtomicInteger left = new AtomicInteger(crowd.size());
        Runnable done = () -> {
            if (left.decrementAndGet() == 0) {
                then.run();
            }
        };
        for (LivingEntity mob : crowd) {
            if (!mob.isValid()) {
                SoftCollision.leaveZombie(plugin, mob);
                done.run();
                continue;
            }
            mob.getScheduler().run(plugin, task -> {
                if (mob instanceof Mob walker) {
                    try {
                        walker.setTarget(null);
                    } catch (RuntimeException ignored) {
                    }
                }
                SoftCollision.leaveZombie(plugin, mob);
                if (mob.isValid()) {
                    mob.remove();
                }
                done.run();
            }, done);
        }
    }

    private static Vector flatToward(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return null;
        }
        Vector vector = to.toVector().subtract(from.toVector());
        vector.setY(0);
        if (vector.lengthSquared() < 1.0E-4) {
            return null;
        }
        return vector.normalize();
    }

    private void clearReviveHold(UUID id) {
        reviveHoldSince.remove(id);
        reviveBy.remove(id);
        revivePausedLeft.remove(id);
    }

    private void revive() {
        long now = System.currentTimeMillis();
        long deadAfter = Math.max(1000L, Math.round(config.shops.deadSeconds * 1000));
        double range = Math.max(0.5, config.shops.reviveRange);
        double rangeSq = range * range;
        long reviveMs = Math.max(100L, Math.round(config.shops.reviveSeconds * 1000));
        long fastMs = Math.max(50L, Math.round(config.shops.fastReviveSeconds * 1000));

        Set<UUID> holding = ConcurrentHashMap.newKeySet();
        for (UUID other : players) {
            if (downed.contains(other) || dead.contains(other)) {
                continue;
            }
            Player mate = Bukkit.getPlayer(other);
            Location there = positions.get(other);
            if (mate == null || !mate.isOnline() || there == null || there.getWorld() == null) {
                continue;
            }
            UUID nearest = null;
            double best = Double.MAX_VALUE;
            for (UUID id : downed) {
                if (dead.contains(id)) {
                    continue;
                }
                Location at = positions.get(id);
                if (at == null || at.getWorld() == null || !at.getWorld().equals(there.getWorld())) {
                    continue;
                }
                double dist = there.distanceSquared(at);
                if (dist > rangeSq || dist >= best) {
                    continue;
                }
                best = dist;
                nearest = id;
            }
            if (nearest == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(sneaking.get(other))) {
                plugin.getMessageManager().actionBar(mate, "game.revive-hint", null, false);
                continue;
            }
            holding.add(nearest);
            UUID previous = reviveBy.get(nearest);
            Long since = reviveHoldSince.get(nearest);
            if (previous == null || !previous.equals(other) || since == null) {
                // Revive started → freeze DEAD countdown at remaining time.
                reviveBy.put(nearest, other);
                reviveHoldSince.put(nearest, now);
                Long downSince = downedAt.get(nearest);
                long leftMs = downSince == null ? deadAfter : Math.max(0L, deadAfter - (now - downSince));
                revivePausedLeft.put(nearest, leftMs);
                since = now;
            }
            boolean fast = plugin.getProfiles().perk(mate.getUniqueId(), Profiles.FAST_REVIVE);
            long need = fast ? fastMs : reviveMs;
            long left = Math.max(0L, need - (now - since));
            String time = String.format(java.util.Locale.US, "%.1f", left / 1000.0);
            plugin.getMessageManager().actionBar(mate, "game.reviving", Map.of("time", time), false);
            if (now - since >= need) {
                clearReviveHold(nearest);
                downedAt.remove(nearest);
                Player downedPlayer = Bukkit.getPlayer(nearest);
                if (downedPlayer != null) {
                    downedPlayer.getScheduler().run(plugin, task -> stand(downedPlayer, true), null);
                }
            }
        }

        for (UUID id : Set.copyOf(downed)) {
            if (dead.contains(id)) {
                continue;
            }
            // Actively being revived → DEAD timer paused (so 1s left still allows full revive).
            if (holding.contains(id)) {
                long leftMs = revivePausedLeft.getOrDefault(id, deadAfter);
                String time = String.format(java.util.Locale.US, "%.1f", leftMs / 1000.0);
                bodies.timerRaw(id, plugin.getMessageManager().getRaw("game.revive-timer", "&c%time%s").replace("%time%", time));
                continue;
            }
            if (reviveHoldSince.containsKey(id)) {
                // Interrupted revive → full reset to deadSeconds (e.g. 25s).
                clearReviveHold(id);
                downedAt.put(id, now);
            }
            Long since = downedAt.get(id);
            if (since != null && now - since >= deadAfter) {
                markDead(id);
                continue;
            }
            double leftSec = since == null ? config.shops.deadSeconds : Math.max(0.0, (deadAfter - (now - since)) / 1000.0);
            String time = String.format(java.util.Locale.US, "%.1f", leftSec);
            bodies.timerRaw(id, plugin.getMessageManager().getRaw("game.revive-timer", "&c%time%s").replace("%time%", time));
        }
        for (UUID id : dead) {
            bodies.timerRaw(id, plugin.getMessageManager().getRaw("game.dead-timer", "&8DEAD"));
        }
    }

    private void markDead(UUID id) {
        if (!dead.add(id)) {
            return;
        }
        downed.remove(id);
        clearReviveHold(id);
        downedAt.remove(id);
        plugin.getProfiles().clearPerks(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.getScheduler().run(plugin, task -> {
                bodies.keepSpectator(player);
                player.removePotionEffect(PotionEffectType.SPEED);
                plugin.getProfiles().clearPerks(id);
                dev.rajce.ketchupZombies.weapons.WeaponSlots.syncPerks(plugin, player);
                plugin.getMessageManager().actionBar(player, "game.dead", null, false);
                plugin.getMessageManager().send(player, "game.dead");
                paint(player);
            }, null);
        }
        bodies.timerRaw(id, plugin.getMessageManager().getRaw("game.dead-timer", "&8DEAD"));
        retargetMobsAwayFrom(id);
        paintAll();
        checkWipe();
    }

    private void stand(Player player, boolean tell) {
        // Teammate sneak revive: stay on the corpse, half of max health.
        wake(player, bodies.death(player.getUniqueId()), false);
        if (tell) {
            plugin.getMessageManager().send(player, "game.revived");
        }
    }

    /** Next round: downed and dead both land on the arena spawn at full health. Dead already lost perks. */
    private void roundRevive(Player player) {
        wake(player, playerSpawn(), true);
    }

    private void wake(Player player, Location dest, boolean full) {
        UUID id = player.getUniqueId();
        downed.remove(id);
        dead.remove(id);
        downedAt.remove(id);
        clearReviveHold(id);
        bodies.release(player);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        double max = 20.0;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            max = Math.max(1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        player.setHealth(full ? max : Math.max(1.0, max / 2.0));
        SoftCollision.apply(player);
        dev.rajce.ketchupZombies.weapons.WeaponSlots.syncPerks(plugin, player);
        if (dest != null && dest.getWorld() != null) {
            player.teleportAsync(dest);
        }
        paint(player);
        paintAll();
    }

    private synchronized void fillQueue() {
        waves.clear();
        waves.addAll(book.waves(round));
        waveIndex = -1;
        queue.clear();
        cursor = 0;
        // Keep windowBroken / boards as-is across rounds — no auto-repair.
        windowSpawns.clear();
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
        roundClock = System.currentTimeMillis();
        Set<UUID> returning = new HashSet<>();
        returning.addAll(dead);
        returning.addAll(downed);
        for (UUID id : returning) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.getScheduler().run(plugin, task -> roundRevive(player), null);
            } else {
                dead.remove(id);
                downed.remove(id);
                downedAt.remove(id);
                clearReviveHold(id);
                bodies.clear(id);
            }
        }
        // First wave at cumulative delay[0] (default 10s). Later waves at delay[1], delay[2], …
        scheduleWaveAt(0);
        paintAll();
    }

    private synchronized void spawnScheduledWave() {
        nextWaveAt = 0;
        int next = waveIndex + 1;
        if (next >= waves.size()) {
            return;
        }
        waveIndex = next;
        loadWave();
        spawnWaveNow();
        scheduleWaveAt(waveIndex + 1);
    }

    private void scheduleWaveAt(int wave) {
        if (wave < 0 || wave >= waves.size()) {
            nextWaveAt = 0;
            return;
        }
        nextWaveAt = roundClock + waveDelayMs(round, wave);
    }

    private long waveDelayMs(int roundNumber, int wave) {
        List<Integer> delays = waveDelays(roundNumber);
        if (wave < delays.size()) {
            return Math.max(0L, delays.get(wave)) * 1000L;
        }
        if (delays.isEmpty()) {
            return Math.round(config.zombies.waveClearDelaySeconds * 1000) * (wave + 1L);
        }
        int last = delays.get(delays.size() - 1);
        int gap = delays.size() >= 2 ? Math.max(1, delays.get(delays.size() - 1) - delays.get(delays.size() - 2)) : 10;
        return (last + gap * (wave - delays.size() + 1L)) * 1000L;
    }

    private List<Integer> waveDelays(int roundNumber) {
        String raw = null;
        if (config.zombies.waveDelays != null) {
            raw = config.zombies.waveDelays.get(Integer.toString(roundNumber));
        }
        if (raw == null || raw.isBlank()) {
            raw = config.zombies.waveDelayFallback;
        }
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            out.add(10);
            return out;
        }
        for (String part : raw.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip junk
            }
        }
        if (out.isEmpty()) {
            out.add(10);
        }
        return out;
    }

    private synchronized void loadWave() {
        queue.clear();
        cursor = 0;
        spawnCooldown = 0;
        windowSpawns.clear();
        if (waveIndex < 0 || waveIndex >= waves.size()) {
            return;
        }
        int people = Math.max(1, players.size());
        for (RoundBook.Mob mob : waves.get(waveIndex).mobs()) {
            int count = (int) Math.ceil(mob.count() * 0.5 * people);
            for (int i = 0; i < Math.max(1, count); i++) {
                queue.add(mob);
            }
        }
        tell("game.wave", Map.of(
                "wave", Integer.toString(waveIndex + 1),
                "waves", Integer.toString(Math.max(1, waves.size())),
                "round", Integer.toString(round)
        ));
    }

    private synchronized void maybeAdvance() {
        if (ended || cursor < queue.size() || pending > 0 || alive > 0) {
            return;
        }
        if (nextWaveAt > 0 || waveIndex + 1 < waves.size()) {
            return;
        }
        if (round >= 3) {
            finish(true);
            return;
        }
        round++;
        tell("game.clear");
        tell("game.round", Map.of("round", Integer.toString(round)));
        showRoundTitle();
        fillQueue();
    }

    private void showRoundTitle() {
        PluginConfig.RoundTitle cfg = config.titles.round;
        String titleRaw = (cfg.title == null ? "&e&lKolo %round%" : cfg.title).replace("%round%", Integer.toString(round));
        String subRaw = cfg.subtitle == null ? "" : cfg.subtitle.replace("%round%", Integer.toString(round));
        Component title = ColorParser.translateColors(titleRaw);
        Component subtitle = subRaw.isBlank() ? Component.empty() : ColorParser.translateColors(subRaw);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(Math.max(0, cfg.fadeIn) * 50L),
                Duration.ofMillis(Math.max(1, cfg.stay) * 50L),
                Duration.ofMillis(Math.max(0, cfg.fadeOut) * 50L)
        );
        Title shown = Title.title(title, subtitle, times);
        PluginConfig.SoundFx roundSound = config.sounds.roundStart;
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.getScheduler().run(plugin, task -> {
                    player.showTitle(shown);
                    SoundKit.play(player, player.getLocation(), roundSound);
                }, null);
            }
        }
    }

    /** Knock title for teammates. The downed player only gets the chat line. Placeholders: %player% %section% %round%. */
    private void broadcastKnockTitle(UUID victim, String playerName, String section) {
        String roundText = Integer.toString(round);
        String sectionText = section == null || section.isBlank() ? "-" : section;
        String titleRaw = plugin.getMessageManager().getRaw("game.death-title", "&c%player%")
                .replace("%player%", playerName)
                .replace("%section%", sectionText)
                .replace("%round%", roundText);
        String subRaw = plugin.getMessageManager().getRaw("game.death-subtitle", "&7Knocked down in &f%section%")
                .replace("%player%", playerName)
                .replace("%section%", sectionText)
                .replace("%round%", roundText);
        Component title = titleRaw.isBlank() ? Component.empty() : ColorParser.translateColors(titleRaw);
        Component subtitle = subRaw.isBlank() ? Component.empty() : ColorParser.translateColors(subRaw);
        Title shown = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(10 * 50L),
                Duration.ofMillis(40 * 50L),
                Duration.ofMillis(10 * 50L)
        ));
        for (UUID mateId : players) {
            if (mateId.equals(victim)) {
                continue;
            }
            Player mate = Bukkit.getPlayer(mateId);
            if (mate != null && mate.isOnline()) {
                mate.getScheduler().run(plugin, task -> {
                    if (mate.isOnline()) {
                        mate.showTitle(shown);
                    }
                }, null);
            }
        }
    }

    private int zombiesLeft() {
        int left = Math.max(0, queue.size() - cursor + alive + pending);
        int people = Math.max(1, players.size());
        for (int i = waveIndex + 1; i < waves.size(); i++) {
            for (RoundBook.Mob mob : waves.get(i).mobs()) {
                left += Math.max(1, (int) Math.ceil(mob.count() * 0.5 * people));
            }
        }
        return left;
    }

    /** Match-end sendTitle. Placeholder %round%. Endless later uses game.survived-title. */
    private void showEndTitle(boolean won) {
        String roundText = Integer.toString(round);
        String titleKey = won ? "game.win-title" : "game.lose-title";
        String subKey = won ? "game.win-subtitle" : "game.lose-subtitle";
        String titleFallback = won ? "&a&lYou win" : "&c&lYou lost";
        String titleRaw = plugin.getMessageManager().getRaw(titleKey, titleFallback).replace("%round%", roundText);
        String subRaw = plugin.getMessageManager().getRaw(subKey, "").replace("%round%", roundText);
        Component title = titleRaw.isBlank() ? Component.empty() : ColorParser.translateColors(titleRaw);
        Component subtitle = subRaw.isBlank() ? Component.empty() : ColorParser.translateColors(subRaw);
        Title shown = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(10 * 50L),
                Duration.ofMillis(60 * 50L),
                Duration.ofMillis(20 * 50L)
        ));
        for (UUID id : Set.copyOf(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) {
                    player.showTitle(shown);
                }
            }, null);
        }
    }

    private void finish(boolean won) {
        if (ended) {
            return;
        }
        ended = true;
        lucky.stop();
        showEndTitle(won);
        tell(won ? "game.win" : "game.lose");
        bodies.clearAll();
        scrapMobs(() -> {
            for (UUID id : Set.copyOf(players)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && Bukkit.isOwnedByCurrentRegion(player)) {
                    restore(player);
                } else if (player != null) {
                    player.getScheduler().run(plugin, task -> restore(player), null);
                } else {
                    dropBoard(id);
                    cleaned.add(id);
                    saved.remove(id);
                }
            }
            players.clear();
            plugin.getGames().forget(this);
            plugin.getCopies().reset(arena, index);
        });
    }

    private void restore(Player player) {
        if (!cleaned.add(player.getUniqueId())) {
            return;
        }
        // Must unlink BEFORE hubBoard.show — HubBoard refuses players still inMatch.
        plugin.getGames().unlink(player);
        bodies.clear(player.getUniqueId());
        bodies.unlockMovement(player);
        // Shop GUI open at match end must not survive into the hub.
        player.closeInventory();
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        plugin.getProfiles().clear(player.getUniqueId());
        org.bukkit.attribute.AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            NamespacedKey extra = new NamespacedKey(plugin, "extra-health");
            health.getModifiers().stream().filter(modifier -> extra.equals(modifier.getKey())).toList().forEach(health::removeModifier);
            player.setHealth(Math.max(1.0, health.getValue()));
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        Saved kit = saved.remove(player.getUniqueId());
        dropBoard(player.getUniqueId());
        player.setLevel(0);
        player.setExp(0f);
        applyKit(player, kit);
        Location hub = plugin.hubSpawn();
        Location dest = hub != null ? hub : (kit != null && kit.back != null ? kit.back.clone() : null);
        if (dest != null) {
            GameMode mode = kit != null && kit.mode != GameMode.SPECTATOR ? kit.mode : GameMode.ADVENTURE;
            UUID playerId = player.getUniqueId();
            plugin.getGames().markReturn(playerId, dest);
            player.teleportAsync(dest).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    plugin.getGames().clearReturn(playerId);
                }
            });
            player.getScheduler().runDelayed(plugin, task -> {
                if (!player.isOnline()) {
                    return;
                }
                bodies.unlockMovement(player);
                applyKit(player, kit);
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(mode);
                    player.setFlying(false);
                    player.setAllowFlight(mode == GameMode.CREATIVE);
                }
                showHubBoard(player);
            }, null, 5L);
        } else {
            showHubBoard(player);
        }
        plugin.getGames().onMatchRestore(player);
    }

    private void showHubBoard(Player player) {
        if (plugin.getHubBoard() == null || !player.isOnline()) {
            return;
        }
        plugin.getHubBoard().show(player);
    }

    /** Always clear game inv, then restore hub snapshot when present. */
    private static void applyKit(Player player, Saved kit) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        GameMode mode = kit != null && kit.mode != GameMode.SPECTATOR ? kit.mode : GameMode.ADVENTURE;
        if (kit != null) {
            player.getInventory().setContents(ItemCopies.of(kit.contents));
            player.getInventory().setArmorContents(ItemCopies.of(kit.armor));
        }
        player.setGameMode(mode);
        player.setFlying(false);
        player.setAllowFlight(mode == GameMode.CREATIVE);
    }

    private void buyDoor(Player player, ArenaDoor door) {
        String key = door.key();
        if (openedDoors.contains(key)) {
            return;
        }
        if (door.price() == null) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThat door has no price."));
            return;
        }
        boolean fromOpen = open.contains(door.from());
        boolean toOpen = open.contains(door.to());
        if (!fromOpen && !toOpen) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThat door is not connected to an open section."));
            return;
        }
        if (fromOpen && toOpen) {
            hideDoor(door);
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), door.price())) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        open.add(fromOpen ? door.to() : door.from());
        Location soundAt = null;
        for (BlockPoint block : door.blocks()) {
            Location at = shifted(block, 0);
            if (soundAt == null) {
                soundAt = at.clone().add(0.5, 0.5, 0.5);
            }
            setBlock(block, Material.AIR.createBlockData());
        }
        if (soundAt != null) {
            Location play = soundAt;
            playMatchSound(play, config.sounds.doorBuy);
        }
        hideDoor(door);
        paint(player);
        plugin.getMessageManager().send(player, "game.door");
    }

    private void hideDoor(ArenaDoor door) {
        openedDoors.add(door.key());
        plugin.getHolograms().clear(arena, index, door.hologramA());
        plugin.getHolograms().clear(arena, index, door.hologramB());
    }

    private void buyPower(Player player) {
        if (power) {
            plugin.getMessageManager().send(player, "game.power");
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), config.power.price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        power = true;
        for (UUID id : players) {
            plugin.getProfiles().power(id, true);
        }
        plugin.getHolograms().showPower(data, index, true);
        paint(player);
        SoundKit.play(player, player.getLocation(), config.sounds.shopBuy);
        plugin.getMessageManager().send(player, "game.power");
    }

    private ArenaWindow pickWindow() {
        List<ArenaWindow> active = activeWindows();
        if (active.isEmpty()) {
            return null;
        }
        ArenaWindow best = null;
        int bestCount = Integer.MAX_VALUE;
        int bestIndex = Integer.MAX_VALUE;
        for (ArenaWindow window : active) {
            int index = data.windows().indexOf(window);
            int count = windowSpawns.getOrDefault(index, 0);
            if (count < bestCount || (count == bestCount && index < bestIndex)) {
                best = window;
                bestCount = count;
                bestIndex = index;
            }
        }
        return best;
    }

    private List<ArenaWindow> activeWindows() {
        List<ArenaWindow> active = new ArrayList<>();
        int reach = config.zombies.spawnDistance;
        for (ArenaWindow window : data.windows()) {
            if (window.spawn() == null || !windowOpen(window)) {
                continue;
            }
            Location at = shifted(window.spawn());
            for (UUID id : players) {
                if (downed.contains(id) || dead.contains(id)) {
                    continue;
                }
                Location there = positions.get(id);
                if (there == null || there.getWorld() == null || !there.getWorld().equals(at.getWorld())) {
                    continue;
                }
                if (there.distanceSquared(at) <= (double) reach * reach) {
                    active.add(window);
                    break;
                }
            }
        }
        return active;
    }

    private boolean windowOpen(ArenaWindow window) {
        if (window.section() != null) {
            return open.contains(window.section());
        }
        if (window.spawn() == null) {
            return false;
        }
        Location at = shifted(window.spawn());
        for (Map.Entry<String, ArenaSection> entry : data.sections().entrySet()) {
            if (open.contains(entry.getKey()) && inside(entry.getValue(), at)) {
                return true;
            }
        }
        return false;
    }

    private boolean inside(ArenaSection section, Location at) {
        if (!world.getName().equals(section.world())) {
            return false;
        }
        double x = at.getX() - index * config.copies.offsetX;
        double z = at.getZ() - index * config.copies.offsetZ;
        double y = at.getY();
        int minX = Math.min(section.x1(), section.x2());
        int maxX = Math.max(section.x1(), section.x2()) + 1;
        int minY = Math.min(section.y1(), section.y2());
        int maxY = Math.max(section.y1(), section.y2()) + 1;
        int minZ = Math.min(section.z1(), section.z2());
        int maxZ = Math.max(section.z1(), section.z2()) + 1;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Rebuild every open-section window from the arena snapshot (called from fillQueue each round). */
    private void restoreWindows() {
        windowBroken.clear();
        windowNextBreak.clear();
        windowNextRepair.clear();
        for (ArenaWindow window : data.windows()) {
            if (!windowOpen(window)) {
                continue;
            }
            for (WindowBlock block : window.blocks()) {
                setBlock(block.point(), windowBoardData(block));
            }
        }
    }

    /** Arena-stored board BlockData (`data.getAsString()` from editor). Oak slab only if YML is corrupt. */
    private BlockData windowBoardData(WindowBlock block) {
        String raw = block.data();
        if (raw != null && !raw.isBlank()) {
            try {
                return Bukkit.createBlockData(raw);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bad window board data \"" + raw + "\" at " + block.point().format() + " — using oak slab");
            }
        } else {
            plugin.getLogger().warning("Missing window board data at " + block.point().format() + " — using oak slab");
        }
        return Material.OAK_SLAB.createBlockData();
    }

    private void setBlock(BlockPoint point, BlockData block) {
        Location at = shifted(point, 0);
        FoliaManager.runAtLocation(at, () -> at.getBlock().setBlockData(block, false));
    }

    private ArenaDoor nearestDoor(Location at) {
        ArenaDoor found = null;
        double best = 16;
        for (ArenaDoor door : data.doors().values()) {
            if (!doorBuyable(door)) {
                continue;
            }
            for (FinePoint point : List.of(door.hologramA(), door.hologramB())) {
                if (point == null) {
                    continue;
                }
                double distance = at.distanceSquared(hologram(point));
                if (distance < best) {
                    best = distance;
                    found = door;
                }
            }
        }
        return found;
    }

    private UltimateMachine nearestMachine(Location at) {
        UltimateMachine found = null;
        double best = 16;
        for (UltimateMachine machine : data.machines().values()) {
            if (machine.hologram() == null) {
                continue;
            }
            double distance = at.distanceSquared(hologram(machine.hologram()));
            if (distance < best) {
                best = distance;
                found = machine;
            }
        }
        return found;
    }

    private boolean machineAtBlock(Location at) {
        if (at == null) {
            return false;
        }
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        for (UltimateMachine machine : data.machines().values()) {
            for (BlockPoint block : machine.blocks()) {
                Location shifted = shifted(block, 0);
                if (shifted.getBlockX() == x && shifted.getBlockY() == y && shifted.getBlockZ() == z) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean luckyAtBlock(Location at) {
        if (at == null) {
            return false;
        }
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        for (FinePoint point : data.spots(SpotKind.LUCKY_CHESTS)) {
            Location shifted = hologram(point);
            if (shifted.getBlockX() == x && shifted.getBlockY() == y && shifted.getBlockZ() == z) {
                return true;
            }
        }
        return false;
    }

    private void upgradeGun(Player player) {
        if (!power) {
            plugin.getMessageManager().send(player, "shop.power-off");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        dev.rajce.ketchupZombies.weapons.WeaponId id = plugin.getWeaponItems().id(hand);
        if (id == null || id.meleeSlot() && id != dev.rajce.ketchupZombies.weapons.WeaponId.PUNCHER) {
            plugin.getMessageManager().send(player, "game.upgrade-hold");
            return;
        }
        if (id == dev.rajce.ketchupZombies.weapons.WeaponId.KNIFE) {
            plugin.getMessageManager().send(player, "game.upgrade-hold");
            return;
        }
        int level = plugin.getWeaponItems().level(hand);
        int max = id == dev.rajce.ketchupZombies.weapons.WeaponId.DIGGER ? 5 : 1;
        if (level >= max) {
            plugin.getMessageManager().send(player, "game.upgrade-max");
            return;
        }
        int price = config.upgrade.price;
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        int next = level + 1;
        ItemStack created = plugin.getWeaponItems().create(id, dev.rajce.ketchupZombies.weapons.WeaponCatalog.base(config, id), next);
        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), created);
        plugin.getWeaponCombat().bindAmmo(player, created);
        plugin.getWeaponCombat().hud(player);
        plugin.getWeaponCombat().held(player);
        gold(player);
        SoundKit.play(player, player.getLocation(), config.sounds.shopBuy);
        plugin.getMessageManager().send(player, "game.upgraded", Map.of(
                "item", dev.rajce.ketchupZombies.weapons.WeaponCatalog.displayName(config, id, next),
                "price", Integer.toString(price)
        ));
    }

    /** Right/left click on door blocks (not only holograms). */
    private ArenaDoor doorAtBlock(Location at) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        for (ArenaDoor door : data.doors().values()) {
            if (!doorBuyable(door)) {
                continue;
            }
            for (BlockPoint block : door.blocks()) {
                Location world = shifted(block, 0);
                if (world.getBlockX() == x && world.getBlockY() == y && world.getBlockZ() == z) {
                    return door;
                }
            }
        }
        return null;
    }

    private boolean doorBuyable(ArenaDoor door) {
        if (openedDoors.contains(door.key())) {
            return false;
        }
        return !(open.contains(door.from()) && open.contains(door.to()));
    }

    private SpotKind nearestSpot(Location at) {
        SpotKind found = null;
        double best = 16;
        for (SpotKind kind : SpotKind.values()) {
            for (FinePoint point : data.spots(kind)) {
                double distance = at.distanceSquared(hologram(point));
                if (distance < best) {
                    best = distance;
                    found = kind;
                }
            }
        }
        return found;
    }

    private FinePoint nearestPoint(List<FinePoint> points, Location at) {
        FinePoint found = null;
        double best = 16;
        for (FinePoint point : points) {
            double distance = at.distanceSquared(hologram(point));
            if (distance < best) {
                best = distance;
                found = point;
            }
        }
        return found;
    }

    private Location hologram(FinePoint point) {
        return shifted(point);
    }

    private Location shifted(FinePoint point) {
        Location at = plugin.getHolograms().locate(world, arena, point, index);
        if (at != null) {
            return at;
        }
        return new Location(world, point.x() + index * config.copies.offsetX, point.y(), point.z() + index * config.copies.offsetZ, point.yaw(), point.pitch());
    }

    private Location shifted(BlockPoint point, double extraY) {
        return new Location(world, point.x() + index * config.copies.offsetX + 0.5, point.y() + extraY, point.z() + index * config.copies.offsetZ + 0.5);
    }

    private Player nearestPlayer(Location at) {
        Player found = null;
        double best = Double.MAX_VALUE;
        for (UUID id : players) {
            if (downed.contains(id) || dead.contains(id)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            Location there = positions.get(id);
            if (player == null || there == null) {
                continue;
            }
            double distance = there.distanceSquared(at);
            if (distance < best) {
                best = distance;
                found = player;
            }
        }
        return found;
    }

    private Player host() {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    private int standing() {
        int count = 0;
        for (UUID id : players) {
            if (!downed.contains(id) && !dead.contains(id)) {
                count++;
            }
        }
        return count;
    }

    /** End the match when nobody is left alive (all downed or dead). */
    private void checkWipe() {
        if (!ended && !players.isEmpty() && standing() == 0) {
            finish(false);
        }
    }

    private void paintAll() {
        if (ended) {
            return;
        }
        // Build mate list once so every viewer sees the same REVIVE/DEAD snapshot.
        List<BoardLayout.Mate> mates = mateSnapshot();
        int left = zombiesLeft();
        String time = formatTime(startedAt == 0L ? 0L : System.currentTimeMillis() - startedAt);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<BoardLayout.Mate> viewMates = mates;
            int killsNow = kills.getOrDefault(id, 0);
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || ended || !players.contains(player.getUniqueId())) {
                    return;
                }
                paint(player, viewMates, left, killsNow, time);
            }, null);
        }
    }

    private List<BoardLayout.Mate> mateSnapshot() {
        List<BoardLayout.Mate> mates = new ArrayList<>();
        List<UUID> order = roster.isEmpty() ? List.copyOf(players) : roster;
        for (UUID id : order) {
            Player mate = Bukkit.getPlayer(id);
            String name = mate != null ? mate.getName() : rosterNames.getOrDefault(id, id.toString().substring(0, 8));
            BoardLayout.MateStatus status;
            int gold = plugin.getProfiles().gold(id);
            if (quit.contains(id) || mate == null || !mate.isOnline()) {
                status = BoardLayout.MateStatus.QUIT;
                gold = 0;
            } else if (dead.contains(id)) {
                status = BoardLayout.MateStatus.DEAD;
            } else if (downed.contains(id)) {
                status = BoardLayout.MateStatus.REVIVE;
            } else {
                status = BoardLayout.MateStatus.ALIVE;
            }
            mates.add(new BoardLayout.Mate(name, gold, status));
        }
        return mates;
    }

    private void paint(Player player) {
        if (!player.isOnline() || ended || !players.contains(player.getUniqueId())) {
            return;
        }
        paint(player, mateSnapshot(), zombiesLeft(), kills.getOrDefault(player.getUniqueId(), 0),
                formatTime(startedAt == 0L ? 0L : System.currentTimeMillis() - startedAt));
    }

    private void paint(Player player, List<BoardLayout.Mate> mates, int zombies, int playerKills, String time) {
        if (!player.isOnline()) {
            return;
        }
        attachMatchBoard(player);
        Sidebar sidebar = boards.get(player.getUniqueId());
        if (sidebar == null) {
            return;
        }
        // Re-attach after SPECTATOR / teleport — megavex can stop updating otherwise (freeze).
        try {
            sidebar.addPlayer(player);
        } catch (RuntimeException ignored) {
        }
        BoardLayout.paint(sidebar, config.scoreboard, new BoardLayout.View(
                round,
                zombies,
                playerKills,
                time,
                areaName(player),
                mates
        ));
    }

    /** Hub/lobby off, match sidebar on. Called once at loadout and before every paint. */
    private void attachMatchBoard(Player player) {
        plugin.clearBoardPreview(player);
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().hide(player);
        }
        if (plugin.getLobbyBoard() != null) {
            plugin.getLobbyBoard().hide(player);
        }
        boards.computeIfAbsent(player.getUniqueId(), id -> {
            Sidebar created = plugin.getScoreboards().createSidebar();
            created.addPlayer(player);
            return created;
        });
    }

    private String areaName(Player player) {
        Location at = positions.getOrDefault(player.getUniqueId(), player.getLocation());
        for (Map.Entry<String, ArenaSection> entry : data.sections().entrySet()) {
            if (inside(entry.getValue(), at)) {
                return entry.getKey();
            }
        }
        return "-";
    }

    private static String formatTime(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long minutes = total / 60L;
        long seconds = total % 60L;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private void dropBoard(UUID id) {
        Sidebar sidebar = boards.remove(id);
        if (sidebar == null) {
            return;
        }
        synchronized (sidebar) {
            sidebar.close();
        }
    }

    private void tell(String key) {
        tell(key, Map.of());
    }

    /** Sends every line of a messages.yml list (or a single string fallback). */
    private void tell(String key, Map<String, String> values) {
        List<Component> lines = plugin.getMessageManager().getList(key, values);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            for (Component line : lines) {
                player.sendMessage(line);
            }
        }
    }

    private static void attribute(LivingEntity entity, Attribute attribute, double value) {
        if (entity.getAttribute(attribute) != null) {
            entity.getAttribute(attribute).setBaseValue(value);
        }
    }

    private static ItemStack leather(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.BLACK);
        item.setItemMeta(meta);
        return item;
    }

    public record Saved(ItemStack[] contents, ItemStack[] armor, Location back, GameMode mode) {
    }
}
