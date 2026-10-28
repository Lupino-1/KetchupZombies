package dev.rajce.ketchupZombies.managers;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.Blueprint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.shop.ShopMenu;
import dev.rajce.ketchupZombies.weapons.WeaponCatalog;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramManager {

    private final KetchupZombies plugin;
    private final ArenaFiles arenas;
    private final InstanceFiles instances;
    private final BlueprintManager blueprints;
    private final NamespacedKey key;
    /** Blueprint min corner + size, so a spot saved on a copy is shifted back before paste offsets apply. */
    private final Map<String, Footprint> boxes = new ConcurrentHashMap<>();

    public HologramManager(KetchupZombies plugin, ArenaFiles arenas, InstanceFiles instances, BlueprintManager blueprints) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.instances = instances;
        this.blueprints = blueprints;
        key = new NamespacedKey(plugin, "hologram");
    }

    /** Cache blueprint bounds from an already loaded blueprint (no disk). */
    public void remember(String arena, Blueprint blueprint) {
        if (arena == null || blueprint == null || blueprint.getCorner1() == null || blueprint.getCorner2() == null) {
            return;
        }
        int minX = Math.min(blueprint.getCorner1().getBlockX(), blueprint.getCorner2().getBlockX());
        int minZ = Math.min(blueprint.getCorner1().getBlockZ(), blueprint.getCorner2().getBlockZ());
        int sizeX = blueprint.getSizeX() > 0
                ? blueprint.getSizeX()
                : Math.abs(blueprint.getCorner1().getBlockX() - blueprint.getCorner2().getBlockX()) + 1;
        int sizeZ = blueprint.getSizeZ() > 0
                ? blueprint.getSizeZ()
                : Math.abs(blueprint.getCorner1().getBlockZ() - blueprint.getCorner2().getBlockZ()) + 1;
        boxes.put(arena, new Footprint(minX, minZ, Math.max(1, sizeX), Math.max(1, sizeZ)));
    }

    public void showSaved() {
        FoliaManager.runAsync(() -> {
            for (String name : arenas.listNow()) {
                rememberFile(name);
                ArenaData data = arenas.readNow(name);
                if (data == null) {
                    continue;
                }
                for (int index : shownIndexes(name)) {
                    showArena(data, index);
                }
            }
        });
    }

    /** Paste every listed copy, including index 0, and drop holograms that were shifted twice. */
    public void showRefreshed(ArenaData data, List<Integer> indexes) {
        if (data == null) {
            return;
        }
        Set<Integer> shown = new HashSet<>();
        shown.add(0);
        if (indexes != null) {
            shown.addAll(indexes);
        }
        for (int index : shown) {
            showArena(data, index);
        }
    }

    public void showArena(ArenaData data, int index) {
        for (SpotKind kind : SpotKind.values()) {
            for (FinePoint point : data.spots(kind)) {
                showSpot(data, index, kind, point);
            }
        }
        for (ArenaDoor door : data.doors().values()) {
            // A sits in from → leads to to; B sits in to → leads to from.
            showDoor(data, index, door.price(), door.hologramA(), door.to());
            showDoor(data, index, door.price(), door.hologramB(), door.from());
        }
        for (dev.rajce.ketchupZombies.models.UltimateMachine machine : data.machines().values()) {
            showMachine(data, index, machine);
        }
    }

    public void clearKnown(ArenaData data) {
        FoliaManager.runAsync(() -> {
            clearArena(data, 0);
            for (int index : instances.indexesNow(data.name())) {
                clearArena(data, index);
            }
        });
    }

    public void clearArena(ArenaData data, int index) {
        for (SpotKind kind : SpotKind.values()) {
            for (FinePoint point : data.spots(kind)) {
                clear(data.name(), index, point, kind == SpotKind.LUCKY_CHESTS);
            }
        }
        for (ArenaDoor door : data.doors().values()) {
            clear(data.name(), index, door.hologramA(), false);
            clear(data.name(), index, door.hologramB(), false);
        }
        for (dev.rajce.ketchupZombies.models.UltimateMachine machine : data.machines().values()) {
            clear(data.name(), index, machine.hologram(), false);
        }
    }

    /** Prefer the in-memory editor {@link ArenaData} — do not re-read disk (save is async). */
    public void showSpotAll(ArenaData data, SpotKind kind, FinePoint point) {
        if (data == null || point == null) {
            return;
        }
        FoliaManager.runAsync(() -> {
            rememberFile(data.name());
            showSpot(data, 0, kind, point);
            for (int index : instances.indexesNow(data.name())) {
                showSpot(data, index, kind, point);
            }
        });
    }

    public void showSpotAll(String arena, SpotKind kind, FinePoint point) {
        FoliaManager.runAsync(() -> {
            rememberFile(arena);
            ArenaData data = arenas.readNow(arena);
            if (data == null) {
                return;
            }
            showSpot(data, 0, kind, point);
            for (int index : instances.indexesNow(data.name())) {
                showSpot(data, index, kind, point);
            }
        });
    }

    /** Prefer the in-memory editor {@link ArenaData} — do not re-read disk (save is async). */
    public void showDoorAll(ArenaData data, Integer price, FinePoint point, String section) {
        if (data == null || point == null) {
            return;
        }
        FoliaManager.runAsync(() -> {
            rememberFile(data.name());
            showDoor(data, 0, price, point, section);
            for (int index : instances.indexesNow(data.name())) {
                showDoor(data, index, price, point, section);
            }
        });
    }

    public void showDoorAll(String arena, Integer price, FinePoint point, String section) {
        FoliaManager.runAsync(() -> {
            rememberFile(arena);
            ArenaData data = arenas.readNow(arena);
            if (data == null) {
                return;
            }
            showDoor(data, 0, price, point, section);
            for (int index : instances.indexesNow(data.name())) {
                showDoor(data, index, price, point, section);
            }
        });
    }

    public void clearAll(String arena, FinePoint point) {
        clearAll(arena, point, false);
    }

    public void clearAll(String arena, FinePoint point, boolean luckyChest) {
        FoliaManager.runAsync(() -> {
            clear(arena, 0, point, luckyChest);
            for (int index : instances.indexesNow(arena)) {
                clear(arena, index, point, luckyChest);
            }
        });
    }

    public void showSpot(ArenaData data, int index, SpotKind kind, FinePoint point) {
        if (kind == SpotKind.LUCKY_CHESTS) {
            showLuckyChest(data, index, point);
            return;
        }
        SpotVisual visual = visual(data, kind, point);
        show(data.name(), index, point, visual.text(), visual.item(), visual.armor());
    }

    /**
     * @param section destination section name for {@code %section%} (the side this hologram leads into)
     */
    public void showDoor(ArenaData data, int index, Integer price, FinePoint point, String section) {
        String dest = section == null ? "" : section;
        Map<String, String> values = price == null
                ? Map.of("section", dest)
                : Map.of("price", Integer.toString(price), "section", dest);
        Component text = price == null ? line("door", values) : line("door-price", values);
        show(data.name(), index, point, text, null, null);
    }

    public void showMachine(ArenaData data, int index, dev.rajce.ketchupZombies.models.UltimateMachine machine) {
        if (machine == null || machine.hologram() == null) {
            return;
        }
        Component text = line("upgraders", Map.of("price", Integer.toString(plugin.config().upgrade.price)));
        show(data.name(), index, machine.hologram(), text, null, null);
    }

    public void showMachineAll(ArenaData data, dev.rajce.ketchupZombies.models.UltimateMachine machine) {
        if (data == null || machine == null) {
            return;
        }
        showMachine(data, 0, machine);
        FoliaManager.runAsync(() -> {
            for (int index : instances.indexesNow(data.name())) {
                showMachine(data, index, machine);
            }
        });
    }

    public void showPower(ArenaData data, int index, boolean bought) {
        if (data == null) {
            return;
        }
        String key = bought ? "power-bought" : "power-buy";
        Component text = line(key, Map.of("price", Integer.toString(plugin.config().power.price)));
        ItemStack icon = new ItemStack(Material.BLAZE_POWDER);
        for (FinePoint point : data.spots(SpotKind.POWER)) {
            show(data.name(), index, point, text, icon, null);
        }
    }

    /** Item floating just above a lucky chest. Same transform as a wall gun, slightly smaller, no price text. */
    public ItemDisplay rollItem(Location chest, ItemStack item) {
        Location at = chest.clone();
        at.setX(Math.floor(chest.getX()) + 0.5);
        at.setY(Math.floor(chest.getY()) + 1.15);
        at.setZ(Math.floor(chest.getZ()) + 0.5);
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setPersistent(false);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.4f, 0.4f, 0.4f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
        });
    }

    public String tag(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** First tagged hologram whose hitbox the crosshair actually meets. */
    public Entity looking(Player player, double range) {
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null) {
            return null;
        }
        var hit = eye.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.0,
                entity -> entity != player && tag(entity) != null);
        return hit == null ? null : hit.getHitEntity();
    }

    public void clear(String arena, int index, FinePoint point) {
        clear(arena, index, point, false);
    }

    public void clear(String arena, int index, FinePoint point, boolean luckyChest) {
        FinePoint shifted = placed(arena, point, index);
        if (shifted == null) {
            return;
        }
        at(shifted, location -> {
            removeAt(location, arena, null);
            if (luckyChest) {
                removeAt(luckyHologram(location), arena, null);
                if (location.getBlock().getType() == Material.CHEST || location.getBlock().getType() == Material.TRAPPED_CHEST) {
                    location.getBlock().setType(Material.AIR, false);
                }
            }
        });
    }

    private void showLuckyChest(ArenaData data, int index, FinePoint point) {
        FinePoint shifted = placed(data.name(), point, index);
        if (shifted == null) {
            return;
        }
        purgeOrphan(data.name(), index, point);
        Component text = line("lucky-chests", Map.of("price", Integer.toString(plugin.config().luckyChest.price)));
        String tag = tag(data.name(), index);
        at(shifted, location -> {
            removeAt(location, data.name(), null);
            removeAt(luckyHologram(location), data.name(), null);
            location.getBlock().setType(Material.CHEST, false);
            Location textAt = luckyHologram(location);
            textAt.setYaw(shifted.yaw());
            textAt.setPitch(shifted.pitch());
            TextDisplay display = textAt.getWorld().spawn(textAt, TextDisplay.class, created -> {
                created.text(text);
                created.setSeeThrough(false);
                created.setBillboard(Display.Billboard.VERTICAL);
                created.setAlignment(TextDisplay.TextAlignment.CENTER);
                created.setPersistent(true);
                created.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
                pin(created);
            });
            settle(display, textAt);
        });
    }

    private Location luckyHologram(Location chest) {
        double offset = plugin.config().luckyChest.hologramOffsetY;
        return new Location(chest.getWorld(), Math.floor(chest.getX()) + 0.5, Math.floor(chest.getY()) + offset, Math.floor(chest.getZ()) + 0.5);
    }

    /** Swap the lucky-chest caption. Call on the chest region. */
    public void luckyCaption(Location chest, net.kyori.adventure.text.Component text) {
        if (chest == null || chest.getWorld() == null || text == null) {
            return;
        }
        Location at = luckyHologram(chest);
        for (org.bukkit.entity.Entity entity : chest.getWorld().getNearbyEntities(at, 0.4, 0.4, 0.4)) {
            if (entity instanceof TextDisplay display && display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                display.text(text);
            }
        }
    }

    public void restoreLucky(Location chest) {
        luckyCaption(chest, line("lucky-chests", Map.of("price", Integer.toString(plugin.config().luckyChest.price))));
    }

    private void show(String arena, int index, FinePoint point, Component text, ItemStack preview, List<ItemStack> armor) {
        FinePoint shifted = placed(arena, point, index);
        if (shifted == null || text == null) {
            return;
        }
        purgeOrphan(arena, index, point);
        String tag = tag(arena, index);
        at(shifted, location -> {
            removeAt(location, arena, null);
            location.setYaw(shifted.yaw());
            location.setPitch(shifted.pitch());
            Location textAt = location.clone().add(0, armor != null && !armor.isEmpty() ? 1.35 : 0.35, 0);
            TextDisplay textDisplay = textAt.getWorld().spawn(textAt, TextDisplay.class, display -> {
                display.text(text);
                display.setSeeThrough(false);
                face(display, shifted);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setPersistent(true);
                display.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
                pin(display);
            });
            settle(textDisplay, textAt);
            if (armor != null && !armor.isEmpty()) {
                Location standAt = location.clone().add(0, -0.9, 0);
                standAt.setYaw(shifted.yaw());
                standAt.setPitch(0);
                ArmorStand stand = standAt.getWorld().spawn(standAt, ArmorStand.class, created -> {
                    created.setInvisible(true);
                    created.setMarker(true);
                    created.setGravity(false);
                    created.setBasePlate(false);
                    created.setArms(false);
                    created.setPersistent(true);
                    created.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
                    EntityEquipment equipment = created.getEquipment();
                    if (equipment != null) {
                        for (ItemStack piece : armor) {
                            equipArmor(equipment, piece);
                        }
                    }
                });
                settle(stand, standAt);
            } else if (preview != null && !preview.getType().isAir()) {
                Location itemAt = location.clone().add(0, -0.15, 0);
                ItemDisplay itemDisplay = itemAt.getWorld().spawn(itemAt, ItemDisplay.class, display -> {
                    display.setItemStack(preview);
                    face(display, shifted);
                    display.setPersistent(true);
                    display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(0.55f, 0.55f, 0.55f),
                            new AxisAngle4f(0, 0, 1, 0)
                    ));
                    display.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
                    pin(display);
                });
                settle(itemDisplay, itemAt);
            }
            Location box = location.clone().subtract(0, 1.0, 0);
            Interaction hitbox = box.getWorld().spawn(box, Interaction.class, created -> {
                created.setInteractionWidth(1.15f);
                created.setInteractionHeight(2.5f);
                created.setResponsive(true);
                created.setPersistent(true);
                created.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
            });
            settle(hitbox, box);
        });
    }

    private static void face(Display display, FinePoint point) {
        if (point.billboardFixed()) {
            display.setBillboard(Display.Billboard.FIXED);
            display.setRotation(point.yaw(), point.pitch());
        } else {
            display.setBillboard(Display.Billboard.CENTER);
        }
    }

    private static void equipArmor(EntityEquipment equipment, ItemStack piece) {
        String name = piece.getType().name();
        if (name.endsWith("_HELMET")) {
            equipment.setHelmet(piece);
        } else if (name.endsWith("_CHESTPLATE")) {
            equipment.setChestplate(piece);
        } else if (name.endsWith("_LEGGINGS")) {
            equipment.setLeggings(piece);
        } else if (name.endsWith("_BOOTS")) {
            equipment.setBoots(piece);
        }
    }

    private SpotVisual visual(ArenaData data, SpotKind kind, FinePoint point) {
        return switch (kind) {
            case WEAPONS -> weaponVisual(data, point);
            case ARMOR -> armorVisual(data, point);
            case LUCKY_CHESTS -> new SpotVisual(
                    line("lucky-chests", Map.of("price", Integer.toString(plugin.config().luckyChest.price))),
                    null,
                    null
            );
            case UPGRADERS -> new SpotVisual(
                    line("upgraders", Map.of("price", Integer.toString(plugin.config().upgrade.price))),
                    null,
                    null
            );
            case PERKS -> new SpotVisual(
                    line("perks"),
                    new ItemStack(Material.EXPERIENCE_BOTTLE),
                    null
            );
            case POWER -> new SpotVisual(
                    line("power-buy", Map.of("price", Integer.toString(plugin.config().power.price))),
                    new ItemStack(Material.BLAZE_POWDER),
                    null
            );
        };
    }

    private SpotVisual weaponVisual(ArenaData data, FinePoint point) {
        List<WeaponId> stock = data.customWeapons(point)
                ? data.weaponOffers(point)
                : plugin.getShopMenu().defaultWeapons();
        if (stock.isEmpty()) {
            return new SpotVisual(line("weapons"), new ItemStack(Material.WOODEN_HOE), null);
        }
        WeaponId weapon = stock.get(0);
        PluginConfig.Weapon stats = WeaponCatalog.base(plugin, weapon);
        String label = WeaponCatalog.displayName(plugin, weapon, 0);
        Component text = stock.size() == 1
                ? weaponLines(label, Math.max(0, stats.price), stats.magazine >= 0 ? plugin.config().shops.ammoRefill : -1)
                : line("weapons");
        return new SpotVisual(text, WeaponCatalog.stack(plugin, weapon, 0), null);
    }

    private SpotVisual armorVisual(ArenaData data, FinePoint point) {
        List<ArmorOffer> offers = data.customArmor(point)
                ? data.armorOffers(point)
                : plugin.getShopMenu().defaults();
        if (offers.isEmpty() || offers.get(0).empty()) {
            return new SpotVisual(line("armor"), new ItemStack(Material.LEATHER_CHESTPLATE), null);
        }
        ArmorOffer offer = offers.get(0);
        List<ItemStack> pieces = ShopMenu.pieces(offer);
        ItemStack preview = pieces.isEmpty() ? new ItemStack(Material.LEATHER_CHESTPLATE) : pieces.get(0).clone();
        Component text = line("armor-buy", Map.of("item", offer.label(), "price", Integer.toString(offer.price())));
        return new SpotVisual(text, preview, pieces.isEmpty() ? null : pieces);
    }

    private Component weaponLines(String item, int price, int refill) {
        Component text = line("weapon-buy", Map.of("item", item, "price", Integer.toString(price)));
        if (refill >= 0) {
            text = text.append(Component.newline())
                    .append(line("weapon-refill", Map.of("refill", Integer.toString(refill))));
        }
        return text;
    }

    /** Lines from {@code config.yml} {@code holograms.<key>}. */
    private Component line(String key) {
        return line(key, Map.of());
    }

    private Component line(String key, Map<String, ?> placeholders) {
        java.util.List<String> lines = plugin.config().holograms.lines(key);
        String raw = String.join("\n", lines == null ? java.util.List.of() : lines);
        if (raw.isBlank()) {
            return Component.empty();
        }
        return plugin.getMessageManager().parse(raw, null, placeholders);
    }

    /**
     * Drop holograms in this column. {@code exact} is one copy tag; null clears every copy of the arena.
     * Marker armor stands have no hitbox, so a nearby-entity search misses them and the next paste stacks another set.
     * A spot saved on a copy also left the old index-0 entities under the instance hologram.
     */
    private void removeAt(Location location, String arena, String exact) {
        World world = location.getWorld();
        if (world == null || arena == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ);
        }
        String prefix = arena + ":";
        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            String tagged = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (tagged == null) {
                continue;
            }
            if (exact != null) {
                if (!exact.equals(tagged)) {
                    continue;
                }
            } else if (!tagged.startsWith(prefix)) {
                continue;
            }
            Location at = entity.getLocation();
            if (Math.abs(at.getX() - location.getX()) > 0.85 || Math.abs(at.getZ() - location.getZ()) > 0.85) {
                continue;
            }
            if (Math.abs(at.getY() - location.getY()) > 3.2) {
                continue;
            }
            entity.remove();
        }
    }

    private void at(FinePoint point, java.util.function.Consumer<Location> action) {
        World world = plugin.getServer().getWorld(point.world());
        if (world == null) {
            return;
        }
        Location location = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        FoliaManager.runAtLocation(location, () -> action.accept(location));
    }

    /** Stop display entities interpolating in from the origin on a freshly pasted chunk. */
    private static void pin(Display display) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
    }

    private void settle(Entity entity, Location at) {
        if (entity == null || at == null) {
            return;
        }
        Location locked = at.clone();
        entity.getScheduler().runDelayed(plugin, task -> {
            if (!entity.isValid()) {
                return;
            }
            if (entity instanceof Display display) {
                pin(display);
            }
            entity.teleportAsync(locked);
        }, null, 1L);
    }

    private void rememberFile(String arena) {
        if (boxes.containsKey(arena)) {
            return;
        }
        int[] raw = blueprints.footprintNow(arena);
        if (raw == null) {
            return;
        }
        boxes.put(arena, new Footprint(raw[0], raw[1], raw[2], raw[3]));
    }

    private Set<Integer> shownIndexes(String arena) {
        Set<Integer> shown = new HashSet<>();
        shown.add(0);
        shown.addAll(instances.indexesNow(arena));
        return shown;
    }

    /** Same point the hologram uses. Spots saved on a copy are pulled back before the offset. */
    public Location locate(World world, String arena, FinePoint point, int index) {
        FinePoint placed = placed(arena, point, index);
        if (world == null || placed == null) {
            return null;
        }
        return new Location(world, placed.x(), placed.y(), placed.z(), placed.yaw(), placed.pitch());
    }

    /** Blueprint-space point, then copy offset. A spot saved on a copy is pulled back first. */
    private FinePoint placed(String arena, FinePoint point, int index) {
        FinePoint base = anchor(point, boxes.get(arena));
        return rawShift(base, index);
    }

    private FinePoint rawShift(FinePoint point, int index) {
        if (point == null) {
            return null;
        }
        if (index == 0) {
            return point;
        }
        int offsetX = plugin.config().copies.offsetX;
        int offsetZ = plugin.config().copies.offsetZ;
        return point.shifted(index * (double) offsetX, 0, index * (double) offsetZ);
    }

    /**
     * Old refresh treated copy-space coords as blueprint coords and added the offset again.
     * That leftover uses this copy's tag only, so the real instance hologram (other index) stays.
     */
    private void purgeOrphan(String arena, int index, FinePoint point) {
        FinePoint legacy = rawShift(point, index);
        FinePoint correct = placed(arena, point, index);
        if (legacy == null || correct == null || near(legacy, correct)) {
            return;
        }
        String exact = tag(arena, index);
        at(legacy, location -> removeAt(location, arena, exact));
    }

    /** If the point sits inside copy N, subtract N offsets so index 0 is the blueprint. */
    private FinePoint anchor(FinePoint point, Footprint box) {
        if (point == null || box == null) {
            return point;
        }
        int offsetX = plugin.config().copies.offsetX;
        int offsetZ = plugin.config().copies.offsetZ;
        if (offsetX == 0 || offsetZ == 0) {
            return point;
        }
        double dx = point.x() - box.minX();
        double dz = point.z() - box.minZ();
        int copyX = (int) Math.floor(dx / offsetX);
        int copyZ = (int) Math.floor(dz / offsetZ);
        if (copyX != copyZ || copyX <= 0) {
            return point;
        }
        double localX = dx - copyX * (double) offsetX;
        double localZ = dz - copyZ * (double) offsetZ;
        if (localX < -4 || localZ < -4 || localX > box.sizeX() + 4 || localZ > box.sizeZ() + 4) {
            return point;
        }
        return point.shifted(-copyX * (double) offsetX, 0, -copyZ * (double) offsetZ);
    }

    private static boolean near(FinePoint a, FinePoint b) {
        return Math.abs(a.x() - b.x()) < 0.05
                && Math.abs(a.y() - b.y()) < 0.05
                && Math.abs(a.z() - b.z()) < 0.05;
    }

    private record Footprint(int minX, int minZ, int sizeX, int sizeZ) {
    }

    private static String tag(String arena, int index) {
        return arena + ":" + index;
    }

    private record SpotVisual(Component text, ItemStack item, List<ItemStack> armor) {
    }
}
