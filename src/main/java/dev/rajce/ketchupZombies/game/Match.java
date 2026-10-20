package dev.rajce.ketchupZombies.game;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.WindowBlock;
import dev.rajce.ketchupZombies.shop.Profiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Match {

    private final KetchupZombies plugin;
    private final RoundBook book;
    private final NamespacedKey mobKey;
    private final String id;
    private final String arena;
    private final int index;
    private final ArenaData data;
    private final World world;
    private final String startSection;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> downed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> cleaned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();
    private final Map<UUID, Sidebar> boards = new ConcurrentHashMap<>();
    private final Map<UUID, Location> positions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> revive = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> windowProgress = new ConcurrentHashMap<>();
    private final Map<Integer, Long> windowNextBreak = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> windowSpawns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final List<LivingEntity> mobs = new CopyOnWriteArrayList<>();
    private final List<RoundBook.Wave> waves = new ArrayList<>();
    private final List<RoundBook.Mob> queue = new ArrayList<>();
    private final Set<String> open = new HashSet<>();
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

    public Match(KetchupZombies plugin, RoundBook book, NamespacedKey mobKey, String arena, int index, ArenaData data, World world) {
        this.plugin = plugin;
        this.book = book;
        this.mobKey = mobKey;
        this.id = UUID.randomUUID().toString();
        this.arena = arena;
        this.index = index;
        this.data = data;
        this.world = world;
        startSection = data.sections().keySet().iterator().next();
        open.add(startSection);
    }

    public boolean contains(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean downed(Player player) {
        return downed.contains(player.getUniqueId());
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
        List<Player> online = new ArrayList<>();
        for (Player player : group) {
            if (player != null && player.isOnline() && players.add(player.getUniqueId())) {
                online.add(player);
            }
        }
        if (online.isEmpty()) {
            plugin.getCopies().reset(arena, index);
            return;
        }
        ArenaSection section = data.sections().get(startSection);
        int offsetX = plugin.config().copies.offsetX * index;
        int offsetZ = plugin.config().copies.offsetZ * index;
        Location rough = new Location(world, (section.x1() + section.x2()) / 2.0 + offsetX + 0.5, section.y1() + 1, (section.z1() + section.z2()) / 2.0 + offsetZ + 0.5);
        for (Player player : online) {
            player.getScheduler().run(plugin, task -> {
                if (!players.contains(player.getUniqueId()) || ended) {
                    return;
                }
                saved.put(player.getUniqueId(), new Saved(player.getInventory().getContents().clone(), player.getInventory().getArmorContents().clone(), player.getLocation().clone(), player.getGameMode()));
                place(player, rough);
            }, null);
        }
    }

    public void gold(Player player) {
        paint(player);
    }

    public void click(Player player, Location at) {
        if (ended || !players.contains(player.getUniqueId()) || downed.contains(player.getUniqueId())) {
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
                    : null;
            plugin.getShopMenu().weapons(player, stock);
            return;
        }
        if (kind == SpotKind.ARMOR) {
            FinePoint point = nearestPoint(data.spots(SpotKind.ARMOR), at);
            List<ArmorOffer> offers = point != null && data.customArmor(point) ? data.armorOffers(point) : plugin.getShopMenu().defaults();
            plugin.getShopMenu().armor(player, offers);
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
        if (!downed.add(player.getUniqueId())) {
            return;
        }
        player.setHealth(1);
        player.setWalkSpeed(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60 * 10, 10, false, false));
        plugin.getMessageManager().send(player, "game.downed");
        ArenaSection section = data.sections().get(startSection);
        if (player.getLocation().getY() < section.y1()) {
            int offsetX = plugin.config().copies.offsetX * index;
            int offsetZ = plugin.config().copies.offsetZ * index;
            player.teleportAsync(new Location(world, (section.x1() + section.x2()) / 2.0 + offsetX + 0.5, section.y1() + 1, (section.z1() + section.z2()) / 2.0 + offsetZ + 0.5));
        }
        if (standing() == 0) {
            finish(false);
        }
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
        players.remove(player.getUniqueId());
        downed.remove(player.getUniqueId());
        positions.remove(player.getUniqueId());
        revive.remove(player.getUniqueId());
        plugin.getMessageManager().send(player, "game.left");
        restore(player);
        if (players.isEmpty()) {
            if (!ended) {
                ended = true;
                for (LivingEntity mob : mobs) {
                    mob.getScheduler().run(plugin, task -> mob.remove(), null);
                }
                mobs.clear();
                plugin.getGames().forget(this);
                plugin.getCopies().reset(arena, index);
            }
            return;
        }
        if (standing() == 0) {
            finish(false);
        }
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
        player.setWalkSpeed(0.2f);
        plugin.getProfiles().gold(player.getUniqueId(), 0);
        plugin.getProfiles().power(player.getUniqueId(), false);
        player.getInventory().addItem(plugin.getWeaponItems().create(dev.rajce.ketchupZombies.weapons.WeaponId.KNIFE, plugin.config().weapons.knife, 0));
        player.getInventory().addItem(plugin.getWeaponItems().create(dev.rajce.ketchupZombies.weapons.WeaponId.PISTOL, plugin.config().weapons.pistol, 0));
        paint(player);
        plugin.getWeaponCombat().hud(player);
        positions.put(player.getUniqueId(), player.getLocation().clone());
        track(player);
        boolean first;
        synchronized (this) {
            first = !running;
            running = true;
        }
        if (first) {
            tell("game.round", Map.of("round", "1"));
            fillQueue();
            loop();
        }
    }

    private void track(Player player) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline() || !players.contains(player.getUniqueId()) || ended) {
                return;
            }
            positions.put(player.getUniqueId(), player.getLocation().clone());
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
        host.getScheduler().runDelayed(plugin, task -> {
            try {
                pulse();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Match tick failed: " + ex.getMessage());
            }
            loop();
        }, null, 5L);
    }

    private synchronized void pulse() {
        if (ended) {
            return;
        }
        revive();
        crackWindows();
        boardCooldown++;
        if (boardCooldown >= 4) {
            boardCooldown = 0;
            paintAll();
        }
        if (cursor >= queue.size()) {
            maybeAdvance();
            return;
        }
        if (nextWaveAt > 0) {
            return;
        }
        spawnCooldown++;
        if (spawnCooldown < 8) {
            return;
        }
        spawnCooldown = 0;
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
            windowProgress.putIfAbsent(windowIndex, 0);
            windowNextBreak.putIfAbsent(windowIndex, 0L);
            Location spot = exit(at);
            try {
                if ("ZOMBIE_PIGMAN".equals(mob.type())) {
                    spawn(mob, spot, PigZombie.class, true);
                } else {
                    spawn(mob, spot, Zombie.class, false);
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

    private void crackWindows() {
        long now = System.currentTimeMillis();
        long wait = Math.max(500L, Math.round(plugin.config().zombies.windowBreakSeconds * 1000));
        for (Map.Entry<Integer, Integer> entry : Set.copyOf(windowProgress.entrySet())) {
            int index = entry.getKey();
            if (index < 0 || index >= data.windows().size()) {
                continue;
            }
            ArenaWindow window = data.windows().get(index);
            int progress = entry.getValue();
            if (progress >= window.blocks().size()) {
                continue;
            }
            long next = windowNextBreak.getOrDefault(index, 0L);
            if (now < next) {
                continue;
            }
            WindowBlock block = window.blocks().get(progress);
            setBlock(block.point(), Material.AIR.createBlockData());
            windowProgress.put(index, progress + 1);
            windowNextBreak.put(index, now + wait);
        }
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
        return spot;
    }

    private void spawn(RoundBook.Mob spec, Location at, Class<? extends Zombie> type, boolean pig) {
        if (ended) {
            synchronized (this) {
                pending = Math.max(0, pending - 1);
            }
            return;
        }
        LivingEntity mob = world.spawn(at, type, entity -> {
            entity.setCustomNameVisible(true);
            entity.customName(Component.text(pig ? "Pig" : "Walker"));
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(false);
            entity.setShouldBurnInDay(false);
            entity.setCollidable(false);
            if (entity instanceof Zombie zombie) {
                zombie.setAdult();
            }
            entity.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, id);
            attribute(entity, Attribute.MAX_HEALTH, spec.health());
            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.setHealth(Math.min(spec.health(), entity.getAttribute(Attribute.MAX_HEALTH).getValue()));
            }
            attribute(entity, Attribute.ATTACK_DAMAGE, spec.damage());
            attribute(entity, Attribute.ARMOR, spec.armor());
            attribute(entity, Attribute.MOVEMENT_SPEED, spec.speed());
            attribute(entity, Attribute.FOLLOW_RANGE, 64);
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
            Player target = nearestPlayer(at);
            Location there = target == null ? null : positions.get(target.getUniqueId());
            if (target != null) {
                try {
                    entity.setTarget(target);
                } catch (RuntimeException ignored) {
                    if (entity instanceof Mob walker && there != null) {
                        walker.getPathfinder().moveTo(there);
                    }
                }
            }
        });
        mobs.add(mob);
        synchronized (this) {
            alive++;
            pending = Math.max(0, pending - 1);
        }
        paintAll();
        maybeAdvance();
    }

    private void revive() {
        for (UUID id : Set.copyOf(downed)) {
            Player player = Bukkit.getPlayer(id);
            Location at = positions.get(id);
            if (player == null || at == null) {
                continue;
            }
            boolean near = false;
            boolean fast = false;
            for (UUID other : players) {
                if (other.equals(id) || downed.contains(other)) {
                    continue;
                }
                Location there = positions.get(other);
                if (there == null || there.getWorld() == null || !there.getWorld().equals(at.getWorld()) || there.distanceSquared(at) > 6) {
                    continue;
                }
                near = true;
                Player mate = Bukkit.getPlayer(other);
                if (mate != null && plugin.getProfiles().perk(mate.getUniqueId(), Profiles.FAST_REVIVE)) {
                    fast = true;
                }
            }
            int progress = near ? revive.getOrDefault(id, 0) + 5 : 0;
            if (progress >= (fast ? 5 : 30)) {
                revive.remove(id);
                player.getScheduler().run(plugin, task -> stand(player), null);
            } else {
                revive.put(id, progress);
            }
        }
    }

    private void stand(Player player) {
        downed.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.setHealth(10);
        plugin.getMessageManager().send(player, "game.revived");
    }

    private synchronized void fillQueue() {
        waves.clear();
        waves.addAll(book.waves(round));
        waveIndex = 0;
        windowProgress.clear();
        windowNextBreak.clear();
        windowSpawns.clear();
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
        restoreWindows();
        loadWave();
        paintAll();
    }

    private synchronized void loadWave() {
        queue.clear();
        cursor = 0;
        pending = 0;
        nextWaveAt = 0;
        spawnCooldown = 0;
        windowSpawns.clear();
        if (waveIndex >= waves.size()) {
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
        long delay = Math.max(0L, Math.round(plugin.config().zombies.waveClearDelaySeconds * 1000));
        if (nextWaveAt == 0) {
            nextWaveAt = System.currentTimeMillis() + delay;
            paintAll();
            return;
        }
        if (System.currentTimeMillis() < nextWaveAt) {
            return;
        }
        nextWaveAt = 0;
        waveIndex++;
        if (waveIndex < waves.size()) {
            loadWave();
            paintAll();
            return;
        }
        if (round >= 3) {
            finish(true);
            return;
        }
        round++;
        tell("game.clear");
        tell("game.round", Map.of("round", Integer.toString(round)));
        fillQueue();
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

    private void finish(boolean won) {
        if (ended) {
            return;
        }
        ended = true;
        tell(won ? "game.win" : "game.lose");
        for (LivingEntity mob : mobs) {
            mob.getScheduler().run(plugin, task -> {
                if (mob.isValid()) {
                    mob.remove();
                }
            }, null);
        }
        mobs.clear();
        for (UUID id : Set.copyOf(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
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
    }

    private void restore(Player player) {
        if (!cleaned.add(player.getUniqueId())) {
            return;
        }
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SPEED);
        plugin.getProfiles().clear(player.getUniqueId());
        org.bukkit.attribute.AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            NamespacedKey extra = new NamespacedKey(plugin, "extra-health");
            health.getModifiers().stream().filter(modifier -> extra.equals(modifier.getKey())).toList().forEach(health::removeModifier);
        }
        Saved kit = saved.remove(player.getUniqueId());
        dropBoard(player.getUniqueId());
        player.setLevel(0);
        player.setExp(0f);
        if (kit == null) {
            return;
        }
        player.getInventory().setContents(kit.contents);
        player.getInventory().setArmorContents(kit.armor);
        player.setGameMode(kit.mode);
        player.teleportAsync(kit.back);
    }

    private void buyDoor(Player player, ArenaDoor door) {
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
            plugin.getMessageManager().send(player, "game.door-open");
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), door.price())) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        open.add(fromOpen ? door.to() : door.from());
        for (BlockPoint block : door.blocks()) {
            setBlock(block, Material.AIR.createBlockData());
        }
        paint(player);
        plugin.getMessageManager().send(player, "game.door");
    }

    private void buyPower(Player player) {
        if (power) {
            plugin.getMessageManager().send(player, "game.power");
            return;
        }
        if (!plugin.getProfiles().takeGold(player.getUniqueId(), plugin.config().power.price)) {
            plugin.getMessageManager().send(player, "shop.poor");
            return;
        }
        power = true;
        for (UUID id : players) {
            plugin.getProfiles().power(id, true);
        }
        paint(player);
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
        int reach = plugin.config().zombies.spawnDistance;
        for (ArenaWindow window : data.windows()) {
            if (window.spawn() == null || !windowOpen(window)) {
                continue;
            }
            Location at = shifted(window.spawn());
            for (UUID id : players) {
                if (downed.contains(id)) {
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
        double x = at.getX() - index * plugin.config().copies.offsetX;
        double z = at.getZ() - index * plugin.config().copies.offsetZ;
        double y = at.getY();
        int minX = Math.min(section.x1(), section.x2());
        int maxX = Math.max(section.x1(), section.x2()) + 1;
        int minY = Math.min(section.y1(), section.y2());
        int maxY = Math.max(section.y1(), section.y2()) + 1;
        int minZ = Math.min(section.z1(), section.z2());
        int maxZ = Math.max(section.z1(), section.z2()) + 1;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private void restoreWindows() {
        windowProgress.clear();
        windowNextBreak.clear();
        for (ArenaWindow window : data.windows()) {
            if (!windowOpen(window)) {
                continue;
            }
            for (WindowBlock block : window.blocks()) {
                try {
                    setBlock(block.point(), Bukkit.createBlockData(block.data()));
                } catch (IllegalArgumentException ignored) {
                    setBlock(block.point(), Material.AIR.createBlockData());
                }
            }
        }
    }

    private void setBlock(BlockPoint point, BlockData block) {
        Location at = shifted(point, 0);
        FoliaManager.runAtLocation(at, () -> at.getBlock().setBlockData(block, false));
    }

    private ArenaDoor nearestDoor(Location at) {
        ArenaDoor found = null;
        double best = 16;
        for (ArenaDoor door : data.doors().values()) {
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
        return new Location(world, point.x() + index * plugin.config().copies.offsetX, point.y(), point.z() + index * plugin.config().copies.offsetZ);
    }

    private Location shifted(BlockPoint point, double extraY) {
        return new Location(world, point.x() + index * plugin.config().copies.offsetX + 0.5, point.y() + extraY, point.z() + index * plugin.config().copies.offsetZ + 0.5);
    }

    private Player nearestPlayer(Location at) {
        Player found = null;
        double best = Double.MAX_VALUE;
        for (UUID id : players) {
            if (downed.contains(id)) {
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
            if (!downed.contains(id)) {
                count++;
            }
        }
        return count;
    }

    private void paintAll() {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.getScheduler().run(plugin, task -> paint(player), null);
            }
        }
    }

    private void paint(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Sidebar sidebar = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Sidebar created = plugin.getScoreboards().createSidebar();
            created.addPlayer(player);
            return created;
        });
        int zombies = zombiesLeft();
        int myKills = kills.getOrDefault(player.getUniqueId(), 0);
        String time = formatTime(startedAt == 0L ? 0L : System.currentTimeMillis() - startedAt);
        String area = areaName(player);
        List<String> roster = new ArrayList<>();
        for (UUID id : players) {
            Player mate = Bukkit.getPlayer(id);
            String name = mate != null ? mate.getName() : id.toString().substring(0, 8);
            String status;
            if (downed.contains(id)) {
                status = "REVIVE";
            } else if (mate == null || !mate.isOnline()) {
                status = "QUIT";
            } else {
                status = Integer.toString(plugin.getProfiles().gold(id));
            }
            roster.add(name + ": " + status);
        }
        synchronized (sidebar) {
            sidebar.title(Component.text("Zombies", NamedTextColor.RED));
            int line = 0;
            sidebar.line(line++, Component.text("Round " + round, NamedTextColor.YELLOW));
            sidebar.line(line++, Component.text("Zombies Left: " + zombies, NamedTextColor.WHITE));
            sidebar.line(line++, Component.empty());
            for (String row : roster) {
                sidebar.line(line++, Component.text(row, NamedTextColor.GREEN));
            }
            sidebar.line(line++, Component.empty());
            sidebar.line(line++, Component.text("Kills: " + myKills, NamedTextColor.WHITE));
            sidebar.line(line++, Component.text("Time: " + time, NamedTextColor.WHITE));
            sidebar.line(line++, Component.text("Area: " + area, NamedTextColor.AQUA));
            while (line < 15) {
                sidebar.line(line++, Component.empty());
            }
        }
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

    private void tell(String key, Map<String, String> values) {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.getMessageManager().send(player, key, values);
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

    private record Saved(ItemStack[] contents, ItemStack[] armor, Location back, GameMode mode) {
    }
}
