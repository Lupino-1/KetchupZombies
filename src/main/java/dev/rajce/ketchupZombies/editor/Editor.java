package dev.rajce.ketchupZombies.editor;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.managers.ArenaFiles;
import dev.rajce.ketchupZombies.managers.BlueprintManager;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.Blueprint;
import dev.rajce.ketchupZombies.models.BlockPoint;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class Editor {

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private final ArenaFiles arenas;
    private final ArenaMenus menus;
    private final ConcurrentHashMap<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> editors = new ConcurrentHashMap<>();
    private final java.util.Set<String> creating = ConcurrentHashMap.newKeySet();

    public Editor(KetchupZombies plugin, BlueprintManager blueprints, ArenaFiles arenas) {
        this.plugin = plugin;
        this.blueprints = blueprints;
        this.arenas = arenas;
        this.menus = new ArenaMenus(this);
    }

    public KetchupZombies plugin() {
        return plugin;
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
            reply(player, "&cSave the arena you are creating first.");
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
        reply(player, "&7Loading arena...");
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
        reply(player, "&7Saving " + name + "...");
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
                            if (player.isOnline()) {
                                reply(player, "&aSaved blueprint and arena file for &f" + name + "&a.");
                            }
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
        data.doors().entrySet().removeIf(entry -> name.equals(entry.getValue().from()) || name.equals(entry.getValue().to()));
        data.windows().removeIf(window -> name.equals(window.section()));
        persist(session);
        reply(player, "&aRemoved section &f" + name + "&a.");
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
        reply(player, "&aSection &f" + name + "&a saved.");
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
        ArenaDoor door = new ArenaDoor(selected.get(0), selected.get(1), 0, 0);
        if (session.data().doors().containsKey(door.key())) {
            reply(player, "&cThat door already exists.");
            return;
        }
        session.data().doors().put(door.key(), door);
        session.clearSelected();
        persist(session);
        reply(player, "&aDoor created. Left-click it to add blocks and the price hologram.");
        showDoors(player);
    }

    public void clickDoor(Player player, String key, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            session.data().doors().remove(key);
            persist(session);
            reply(player, "&aRemoved door.");
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
            reply(player, "&aCleared door blocks.");
            menus.doorEdit(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            session.mode(EditMode.DOOR_BLOCKS);
            session.screen(EditorSession.Screen.DOOR_EDIT);
            player.closeInventory();
            reply(player, "&ePlace blocks. Each placed block is part of the door. Type &fleave &eto stop.");
        }
    }

    public void clickDoorHologram(Player player, MenuClick click) {
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
            door.hologram(null);
            persist(session);
            menus.doorEdit(player, session);
            return;
        }
        if (click == MenuClick.LEFT) {
            armMark(player, session, MarkTarget.DOOR_HOLOGRAM, EditorSession.Screen.DOOR_EDIT);
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
        if (session.data().sections().isEmpty()) {
            reply(player, "&cCreate a section first.");
            return;
        }
        session.clearWindowDraft();
        session.mode(EditMode.NONE);
        session.screen(EditorSession.Screen.WINDOW_FORM);
        menus.windowForm(player, session);
    }

    public void clickWindow(Player player, int index, MenuClick click) {
        if (click != MenuClick.RIGHT) {
            return;
        }
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        List<ArenaWindow> windows = session.data().windows();
        if (index >= 0 && index < windows.size()) {
            windows.remove(index);
            persist(session);
            reply(player, "&aRemoved window.");
        }
        menus.windows(player, session);
    }

    public void clickWindowCorner(Player player, int index, MenuClick click) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        if (click == MenuClick.RIGHT) {
            if (index == 1) {
                session.windowCorner1(null);
            } else {
                session.windowCorner2(null);
            }
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
        if (click == MenuClick.RIGHT) {
            session.windowSection(null);
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
        session.windowSection(section);
        session.screen(EditorSession.Screen.WINDOW_FORM);
        menus.windowForm(player, session);
    }

    public void saveWindow(Player player) {
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        Location first = session.windowCorner1();
        Location second = session.windowCorner2();
        String section = session.windowSection();
        if (first == null || second == null || section == null) {
            reply(player, "&cSet both corners and a section first.");
            return;
        }
        if (!sameWorld(first, second)) {
            reply(player, "&cCorners must be in the same world.");
            return;
        }
        String size = WindowFace.sizeOf(first, second);
        if (size == null) {
            reply(player, "&cThe area must be a flat 2x2 or 2x3.");
            return;
        }
        if (!session.data().sections().containsKey(section)) {
            reply(player, "&cThat section no longer exists.");
            return;
        }
        World world = first.getWorld();
        BlockPoint min = WindowFace.min(first, second);
        BlockPoint max = WindowFace.max(first, second);
        session.clearWindowDraft();
        reply(player, "&7Saving window...");
        snapshotWindow(player, session, world, size, section, min, max);
    }

    private void snapshotWindow(Player player, EditorSession session, World world, String size, String section, BlockPoint min, BlockPoint max) {
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
                        FoliaManager.runAtEntity(player, () -> finishWindow(player, session, size, section, min, max, blocks));
                    }
                }
            });
        }
    }

    private void finishWindow(Player player, EditorSession session, String size, String section, BlockPoint min, BlockPoint max, List<WindowBlock> blocks) {
        if (sessions.get(player.getUniqueId()) != session || session.data() == null) {
            return;
        }
        session.data().windows().add(new ArenaWindow(size, section, min, max, null, blocks));
        persist(session);
        if (!player.isOnline()) {
            return;
        }
        reply(player, "&aWindow saved.");
        showWindows(player);
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
        if (click != MenuClick.RIGHT) {
            return;
        }
        EditorSession session = editSession(player);
        if (session == null) {
            return;
        }
        List<BlockPoint> points = session.data().spots(kind);
        if (index >= 0 && index < points.size()) {
            points.remove(index);
            persist(session);
            reply(player, "&aRemoved.");
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
        sessions.remove(player.getUniqueId());
        editors.remove(name, player.getUniqueId());
        player.closeInventory();
        reply(player, "&7Deleting " + name + "...");
        blueprints.delete(name).thenCompose(ignored -> arenas.delete(name)).whenComplete((ignored, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("Arena delete " + name + ": " + error.getMessage());
                reply(player, "&cDelete failed. Check console.");
                return;
            }
            reply(player, "&aDeleted arena and blueprint &f" + name + "&a.");
        }));
    }

    public void closeEdit(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.data() != null) {
            editors.remove(session.data().name(), player.getUniqueId());
        }
        player.closeInventory();
        reply(player, "&7Closed the arena editor.");
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
            session.mode(EditMode.NONE);
            later(player, "&7Left edit mode.", () -> openScreen(player, session));
            return;
        }
        if (session.mode() == EditMode.NAME) {
            onName(player, session, text);
            return;
        }
        if (session.mode() == EditMode.MARK) {
            String hint = isHologram(session.markTarget())
                    ? "&eStand on the spot and left-click. Type &fleave &eto cancel."
                    : "&eLeft-click a block, or type &fleave&e.";
            later(player, hint, null);
            return;
        }
        if (session.mode() == EditMode.DOOR_BLOCKS) {
            later(player, "&ePlace blocks, or type &fleave&e.", null);
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
            case WINDOW_CORNER_1 -> {
                session.windowCorner1(location);
                finishMark(player, session, "&aCorner 1 set to &f" + format(location) + "&a.");
            }
            case WINDOW_CORNER_2 -> {
                session.windowCorner2(location);
                finishMark(player, session, "&aCorner 2 set to &f" + format(location) + "&a.");
            }
            case SPOT, DOOR_HOLOGRAM -> confirmHologram(player);
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
            finishSpot(player, session, player.getLocation());
            return;
        }
        finishHologram(player, session, player.getLocation());
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
        reply(player, "&aDoor block added. Place another, or type &fleave&a.");
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
            later(player, "&aName set to &f" + text + "&a.", () -> menus.sectionForm(player, session));
            return;
        }
        session.createName(text);
        session.mode(EditMode.NONE);
        later(player, "&aName set to &f" + text + "&a.", () -> menus.create(player, session));
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
        finishMark(player, session, "&aCorner " + (first ? 1 : 2) + " set to &f" + format(location) + "&a.");
    }

    private void finishMark(Player player, EditorSession session, String message) {
        session.mode(EditMode.NONE);
        later(player, message, () -> openScreen(player, session));
    }

    private void finishSpot(Player player, EditorSession session, Location location) {
        SpotKind kind = session.spotKind();
        if (kind == null || location.getWorld() == null) {
            session.mode(EditMode.NONE);
            later(player, "&cThat spot could not be saved.", () -> openScreen(player, session));
            return;
        }
        List<BlockPoint> points = session.data().spots(kind);
        if (kind == SpotKind.POWER) {
            points.clear();
        }
        points.add(BlockPoint.of(location));
        session.mode(EditMode.NONE);
        persist(session);
        String message = kind == SpotKind.POWER ? "&aPower hologram set." : "&aHologram coordinate saved.";
        later(player, message, () -> menus.spots(player, session, kind));
    }

    private void finishHologram(Player player, EditorSession session, Location location) {
        ArenaDoor door = door(session);
        session.mode(EditMode.NONE);
        if (door == null) {
            later(player, "&cThat door no longer exists.", () -> menus.doors(player, session));
            return;
        }
        door.hologram(BlockPoint.of(location));
        persist(session);
        later(player, "&aPrice hologram set to &f" + format(location) + "&a.", () -> menus.doorEdit(player, session));
    }

    private void armName(Player player, EditorSession session, EditorSession.Screen screen) {
        session.mode(EditMode.NAME);
        session.screen(screen);
        player.closeInventory();
        reply(player, "&eType a name. Letters and numbers only, or &fleave&e.");
    }

    private void armMark(Player player, EditorSession session, MarkTarget target, EditorSession.Screen screen) {
        session.mode(EditMode.MARK);
        session.markTarget(target);
        session.screen(screen);
        player.closeInventory();
        if (isHologram(target)) {
            reply(player, "&eStand where the hologram should be and left-click. Type &fleave &eto cancel.");
            return;
        }
        reply(player, "&eLeft-click a block, or type &fleave&e.");
    }

    private static boolean isHologram(MarkTarget target) {
        return target == MarkTarget.SPOT || target == MarkTarget.DOOR_HOLOGRAM;
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

    private void persist(EditorSession session) {
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

    private void later(Player player, String message, Runnable then) {
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

    private void reply(Player player, String message) {
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
