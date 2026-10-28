package dev.rajce.ketchupZombies.editor;

import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.SpotKind;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EditorSession {

    public enum Kind {
        CREATE,
        EDIT
    }

    public enum Screen {
        CREATE,
        HUB,
        SECTIONS,
        SECTION_FORM,
        DOORS,
        DOOR_PICK,
        DOOR_EDIT,
        MACHINES,
        MACHINE_EDIT,
        WINDOWS,
        WINDOW_FORM,
        SPOT
    }

    private final UUID playerId;
    private final Kind kind;
    private final ArenaData data;
    private EditMode mode = EditMode.NONE;
    private Screen screen = Screen.CREATE;
    private String createName;
    private Location corner1;
    private Location corner2;
    private boolean saving;
    private String sectionName;
    private Location sectionCorner1;
    private Location sectionCorner2;
    private String windowSection;
    private Location windowCorner1;
    private Location windowCorner2;
    private int windowIndex = -1;
    private boolean windowSaving;
    private SpotKind spotKind;
    private int spotIndex = -1;
    private String armorSet = "leather";
    private final List<String> armorPieces = new ArrayList<>();
    private MarkTarget markTarget;
    private String doorKey;
    private String machineId;
    private final List<String> selected = new ArrayList<>();
    private long handledTick = Long.MIN_VALUE;
    private int handledX;
    private int handledY;
    private int handledZ;
    private String handledWorld = "";
    private long suppressTick = Long.MIN_VALUE;

    private EditorSession(UUID playerId, Kind kind, ArenaData data) {
        this.playerId = playerId;
        this.kind = kind;
        this.data = data;
    }

    public static EditorSession create(UUID playerId) {
        EditorSession session = new EditorSession(playerId, Kind.CREATE, null);
        session.screen = Screen.CREATE;
        return session;
    }

    public static EditorSession edit(UUID playerId, ArenaData data) {
        EditorSession session = new EditorSession(playerId, Kind.EDIT, data);
        session.screen = Screen.HUB;
        return session;
    }

    public synchronized UUID playerId() {
        return playerId;
    }

    public synchronized Kind kind() {
        return kind;
    }

    public synchronized ArenaData data() {
        return data;
    }

    public synchronized EditMode mode() {
        return mode;
    }

    public synchronized void mode(EditMode mode) {
        this.mode = mode;
    }

    public synchronized Screen screen() {
        return screen;
    }

    public synchronized void screen(Screen screen) {
        this.screen = screen;
    }

    public synchronized String createName() {
        return createName;
    }

    public synchronized void createName(String createName) {
        this.createName = createName;
    }

    public synchronized Location corner1() {
        return copy(corner1);
    }

    public synchronized void corner1(Location corner1) {
        this.corner1 = copy(corner1);
    }

    public synchronized Location corner2() {
        return copy(corner2);
    }

    public synchronized void corner2(Location corner2) {
        this.corner2 = copy(corner2);
    }

    public synchronized boolean saving() {
        return saving;
    }

    public synchronized void saving(boolean saving) {
        this.saving = saving;
    }

    public synchronized boolean createReady() {
        return createName != null && corner1 != null && corner2 != null && !saving;
    }

    public synchronized String sectionName() {
        return sectionName;
    }

    public synchronized void sectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public synchronized Location sectionCorner1() {
        return copy(sectionCorner1);
    }

    public synchronized void sectionCorner1(Location sectionCorner1) {
        this.sectionCorner1 = copy(sectionCorner1);
    }

    public synchronized Location sectionCorner2() {
        return copy(sectionCorner2);
    }

    public synchronized void sectionCorner2(Location sectionCorner2) {
        this.sectionCorner2 = copy(sectionCorner2);
    }

    public synchronized String windowSection() {
        return windowSection;
    }

    public synchronized void windowSection(String windowSection) {
        this.windowSection = windowSection;
    }

    public synchronized MarkTarget markTarget() {
        return markTarget;
    }

    public synchronized void markTarget(MarkTarget markTarget) {
        this.markTarget = markTarget;
    }

    public synchronized String doorKey() {
        return doorKey;
    }

    public synchronized void doorKey(String doorKey) {
        this.doorKey = doorKey;
    }

    public synchronized String machineId() {
        return machineId;
    }

    public synchronized void machineId(String machineId) {
        this.machineId = machineId;
    }

    public synchronized List<String> selected() {
        return List.copyOf(selected);
    }

    public synchronized void clearSelected() {
        selected.clear();
    }

    public synchronized boolean selected(String name) {
        return selected.contains(name);
    }

    public synchronized boolean toggleSelected(String name) {
        if (selected.remove(name)) {
            return true;
        }
        if (selected.size() >= 2) {
            return false;
        }
        selected.add(name);
        return true;
    }

    public synchronized boolean claim(Block block) {
        long tick = block.getWorld().getFullTime();
        String world = block.getWorld().getName();
        if (handledTick == tick && handledX == block.getX() && handledY == block.getY() && handledZ == block.getZ() && world.equals(handledWorld)) {
            return false;
        }
        handledTick = tick;
        handledX = block.getX();
        handledY = block.getY();
        handledZ = block.getZ();
        handledWorld = world;
        suppressTick = tick;
        return true;
    }

    public synchronized boolean suppressBreak(Block block) {
        return suppressTick == block.getWorld().getFullTime();
    }

    public synchronized Location windowCorner1() {
        return copy(windowCorner1);
    }

    public synchronized void windowCorner1(Location windowCorner1) {
        this.windowCorner1 = copy(windowCorner1);
    }

    public synchronized Location windowCorner2() {
        return copy(windowCorner2);
    }

    public synchronized void windowCorner2(Location windowCorner2) {
        this.windowCorner2 = copy(windowCorner2);
    }

    public synchronized int windowIndex() {
        return windowIndex;
    }

    public synchronized void windowIndex(int windowIndex) {
        this.windowIndex = windowIndex;
    }

    public synchronized boolean windowSaving() {
        return windowSaving;
    }

    public synchronized void windowSaving(boolean windowSaving) {
        this.windowSaving = windowSaving;
    }

    public synchronized SpotKind spotKind() {
        return spotKind;
    }

    public synchronized void spotKind(SpotKind spotKind) {
        this.spotKind = spotKind;
    }

    public synchronized int spotIndex() {
        return spotIndex;
    }

    public synchronized void spotIndex(int spotIndex) {
        this.spotIndex = spotIndex;
    }

    public synchronized String armorSet() {
        return armorSet;
    }

    public synchronized void armorSet(String armorSet) {
        this.armorSet = armorSet;
        armorPieces.clear();
    }

    public synchronized List<String> armorPieces() {
        return List.copyOf(armorPieces);
    }

    public synchronized boolean toggleArmorPiece(String piece) {
        if (armorPieces.remove(piece)) {
            return false;
        }
        armorPieces.add(piece);
        return true;
    }

    public synchronized void clearSectionDraft() {
        sectionName = null;
        sectionCorner1 = null;
        sectionCorner2 = null;
    }

    public synchronized void clearWindowDraft() {
        windowSection = null;
        windowCorner1 = null;
        windowCorner2 = null;
        windowIndex = -1;
    }

    public synchronized void clearWizard() {
        clearSectionDraft();
        clearWindowDraft();
        clearSelected();
        doorKey = null;
        machineId = null;
        markTarget = null;
        spotKind = null;
    }

    private static Location copy(Location location) {
        return location == null ? null : location.clone();
    }
}
