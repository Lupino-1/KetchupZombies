package dev.rajce.ketchupZombies.managers;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.UltimateMachine;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.WindowBlock;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaFiles {

    private final KetchupZombies plugin;
    private final File folder;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    /** Latest snapshot waiting to flush — coalesces rapid editor saves so an older write cannot overwrite a newer one. */
    private final ConcurrentHashMap<String, ArenaData> pending = new ConcurrentHashMap<>();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private volatile Set<String> names = Set.of();

    public ArenaFiles(KetchupZombies plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "arenas");
        folder.mkdirs();
        refreshNames();
    }

    public Set<String> names() {
        return names;
    }

    public List<String> listNow() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> found = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                found.add(filename.substring(0, filename.length() - 4));
            }
        }
        return found;
    }

    public ArenaData readNow(String name) {
        File file = file(name);
        if (!file.isFile()) {
            return null;
        }
        return read(file, name);
    }

    public void refreshNames() {
        FoliaManager.runAsync(() -> {
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            Set<String> found = new HashSet<>();
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    found.add(filename.substring(0, filename.length() - 4));
                }
            }
            names = Set.copyOf(found);
        });
    }

    public CompletableFuture<ArenaData> load(String name) {
        CompletableFuture<ArenaData> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            try {
                File file = file(name);
                if (!file.isFile()) {
                    future.complete(null);
                    return;
                }
                future.complete(read(file, name));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void save(ArenaData data) {
        if (data == null) {
            return;
        }
        String name = data.name();
        pending.put(name, data.copy());
        FoliaManager.runAsync(() -> flush(name));
    }

    private void flush(String name) {
        synchronized (lock(name)) {
            if (deleted.contains(name)) {
                pending.remove(name);
                return;
            }
            ArenaData latest = pending.remove(name);
            if (latest == null) {
                return;
            }
            try {
                write(latest);
                remember(name);
            } catch (IOException e) {
                plugin.getLogger().warning("Arena save " + name + ": " + e.getMessage());
            }
        }
    }

    public CompletableFuture<Void> createEmpty(String name) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            synchronized (lock(name)) {
                try {
                    write(new ArenaData(name));
                    remember(name);
                    future.complete(null);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    public boolean fileExists(String name) {
        return file(name).isFile();
    }

    public CompletableFuture<Void> delete(String name) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            synchronized (lock(name)) {
                try {
                    deleted.add(name);
                    if (file(name).isFile() && !file(name).delete()) {
                        throw new IOException("Could not delete " + file(name).getName());
                    }
                    forget(name);
                    future.complete(null);
                } catch (IOException e) {
                    deleted.remove(name);
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    private void remember(String name) {
        synchronized (this) {
            deleted.remove(name);
            Set<String> updated = new HashSet<>(names);
            updated.add(name);
            names = Set.copyOf(updated);
        }
    }

    private void forget(String name) {
        synchronized (this) {
            Set<String> updated = new HashSet<>(names);
            updated.remove(name);
            names = Set.copyOf(updated);
        }
    }

    private Object lock(String name) {
        return locks.computeIfAbsent(name, ignored -> new Object());
    }

    private File file(String name) {
        return new File(folder, name + ".yml");
    }

    private void write(ArenaData data) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Arena markers.",
                "Spot holograms: yaw/pitch from place look. fixed defaults true (Billboard.FIXED); set fixed: false for CENTER.",
                "Window repair-1 / repair-2: inclusive block AABB (player must sneak inside to repair).",
                "Map/marker change: /kz refresh <name>. Hologram text lines: config.yml holograms."
        ));
        yaml.set("name", data.name());
        if (data.spawn() != null) {
            yaml.set("spawn", fineMap(data.spawn()));
        }
        if (data.sections().isEmpty()) {
            yaml.createSection("sections");
        } else {
            for (Map.Entry<String, ArenaSection> entry : data.sections().entrySet()) {
                ArenaSection section = entry.getValue();
                String path = "sections." + entry.getKey();
                yaml.set(path + ".world", section.world());
                yaml.set(path + ".x1", section.x1());
                yaml.set(path + ".y1", section.y1());
                yaml.set(path + ".z1", section.z1());
                yaml.set(path + ".x2", section.x2());
                yaml.set(path + ".y2", section.y2());
                yaml.set(path + ".z2", section.z2());
            }
        }
        if (data.doors().isEmpty()) {
            yaml.createSection("doors");
        } else {
            for (ArenaDoor door : data.doors().values()) {
                String path = "doors." + door.key();
                yaml.set(path + ".from", door.from());
                yaml.set(path + ".to", door.to());
                if (door.price() != null) {
                    yaml.set(path + ".price", door.price());
                }
                if (door.hologramA() != null) {
                    yaml.set(path + ".hologram-a", fineMap(door.hologramA()));
                }
                if (door.hologramB() != null) {
                    yaml.set(path + ".hologram-b", fineMap(door.hologramB()));
                }
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (BlockPoint block : door.blocks()) {
                    blocks.add(pointMap(block));
                }
                yaml.set(path + ".blocks", blocks);
            }
        }
        if (data.machines().isEmpty()) {
            yaml.createSection("machines");
        } else {
            for (UltimateMachine machine : data.machines().values()) {
                String path = "machines." + machine.id();
                if (machine.hologram() != null) {
                    yaml.set(path + ".hologram", fineMap(machine.hologram()));
                }
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (BlockPoint block : machine.blocks()) {
                    blocks.add(pointMap(block));
                }
                yaml.set(path + ".blocks", blocks);
            }
        }
        List<Map<String, Object>> windows = new ArrayList<>();
        for (ArenaWindow window : data.windows()) {
            windows.add(windowMap(window));
        }
        yaml.set("windows", windows);
        for (SpotKind kind : SpotKind.values()) {
            List<Map<String, Object>> spots = new ArrayList<>();
            for (FinePoint point : data.spots(kind)) {
                Map<String, Object> stored = fineMap(point);
                if (data.customArmor(point)) {
                    List<Map<String, Object>> offers = new ArrayList<>();
                    for (ArmorOffer offer : data.armorOffers(point)) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("price", offer.price());
                        for (Map.Entry<String, String> slot : offer.slots().entrySet()) {
                            row.put(slot.getKey(), slot.getValue());
                        }
                        offers.add(row);
                    }
                    stored.put("armor", offers);
                }
                if (data.customWeapons(point)) {
                    List<String> weapons = new ArrayList<>();
                    for (WeaponId weapon : data.weaponOffers(point)) {
                        weapons.add(weapon.id());
                    }
                    stored.put("weapons", weapons);
                }
                spots.add(stored);
            }
            yaml.set("spots." + kind.key(), spots);
        }
        yaml.save(file(data.name()));
    }

    private ArenaData read(File file, String name) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ArenaData data = new ArenaData(name);
        ConfigurationSection spawnSection = yaml.getConfigurationSection("spawn");
        if (spawnSection != null) {
            data.spawn(fine(spawnSection.getValues(false), 0));
        }
        ConfigurationSection sections = yaml.getConfigurationSection("sections");
        if (sections != null) {
            for (String key : sections.getKeys(false)) {
                ConfigurationSection section = sections.getConfigurationSection(key);
                if (section == null || !section.contains("world")) {
                    continue;
                }
                data.sections().put(key, new ArenaSection(
                        section.getString("world"),
                        section.getInt("x1"),
                        section.getInt("y1"),
                        section.getInt("z1"),
                        section.getInt("x2"),
                        section.getInt("y2"),
                        section.getInt("z2")
                ));
            }
        }
        ConfigurationSection doors = yaml.getConfigurationSection("doors");
        if (doors != null) {
            for (String key : doors.getKeys(false)) {
                ConfigurationSection door = doors.getConfigurationSection(key);
                if (door == null || !door.contains("from") || !door.contains("to")) {
                    continue;
                }
                ArenaDoor value = new ArenaDoor(door.getString("from"), door.getString("to"));
                if (door.contains("price")) {
                    value.price(door.getInt("price"));
                } else if (door.getInt("price-from") > 0) {
                    value.price(door.getInt("price-from"));
                } else if (door.getInt("price-to") > 0) {
                    value.price(door.getInt("price-to"));
                }
                FinePoint hologramA = fine(section(door, "hologram-a"), 1.25);
                FinePoint hologramB = fine(section(door, "hologram-b"), 1.25);
                if (hologramA == null) {
                    hologramA = fine(section(door, "hologram"), 1.25);
                }
                value.hologramA(hologramA);
                value.hologramB(hologramB);
                for (Map<?, ?> raw : door.getMapList("blocks")) {
                    BlockPoint block = point(raw);
                    if (block != null) {
                        value.addBlock(block);
                    }
                }
                data.doors().put(value.key(), value);
            }
        }
        ConfigurationSection machines = yaml.getConfigurationSection("machines");
        if (machines != null) {
            for (String key : machines.getKeys(false)) {
                ConfigurationSection machine = machines.getConfigurationSection(key);
                if (machine == null) {
                    continue;
                }
                UltimateMachine value = new UltimateMachine(key);
                value.hologram(fine(section(machine, "hologram"), 1.25));
                for (Map<?, ?> raw : machine.getMapList("blocks")) {
                    BlockPoint block = point(raw);
                    if (block != null) {
                        value.addBlock(block);
                    }
                }
                data.machines().put(key, value);
            }
        }
        for (Map<?, ?> raw : yaml.getMapList("windows")) {
            ArenaWindow window = window(raw);
            if (window != null) {
                data.windows().add(window);
            }
        }
        for (SpotKind kind : SpotKind.values()) {
            for (Map<?, ?> raw : yaml.getMapList("spots." + kind.key())) {
                FinePoint point = fine(raw, 1.25);
                if (point != null) {
                    data.spots(kind).add(point);
                    if (raw.containsKey("armor")) {
                        data.armorOffers(point, armorOffers(raw.get("armor")));
                    }
                    if (raw.containsKey("weapons")) {
                        data.weaponOffers(point, weaponOffers(raw.get("weapons")));
                    }
                }
            }
        }
        return data;
    }

    private static List<WeaponId> weaponOffers(Object raw) {
        List<WeaponId> weapons = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return weapons;
        }
        for (Object entry : list) {
            if (entry instanceof String id) {
                WeaponId weapon = WeaponId.byId(id);
                if (weapon != null && !weapons.contains(weapon)) {
                    weapons.add(weapon);
                }
            }
        }
        return weapons;
    }

    private static Map<String, Object> windowMap(ArenaWindow window) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (window.size() != null) {
            map.put("size", window.size());
        }
        if (window.section() != null) {
            map.put("section", window.section());
        }
        if (window.corner1() != null) {
            map.put("corner-1", pointMap(window.corner1()));
        }
        if (window.corner2() != null) {
            map.put("corner-2", pointMap(window.corner2()));
        }
        if (window.faceMin() != null && window.faceMax() != null) {
            map.put("face-min", pointMap(window.faceMin()));
            map.put("face-max", pointMap(window.faceMax()));
        }
        if (window.spawn() != null) {
            map.put("spawn", fineMap(window.spawn()));
        }
        if (window.repair1() != null) {
            map.put("repair-1", pointMap(window.repair1()));
        }
        if (window.repair2() != null) {
            map.put("repair-2", pointMap(window.repair2()));
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (WindowBlock block : window.blocks()) {
            Map<String, Object> stored = pointMap(block.point());
            stored.put("data", block.data());
            blocks.add(stored);
        }
        map.put("blocks", blocks);
        return map;
    }

    private static Map<String, Object> pointMap(BlockPoint point) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("world", point.world());
        map.put("x", point.x());
        map.put("y", point.y());
        map.put("z", point.z());
        return map;
    }

    private static Map<String, Object> fineMap(FinePoint point) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("world", point.world());
        map.put("x", point.x());
        map.put("y", point.y());
        map.put("z", point.z());
        map.put("yaw", point.yaw());
        map.put("pitch", point.pitch());
        if (point.fixed() != null) {
            map.put("fixed", point.fixed());
        }
        map.put("precise", true);
        return map;
    }

    private static ArenaWindow window(Map<?, ?> raw) {
        ArenaWindow window = new ArenaWindow();
        if (raw.get("size") instanceof String size && !size.isBlank()) {
            window.applyFace(size, point(map(raw.get("face-min"))), point(map(raw.get("face-max"))), windowBlocks(raw.get("blocks")));
            if (window.faceMin() == null || window.faceMax() == null) {
                window.applyFace(null, null, null, List.of());
            }
        } else {
            window.applyFace(null, null, null, windowBlocks(raw.get("blocks")));
        }
        if (raw.get("section") instanceof String section && !section.isBlank()) {
            window.section(section);
        }
        BlockPoint corner1 = point(map(raw.get("corner-1")));
        BlockPoint corner2 = point(map(raw.get("corner-2")));
        window.corner1(corner1 != null ? corner1 : window.faceMin());
        window.corner2(corner2 != null ? corner2 : window.faceMax());
        window.spawn(fine(map(raw.get("spawn")), 0));
        window.repair1(point(map(raw.get("repair-1"))));
        window.repair2(point(map(raw.get("repair-2"))));
        return window;
    }

    private static Map<?, ?> section(ConfigurationSection parent, String key) {
        ConfigurationSection child = parent.getConfigurationSection(key);
        return child == null ? null : child.getValues(false);
    }

    private static List<WindowBlock> windowBlocks(Object raw) {
        List<WindowBlock> blocks = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return blocks;
        }
        for (Object item : list) {
            Map<?, ?> values = map(item);
            BlockPoint point = point(values);
            if (point == null || values == null || !(values.get("data") instanceof String data) || data.isBlank()) {
                continue;
            }
            blocks.add(new WindowBlock(point, data));
        }
        return blocks;
    }

    private static List<ArmorOffer> armorOffers(Object raw) {
        List<ArmorOffer> offers = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return offers;
        }
        for (Object item : list) {
            Map<?, ?> values = map(item);
            if (values == null) {
                continue;
            }
            int price = number(values.get("price"));
            Map<String, String> slots = new java.util.LinkedHashMap<>();
            for (String slot : ArmorOffer.SLOT_ORDER) {
                Object value = values.get(slot);
                if (value instanceof String set && !set.isBlank()) {
                    slots.put(slot, set);
                }
            }
            if (!slots.isEmpty()) {
                offers.add(new ArmorOffer(slots, price));
                continue;
            }
            // Legacy: set + pieces list
            if (!(values.get("set") instanceof String set) || set.isBlank()) {
                continue;
            }
            List<String> pieces = new ArrayList<>();
            Object stored = values.get("pieces");
            if (stored instanceof List<?> pieceList) {
                for (Object piece : pieceList) {
                    if (piece instanceof String text && !text.isBlank()) {
                        pieces.add(text);
                    }
                }
            } else if (stored instanceof String text) {
                for (String piece : text.split(",")) {
                    if (!piece.isBlank()) {
                        pieces.add(piece.trim());
                    }
                }
            }
            offers.add(new ArmorOffer(set, pieces, price));
        }
        return offers;
    }

    private static BlockPoint point(Map<?, ?> raw) {
        if (raw == null || !(raw.get("world") instanceof String world)) {
            return null;
        }
        return new BlockPoint(world, number(raw.get("x")), number(raw.get("y")), number(raw.get("z")));
    }

    private static FinePoint fine(Map<?, ?> raw, double lift) {
        if (raw == null || !(raw.get("world") instanceof String world)) {
            return null;
        }
        double x = decimal(raw.get("x"));
        double y = decimal(raw.get("y"));
        double z = decimal(raw.get("z"));
        if (!Boolean.TRUE.equals(raw.get("precise"))) {
            x += 0.5;
            y += lift;
            z += 0.5;
        }
        float yaw = (float) decimal(raw.get("yaw"));
        float pitch = (float) decimal(raw.get("pitch"));
        Boolean fixed = null;
        if (raw.containsKey("fixed")) {
            Object value = raw.get("fixed");
            if (value instanceof Boolean bool) {
                fixed = bool;
            }
        }
        return new FinePoint(world, x, y, z, yaw, pitch, fixed);
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static Map<?, ?> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return raw;
        }
        if (value instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        return null;
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
