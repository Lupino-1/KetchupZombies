package dev.rajce.ketchupZombies.editor;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.managers.ArenaFiles;
import dev.rajce.ketchupZombies.managers.BlueprintManager;
import dev.rajce.ketchupZombies.managers.HologramManager;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.Blueprint;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.WindowBlock;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class Editor {

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private final ArenaFiles arenas;
    private final HologramManager holograms;
    private final ArenaMenus menus;
    private final ShopStock shopStock;
    private final ConcurrentHashMap<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> editors = new ConcurrentHashMap<>();
    private final java.util.Set<String> creating = ConcurrentHashMap.newKeySet();

    public Editor(KetchupZombies plugin, BlueprintManager blueprints, ArenaFiles arenas, HologramManager holograms) {
        this.plugin = plugin;
        this.blueprints = blueprints;
        this.arenas = arenas;
        this.holograms = holograms;
        this.menus = new ArenaMenus(this);
        this.shopStock = new ShopStock(this);
    }

    public KetchupZombies plugin() {
        return plugin;
    }

    public boolean inSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public List<String> completeArenas() {
        return new ArrayList<>(arenas.names());
    }

    public boolean capturingChat(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session != null && session.mode() != EditMode.NONE;
    }

    public boolean marking(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session != null && session.mode().marksBlock();
    }

    public boolean hologramMark(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session != null && session.mode() == EditMode.MARK && isHologram(session.markTarget());
    }

    public boolean shouldCancelBreak(Player player, Block block) {
        EditorSession session = sessions.get(player.getUniqueId());
        return session != null && (session.mode().marksBlock() || session.suppressBreak(block));
    }

    public void openCreate(Player player) {
        EditorSession existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.kind() == EditorSession.Kind.EDIT) {
            reply(player, "&cClose the arena editor first.");
            return;
        }
        EditorSession session = existing == null ? EditorSession.create(player.getUniqueId()) : existing;
        sessions.put(player.getUniqueId(), session);
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.CREATE);
        menus.create(player, session);
    }

    public void openEdit(Player player, String name) {
        if (!Names.valid(name)) {
            reply(player, "&cName must be 1-32 letters and numbers.");
            return;
        }
        EditorSession existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.kind() == EditorSession.Kind.CREATE) {
            if (existing.saving()) {
                reply(player, "&cSave already running.");
                return;
            }
            FoliaManager.runAsync(() -> {
                boolean exists = arenas.fileExists(name);
                FoliaManager.runAtEntity(player, () -> openEditAfterCreate(player, name, exists));
            });
            return;
        }
        if (existing != null && existing.kind() == EditorSession.Kind.EDIT) {
            if (!name.equals(existing.data().name())) {
                reply(player, "&cClose the arena you are editing first.");
                return;
            }
            existing.mode(EditMode.NONE);
            existing.screen(EditorSession.Screen.HUB);
            menus.hub(player, existing);
            return;
        }
        UUID holder = editors.get(name);
        if (holder != null && !holder.equals(player.getUniqueId())) {
            reply(player, "&cSomeone else is editing that arena.");
            return;
        }
        beginEdit(player, name);
    }

    private void openEditAfterCreate(Player player, String name, boolean exists) {
        if (!player.isOnline()) {
            return;
        }
        EditorSession existing = sessions.get(player.getUniqueId());
        if (existing == null || existing.kind() != EditorSession.Kind.CREATE) {
            beginEdit(player, name);
            return;
        }
        if (!exists) {
            reply(player, "&cArena " + name + " does not exist.");
            return;
        }
        UUID holder = editors.get(name);
        if (holder != null && !holder.equals(player.getUniqueId())) {
            reply(player, "&cSomeone else is editing that arena.");
            return;
        }
        sessions.remove(player.getUniqueId());
        beginEdit(player, name);
    }

    private void beginEdit(Player player, String name) {
        info(player, "editor.loading");
        arenas.load(name).whenComplete((data, error) -> FoliaManager.runAtEntity(player, () -> finishOpen(player, name, data, error)));
    }

    public void clickCreateName(Player player, MenuClick click) {
        EditorSession session = createSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            session.createName(null);
            menus.create(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armName(player, session, EditorSession.Screen.CREATE);
        }
    }

    public void clickCreateCorner(Player player, int index, MenuClick click) {
        EditorSession session = createSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            if (index == 1) {
                session.corner1(null);
            } else {
                session.corner2(null);
            }
            menus.create(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, index == 1 ? MarkTarget.CREATE_CORNER_1 : MarkTarget.CREATE_CORNER_2, EditorSession.Screen.CREATE);
        }
    }

    public void saveCreate(Player player) {
        EditorSession session = createSession(player);
        if (session == null) {
            return;
        }
        String name = session.createName();
        Location first = session.corner1();
        Location second = session.corner2();
        if (session.saving()) {
            reply(player, "&cSave already running.");
            return;
        }
        if (name == null || first == null || second == null) {
            reply(player, "&cSet the name and both corners first.");
            return;
        }
        if (!sameWorld(first, second)) {
            reply(player, "&cCorners must be in the same world.");
            return;
        }
        session.saving(true);
        player.closeInventory();
        info(player, "editor.saving", Map.of("name", name));
        FoliaManager.runAsync(() -> {
            if (!creating.add(name)) {
                finishCreateFailure(player, session, "&cThat arena is already being saved.");
                return;
            }
            File blueprint = new File(new File(plugin.getDataFolder(), "blueprints"), name + ".json");
            if (blueprint.isFile() || arenas.fileExists(name)) {
                creating.remove(name);
                finishCreateFailure(player, session, "&cAn arena with that name already exists.");
                return;
            }
            blueprints.save(name, new Blueprint(first, second))
                    .thenCompose(ignored -> arenas.createEmpty(name))
                    .whenComplete((ignored, error) -> {
                        creating.remove(name);
                        if (error != null) {
                            plugin.getLogger().warning("Arena create " + name + ": " + error.getMessage());
                            finishCreateFailure(player, session, "&cSave failed. Check console.");
                            return;
                        }
                        FoliaManager.runAtEntity(player, () -> {
                            sessions.remove(player.getUniqueId());
                            if (!player.isOnline()) {
                                return;
                            }
                            info(player, "editor.saved-create", Map.of("name", name));
                        });
                    });
        });
    }

    public void showHub(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.HUB);
        menus.hub(player, session);
    }

    public void showSections(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.mode(EditMode.NONE);
        session.clearSectionDraft();
        session.screen(EditorSession.Screen.SECTIONS);
        menus.sections(player, session);
    }

    public void beginSection(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.clearSectionDraft();
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.SECTION_FORM);
        menus.sectionForm(player, session);
    }

    public void clickSection(Player player, String name, MenuClick click) {
        if (click != MenuClick.RIGHT) {
            return;
        }
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaData data = session.data();
        data.sections().remove(name);
        data.doors().entrySet().removeIf(entry -> {
            boolean hit = name.equals(entry.getValue().from()) || name.equals(entry.getValue().to());
            if (hit) {
                clearDoor(data.name(), entry.getValue());
            }
            return hit;
        });
        data.windows().removeIf(window -> name.equals(window.section()));
        persist(session);
        info(player, "editor.removed-section", Map.of("name", name));
        menus.sections(player, session);
    }

    public void clickSectionName(Player player, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            session.sectionName(null);
            menus.sectionForm(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armName(player, session, EditorSession.Screen.SECTION_FORM);
        }
    }

    public void clickSectionCorner(Player player, int index, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            if (index == 1) {
                session.sectionCorner1(null);
            } else {
                session.sectionCorner2(null);
            }
            menus.sectionForm(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, index == 1 ? MarkTarget.SECTION_CORNER_1 : MarkTarget.SECTION_CORNER_2, EditorSession.Screen.SECTION_FORM);
        }
    }

    public void saveSection(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        String name = session.sectionName();
        Location first = session.sectionCorner1();
        Location second = session.sectionCorner2();
        if (name == null || first == null || second == null) {
            reply(player, "&cSet the name and both corners first.");
            return;
        }
        if (!sameWorld(first, second)) {
            reply(player, "&cCorners must be in the same world.");
            return;
        }
        if (session.data().sections().containsKey(name)) {
            reply(player, "&cThat section already exists.");
            return;
        }
        session.data().sections().put(name, ArenaSection.of(first, second));
        session.clearSectionDraft();
        persist(session);
        info(player, "editor.saved-section", Map.of("name", name));
        showSections(player);
    }

    public void showDoors(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.mode(EditMode.NONE);
        session.clearSelected();
        session.screen(EditorSession.Screen.DOORS);
        menus.doors(player, session);
    }

    public void beginDoor(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (session.data().sections().size() < 2) {
            reply(player, "&cCreate at least two sections first.");
            return;
        }
        session.clearSelected();
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.DOOR_PICK);
        menus.doorPick(player, session);
    }

    public void toggleDoorSection(Player player, String name) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (!session.toggleSelected(name)) {
            reply(player, "&cOnly two sections can be selected.");
            return;
        }
        menus.doorPick(player, session);
    }

    public void createDoor(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        List<String> selected = session.selected();
        if (selected.size() != 2) {
            reply(player, "&cSelect exactly two sections.");
            return;
        }
        ArenaDoor door = new ArenaDoor(selected.get(0), selected.get(1));
        if (session.data().doors().containsKey(door.key())) {
            reply(player, "&cThat door already exists.");
            return;
        }
        session.data().doors().put(door.key(), door);
        session.clearSelected();
        persist(session);
        info(player, "editor.door-created");
        showDoors(player);
    }

    public void clickDoor(Player player, String key, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            ArenaDoor removed = session.data().doors().remove(key);
            if (removed != null) {
                clearDoor(session.data().name(), removed);
            }
            persist(session);
            info(player, "editor.removed-door");
            menus.doors(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            if (!session.data().doors().containsKey(key)) {
                return;
            }
            session.doorKey(key);
            session.mode(EditMode.NONE);
            session.screen(EditorSession.Screen.DOOR_EDIT);
            menus.doorEdit(player, session);
        }
    }

    public void clickDoorBlocks(Player player, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaDoor door = door(session);
        if (door == null) {
            showDoors(player);
            return;
        }
        if (click == MenuClick.RIGHT) {
            door.clearBlocks();
            persist(session);
            info(player, "editor.cleared-door-blocks");
            menus.doorEdit(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            session.mode(EditMode.DOOR_BLOCKS);
            session.screen(EditorSession.Screen.DOOR_EDIT);
            player.closeInventory();
            info(player, "editor.place-door-blocks");
        }
    }

    public void clickDoorPrice(Player player, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaDoor door = door(session);
        if (door == null) {
            showDoors(player);
            return;
        }
        if (click == MenuClick.RIGHT) {
            door.price(null);
            persist(session);
            showDoor(session.data().name(), door);
            menus.doorEdit(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            session.mode(EditMode.PRICE);
            session.screen(EditorSession.Screen.DOOR_EDIT);
            player.closeInventory();
            info(player, "editor.type-price");
        }
    }

    public void clickDoorHologram(Player player, int side, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaDoor door = door(session);
        if (door == null) {
            showDoors(player);
            return;
        }
        if (click == MenuClick.RIGHT) {
            FinePoint point = side == 0 ? door.hologramA() : door.hologramB();
            if (side == 0) {
                door.hologramA(null);
            } else {
                door.hologramB(null);
            }
            holograms.clearAll(session.data().name(), point);
            persist(session);
            menus.doorEdit(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, side == 0 ? MarkTarget.DOOR_HOLOGRAM_A : MarkTarget.DOOR_HOLOGRAM_B, EditorSession.Screen.DOOR_EDIT);
        }
    }

    public void showWindows(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.mode(EditMode.NONE);
        session.clearWindowDraft();
        session.screen(EditorSession.Screen.WINDOWS);
        menus.windows(player, session);
    }

    public void showWindowForm(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.WINDOW_FORM);
        menus.windowForm(player, session);
    }

    public void beginWindow(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.data().windows().add(new ArenaWindow());
        persist(session);
        info(player, "editor.window-created");
        showWindows(player);
    }

    public void clickWindow(Player player, int index, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        List<ArenaWindow> windows = session.data().windows();
        if (index < 0 || index >= windows.size()) {
            menus.windows(player, session);
            return;
        }
        if (click == MenuClick.RIGHT) {
            windows.remove(index);
            persist(session);
            info(player, "editor.removed-window");
            menus.windows(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            openWindow(player, session, index);
        }
    }

    private void openWindow(Player player, EditorSession session, int index) {
        ArenaWindow window = session.data().windows().get(index);
        session.windowIndex(index);
        session.windowSection(window.section());
        session.windowCorner1(location(window.corner1() != null ? window.corner1() : window.faceMin()));
        session.windowCorner2(location(window.corner2() != null ? window.corner2() : window.faceMax()));
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.WINDOW_FORM);
        menus.windowForm(player, session);
    }

    public void clickWindowCorner(Player player, int index, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaWindow window = window(session);
        if (window == null) {
            showWindows(player);
            return;
        }
        if (session.windowSaving()) {
            reply(player, "&cSave already running.");
            return;
        }
        if (click == MenuClick.RIGHT) {
            if (index == 1) {
                window.corner1(null);
                session.windowCorner1(null);
            } else {
                window.corner2(null);
                session.windowCorner2(null);
            }
            window.clearFace();
            persist(session);
            menus.windowForm(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, index == 1 ? MarkTarget.WINDOW_CORNER_1 : MarkTarget.WINDOW_CORNER_2, EditorSession.Screen.WINDOW_FORM);
        }
    }

    public void clickWindowSection(Player player, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaWindow window = window(session);
        if (window == null) {
            showWindows(player);
            return;
        }
        if (click == MenuClick.RIGHT) {
            window.section(null);
            session.windowSection(null);
            persist(session);
            menus.windowForm(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            session.mode(EditMode.NONE);
            menus.pickWindowSection(player, session);
        }
    }

    public void pickWindowSection(Player player, String section) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaWindow window = window(session);
        if (window == null) {
            showWindows(player);
            return;
        }
        if (!session.data().sections().containsKey(section)) {
            reply(player, "&cThat section no longer exists.");
            return;
        }
        window.section(section);
        session.windowSection(section);
        persist(session);
        session.screen(EditorSession.Screen.WINDOW_FORM);
        menus.windowForm(player, session);
    }

    public void clickWindowSpawn(Player player, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        ArenaWindow window = window(session);
        if (window == null) {
            showWindows(player);
            return;
        }
        if (click == MenuClick.RIGHT) {
            window.spawn(null);
            persist(session);
            menus.windowForm(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, MarkTarget.WINDOW_SPAWN, EditorSession.Screen.WINDOW_FORM);
        }
    }

    private void storeWindowCorner(Player player, EditorSession session, Location location, boolean first) {
        ArenaWindow window = window(session);
        if (window == null) {
            session.mode(EditMode.NONE);
            later(player, "&cThat window no longer exists.", () -> menus.windows(player, session));
            return;
        }
        if (session.windowSaving()) {
            later(player, "&cSave already running. Left-click again, or type &fleave&e.", null);
            return;
        }
        BlockPoint other = first ? window.corner2() : window.corner1();
        if (other != null && location.getWorld() != null && !location.getWorld().getName().equals(other.world())) {
            later(player, "&cCorners must be in the same world. Left-click again, or type &fleave&e.", null);
            return;
        }
        BlockPoint point = BlockPoint.of(location);
        if (first) {
            window.corner1(point);
            session.windowCorner1(location);
        } else {
            window.corner2(point);
            session.windowCorner2(location);
        }
        session.mode(EditMode.NONE);
        if (window.corner1() == null || window.corner2() == null) {
            window.clearFace();
            persist(session);
            laterInfo(player, "editor.corner-set", Map.of("n", first ? "1" : "2", "pos", format(location)), () -> menus.windowForm(player, session));
            return;
        }
        Location firstLocation = location(window.corner1());
        Location secondLocation = location(window.corner2());
        if (firstLocation == null || secondLocation == null) {
            window.clearFace();
            persist(session);
            later(player, "&cThat world is not loaded.", () -> menus.windowForm(player, session));
            return;
        }
        String size = WindowFace.sizeOf(firstLocation, secondLocation);
        if (size == null) {
            window.clearFace();
            persist(session);
            later(player, "&cThe area must be a flat 2x2 or 2x3.", () -> menus.windowForm(player, session));
            return;
        }
        session.windowSaving(true);
        info(player, "editor.saving-window");
        snapshotWindow(player, session, window, firstLocation.getWorld(), size, WindowFace.min(firstLocation, secondLocation), WindowFace.max(firstLocation, secondLocation));
    }

    private void snapshotWindow(Player player, EditorSession session, ArenaWindow window, World world, String size, BlockPoint min, BlockPoint max) {
        List<BlockPoint> positions = WindowFace.positions(min, max);
        Map<Long, List<Integer>> chunks = new LinkedHashMap<>();
        for (int index = 0; index < positions.size(); index++) {
            BlockPoint point = positions.get(index);
            long key = chunkKey(point.x() >> 4, point.z() >> 4);
            chunks.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
        }
        AtomicInteger remaining = new AtomicInteger(chunks.size());
        ConcurrentLinkedQueue<CapturedBlock> found = new ConcurrentLinkedQueue<>();
        for (Map.Entry<Long, List<Integer>> entry : chunks.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) entry.getKey().longValue();
            List<Integer> indexes = entry.getValue();
            plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                try {
                    for (int index : indexes) {
                        BlockPoint point = positions.get(index);
                        BlockData data = world.getBlockAt(point.x(), point.y(), point.z()).getBlockData();
                        found.add(new CapturedBlock(index, new WindowBlock(point, data.getAsString())));
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        List<CapturedBlock> ordered = new ArrayList<>(found);
                        ordered.sort(Comparator.comparingInt(CapturedBlock::index));
                        List<WindowBlock> blocks = new ArrayList<>(ordered.size());
                        for (CapturedBlock captured : ordered) {
                            blocks.add(captured.block());
                        }
                        FoliaManager.runAtEntity(player, () -> finishWindow(player, session, window, size, min, max, blocks));
                    }
                }
            });
        }
    }

    private void finishWindow(Player player, EditorSession session, ArenaWindow window, String size, BlockPoint min, BlockPoint max, List<WindowBlock> blocks) {
        session.windowSaving(false);
        if (sessions.get(player.getUniqueId()) != session || session.data() == null || !session.data().windows().contains(window)) {
            return;
        }
        window.applyFace(size, min, max, blocks);
        persist(session);
        if (!player.isOnline()) {
            return;
        }
        info(player, "editor.window-saved");
        openWindow(player, session, session.data().windows().indexOf(window));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public void showSpots(Player player, SpotKind kind) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.spotKind(kind);
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.SPOT);
        menus.spots(player, session, kind);
    }

    public void beginSpot(Player player, SpotKind kind) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        session.spotKind(kind);
        armMark(player, session, MarkTarget.SPOT, EditorSession.Screen.SPOT);
    }

    public void clickSpot(Player player, SpotKind kind, int index, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.LEFT && kind == SpotKind.ARMOR) {
            session.spotIndex(index);
            shopStock.open(player, session);
            return;
        }
        if (click == MenuClick.LEFT && kind == SpotKind.WEAPONS) {
            session.spotIndex(index);
            shopStock.openWeapons(player, session);
            return;
        }
        if (click != MenuClick.RIGHT) {
            return;
        }
        List<FinePoint> points = session.data().spots(kind);
        if (index >= 0 && index < points.size()) {
            FinePoint removed = points.remove(index);
            session.data().forgetSpot(removed);
            holograms.clearAll(session.data().name(), removed);
            persist(session);
            info(player, "editor.removed");
        }
        menus.spots(player, session, kind);
    }

    public void deleteArena(Player player, MenuClick click) {
        if (click != MenuClick.RIGHT) {
            return;
        }
        EditorSession session = editSession(player);
        if (session == null || session.data() == null) {
            return;
        }
        String name = session.data().name();
        holograms.clearKnown(session.data());
        sessions.remove(player.getUniqueId());
        editors.remove(name, player.getUniqueId());
        player.closeInventory();
        info(player, "editor.deleting", Map.of("name", name));
        blueprints.delete(name).thenCompose(ignored -> arenas.delete(name)).whenComplete((ignored, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("Arena delete " + name + ": " + error.getMessage());
                reply(player, "&cDelete failed. Check console.");
                return;
            }
            info(player, "editor.deleted", Map.of("name", name));
        }));
    }

    public void closeEdit(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.data() != null) {
            editors.remove(session.data().name(), player.getUniqueId());
        }
        player.closeInventory();
        info(player, "editor.closed");
    }

    public void onQuit(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.data() != null) {
            editors.remove(session.data().name(), player.getUniqueId());
        }
    }

    public void onChat(Player player, String text) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode() == EditMode.NONE) {
            return;
        }
        if ("leave".equalsIgnoreCase(text)) {
            EditMode mode = session.mode();
            session.mode(EditMode.NONE);
            if (mode == EditMode.ARMOR_PRICE) {
                laterInfo(player, "editor.left-mode", null, () -> shopStock.open(player, session));
                return;
            }
            laterInfo(player, "editor.left-mode", null, () -> openScreen(player, session));
            return;
        }
        if (session.mode() == EditMode.ARMOR_PRICE) {
            shopStock.onPrice(player, session, text);
            return;
        }
        if (session.mode() == EditMode.NAME) {
            onName(player, session, text);
            return;
        }
        if (session.mode() == EditMode.PRICE) {
            onPrice(player, session, text);
            return;
        }
        if (session.mode() == EditMode.MARK) {
            String hint;
            if (session.markTarget() == MarkTarget.WINDOW_SPAWN) {
                hint = "editor.mark-spawn";
            } else if (isHologram(session.markTarget())) {
                hint = "editor.mark-hologram";
            } else {
                hint = "editor.mark-block";
            }
            laterInfo(player, hint, null, null);
            return;
        }
        if (session.mode() == EditMode.DOOR_BLOCKS) {
            laterInfo(player, "editor.place-blocks-hint", null, null);
        }
    }

    public void leftClickBlock(Player player, Block block) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode() != EditMode.MARK) {
            return;
        }
        if (!session.claim(block)) {
            return;
        }
        Location location = block.getLocation();
        MarkTarget target = session.markTarget();
        if (target == null) {
            return;
        }
        switch (target) {
            case CREATE_CORNER_1 -> setCorner(player, session, location, session.corner2(), true, true);
            case CREATE_CORNER_2 -> setCorner(player, session, location, session.corner1(), false, true);
            case SECTION_CORNER_1 -> setCorner(player, session, location, session.sectionCorner2(), true, false);
            case SECTION_CORNER_2 -> setCorner(player, session, location, session.sectionCorner1(), false, false);
            case WINDOW_CORNER_1 -> storeWindowCorner(player, session, location, true);
            case WINDOW_CORNER_2 -> storeWindowCorner(player, session, location, false);
            case SPOT, DOOR_HOLOGRAM_A, DOOR_HOLOGRAM_B, WINDOW_SPAWN -> confirmHologram(player);
        }
    }

    public void confirmHologram(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !hologramMark(player)) {
            return;
        }
        if (!session.claim(player.getLocation().getBlock())) {
            return;
        }
        if (session.markTarget() == MarkTarget.SPOT) {
            finishSpot(player, session, player.getEyeLocation());
            return;
        }
        if (session.markTarget() == MarkTarget.WINDOW_SPAWN) {
            finishWindowSpawn(player, session, player.getLocation());
            return;
        }
        finishHologram(player, session, player.getEyeLocation());
    }

    public void placeDoorBlock(Player player, Block block) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode() != EditMode.DOOR_BLOCKS) {
            return;
        }
        ArenaDoor door = door(session);
        if (door == null) {
            return;
        }
        if (!door.addBlock(BlockPoint.of(block.getLocation()))) {
            return;
        }
        persist(session);
        info(player, "editor.door-block-added");
    }

    private void finishOpen(Player player, String name, ArenaData data, Throwable error) {
        if (!player.isOnline()) {
            return;
        }
        if (error != null) {
            plugin.getLogger().warning("Arena " + name + ": " + error.getMessage());
            reply(player, "&cCould not read that arena. Check console.");
            return;
        }
        if (data == null) {
            reply(player, "&cArena " + name + " does not exist.");
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            reply(player, "&cClose the editor you already have open.");
            return;
        }
        EditorSession session = EditorSession.edit(player.getUniqueId(), data);
        sessions.put(player.getUniqueId(), session);
        if (editors.putIfAbsent(name, player.getUniqueId()) != null) {
            sessions.remove(player.getUniqueId());
            reply(player, "&cSomeone else is editing that arena.");
            return;
        }
        if (!player.isOnline()) {
            sessions.remove(player.getUniqueId());
            editors.remove(name, player.getUniqueId());
            return;
        }
        menus.hub(player, session);
    }

    private void onName(Player player, EditorSession session, String text) {
        if (!Names.valid(text)) {
            later(player, "&cName must be 1-32 letters and numbers. Type it again, or &fleave&c.", null);
            return;
        }
        if (session.screen() == EditorSession.Screen.SECTION_FORM) {
            if (session.data() != null && session.data().sections().containsKey(text)) {
                later(player, "&cThat section already exists. Type another name, or &fleave&c.", null);
                return;
            }
            session.sectionName(text);
            session.mode(EditMode.NONE);
            laterInfo(player, "editor.name-set", Map.of("name", text), () -> menus.sectionForm(player, session));
            return;
        }
        session.createName(text);
        session.mode(EditMode.NONE);
        laterInfo(player, "editor.name-set", Map.of("name", text), () -> menus.create(player, session));
    }

    private void onPrice(Player player, EditorSession session, String text) {
        if (!text.matches("[0-9]{1,9}")) {
            later(player, "&cType a whole number, or &fleave&c.", null);
            return;
        }
        int price = Integer.parseInt(text);
        session.mode(EditMode.NONE);
        laterInfo(player, "editor.price-set", Map.of("price", Integer.toString(price)), () -> {
            ArenaDoor door = door(session);
            if (door == null) {
                menus.doors(player, session);
                return;
            }
            door.price(price);
            persist(session);
            showDoor(session.data().name(), door);
            menus.doorEdit(player, session);
        });
    }

    private void setCorner(Player player, EditorSession session, Location location, Location other, boolean first, boolean create) {
        if (other != null && !sameWorld(location, other)) {
            later(player, "&cCorners must be in the same world. Left-click again, or type &fleave&e.", null);
            return;
        }
        if (create) {
            if (first) {
                session.corner1(location);
            } else {
                session.corner2(location);
            }
        } else if (first) {
            session.sectionCorner1(location);
        } else {
            session.sectionCorner2(location);
        }
        finishMark(player, session, first ? "1" : "2", location);
    }

    private void finishMark(Player player, EditorSession session, String number, Location location) {
        session.mode(EditMode.NONE);
        laterInfo(player, "editor.corner-set", Map.of("n", number, "pos", format(location)), () -> openScreen(player, session));
    }

    private void finishSpot(Player player, EditorSession session, Location location) {
        SpotKind kind = session.spotKind();
        if (kind == null || location.getWorld() == null) {
            session.mode(EditMode.NONE);
            later(player, "&cThat spot could not be saved.", () -> openScreen(player, session));
            return;
        }
        List<FinePoint> points = session.data().spots(kind);
        if (kind == SpotKind.POWER) {
            for (FinePoint old : List.copyOf(points)) {
                holograms.clearAll(session.data().name(), old);
            }
            points.clear();
        }
        FinePoint point = FinePoint.of(location);
        points.add(point);
        session.mode(EditMode.NONE);
        persist(session);
        holograms.showSpotAll(session.data().name(), kind, point);
        laterInfo(player, kind == SpotKind.POWER ? "editor.power-set" : "editor.hologram-saved", Map.of("pos", point.format()), () -> menus.spots(player, session, kind));
    }

    private void finishHologram(Player player, EditorSession session, Location location) {
        ArenaDoor door = door(session);
        session.mode(EditMode.NONE);
        if (door == null) {
            later(player, "&cThat door no longer exists.", () -> menus.doors(player, session));
            return;
        }
        boolean second = session.markTarget() == MarkTarget.DOOR_HOLOGRAM_B;
        FinePoint point = FinePoint.of(location);
        if (second) {
            door.hologramB(point);
        } else {
            door.hologramA(point);
        }
        persist(session);
        holograms.showDoorAll(session.data().name(), door.price(), point);
        laterInfo(player, "editor.door-hologram-set", Map.of("n", second ? "2" : "1", "pos", point.format()), () -> menus.doorEdit(player, session));
    }

    private void finishWindowSpawn(Player player, EditorSession session, Location location) {
        ArenaWindow window = window(session);
        session.mode(EditMode.NONE);
        if (window == null || location.getWorld() == null) {
            later(player, "&cThat window no longer exists.", () -> menus.windows(player, session));
            return;
        }
        FinePoint point = FinePoint.of(location);
        window.spawn(point);
        persist(session);
        laterInfo(player, "editor.spawn-set", Map.of("pos", point.format()), () -> menus.windowForm(player, session));
    }

    private void armName(Player player, EditorSession session, EditorSession.Screen screen) {
        session.mode(EditMode.NAME);
        session.screen(screen);
        player.closeInventory();
        info(player, "editor.type-name");
    }

    private void armMark(Player player, EditorSession session, MarkTarget target, EditorSession.Screen screen) {
        session.mode(EditMode.MARK);
        session.markTarget(target);
        session.screen(screen);
        player.closeInventory();
        if (target == MarkTarget.WINDOW_SPAWN) {
            info(player, "editor.mark-spawn");
            return;
        }
        if (isHologram(target)) {
            info(player, "editor.mark-hologram");
            return;
        }
        info(player, "editor.mark-block");
    }

    private static boolean isHologram(MarkTarget target) {
        return target == MarkTarget.SPOT || target == MarkTarget.DOOR_HOLOGRAM_A || target == MarkTarget.DOOR_HOLOGRAM_B || target == MarkTarget.WINDOW_SPAWN;
    }

    private void openScreen(Player player, EditorSession session) {
        switch (session.screen()) {
            case CREATE -> menus.create(player, session);
            case HUB -> menus.hub(player, session);
            case SECTIONS -> menus.sections(player, session);
            case SECTION_FORM -> menus.sectionForm(player, session);
            case DOORS -> menus.doors(player, session);
            case DOOR_PICK -> menus.doorPick(player, session);
            case DOOR_EDIT -> menus.doorEdit(player, session);
            case WINDOWS -> menus.windows(player, session);
            case WINDOW_FORM -> menus.windowForm(player, session);
            case SPOT -> menus.spots(player, session, session.spotKind() == null ? SpotKind.WEAPONS : session.spotKind());
        }
    }

    private ArenaDoor door(EditorSession session) {
        if (session.data() == null || session.doorKey() == null) {
            return null;
        }
        return session.data().doors().get(session.doorKey());
    }

    private ArenaWindow window(EditorSession session) {
        if (session.data() == null) {
            return null;
        }
        int index = session.windowIndex();
        List<ArenaWindow> windows = session.data().windows();
        if (index < 0 || index >= windows.size()) {
            return null;
        }
        return windows.get(index);
    }

    private Location location(BlockPoint point) {
        if (point == null) {
            return null;
        }
        World world = plugin.getServer().getWorld(point.world());
        if (world == null) {
            return null;
        }
        return new Location(world, point.x(), point.y(), point.z());
    }

    private EditorSession createSession(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.kind() != EditorSession.Kind.CREATE || session.saving()) {
            return null;
        }
        return session;
    }

    private EditorSession editSession(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || session.kind() != EditorSession.Kind.EDIT) {
            return null;
        }
        return session;
    }

    void persist(EditorSession session) {
        arenas.save(session.data());
    }

    private void finishCreateFailure(Player player, EditorSession session, String message) {
        FoliaManager.runAtEntity(player, () -> {
            session.saving(false);
            if (!player.isOnline()) {
                return;
            }
            reply(player, message);
            menus.create(player, session);
        });
    }

    private void showDoor(String arena, ArenaDoor door) {
        holograms.showDoorAll(arena, door.price(), door.hologramA());
        holograms.showDoorAll(arena, door.price(), door.hologramB());
    }

    private void clearDoor(String arena, ArenaDoor door) {
        holograms.clearAll(arena, door.hologramA());
        holograms.clearAll(arena, door.hologramB());
    }

    void info(Player player, String key) {
        info(player, key, null);
    }

    private void info(Player player, String key, Map<String, ?> values) {
        if (values == null) {
            plugin.getMessageManager().send(player, key);
            return;
        }
        plugin.getMessageManager().send(player, key, values);
    }

    void laterInfo(Player player, String key, Map<String, ?> values, Runnable then) {
        FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            info(player, key, values);
            if (then != null) {
                then.run();
            }
        });
    }

    void later(Player player, String message, Runnable then) {
        FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            reply(player, message);
            if (then != null) {
                then.run();
            }
        });
    }

    void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }

    private static boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private static String format(Location location) {
        if (location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private record CapturedBlock(int index, WindowBlock block) {
    }
}
