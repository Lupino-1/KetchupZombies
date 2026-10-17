package dev.rajce.ketchupZombies.managers;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.WindowBlock;
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
        ArenaData copy = data.copy();
        FoliaManager.runAsync(() -> {
            synchronized (lock(copy.name())) {
                if (deleted.contains(copy.name())) {
                    return;
                }
                try {
                    write(copy);
                    remember(copy.name());
                } catch (IOException e) {
                    plugin.getLogger().warning("Arena save " + copy.name() + ": " + e.getMessage());
                }
            }
        });
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
        yaml.options().setHeader(List.of("Arena markers."));
        yaml.set("name", data.name());
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
                yaml.set(path + ".price-from", door.priceFrom());
                yaml.set(path + ".price-to", door.priceTo());
                if (door.hologram() != null) {
                    yaml.set(path + ".hologram", pointMap(door.hologram()));
                }
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (BlockPoint block : door.blocks()) {
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
            for (BlockPoint point : data.spots(kind)) {
                spots.add(pointMap(point));
            }
            yaml.set("spots." + kind.key(), spots);
        }
        yaml.save(file(data.name()));
    }

    private ArenaData read(File file, String name) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ArenaData data = new ArenaData(name);
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
                ArenaDoor value = new ArenaDoor(
                        door.getString("from"),
                        door.getString("to"),
                        door.getInt("price-from"),
                        door.getInt("price-to")
                );
                ConfigurationSection hologram = door.getConfigurationSection("hologram");
                if (hologram != null) {
                    value.hologram(point(hologram.getValues(false)));
                }
                for (Map<?, ?> raw : door.getMapList("blocks")) {
                    BlockPoint block = point(raw);
                    if (block != null) {
                        value.addBlock(block);
                    }
                }
                data.doors().put(value.key(), value);
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
                BlockPoint point = point(raw);
                if (point != null) {
                    data.spots(kind).add(point);
                }
            }
        }
        return data;
    }

    private static Map<String, Object> windowMap(ArenaWindow window) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("size", window.size());
        map.put("section", window.section());
        map.put("face-min", pointMap(window.faceMin()));
        map.put("face-max", pointMap(window.faceMax()));
        if (window.spawn() != null) {
            map.put("spawn", pointMap(window.spawn()));
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

    private static ArenaWindow window(Map<?, ?> raw) {
        if (!(raw.get("size") instanceof String size) || !(raw.get("section") instanceof String section)) {
            return null;
        }
        BlockPoint faceMin = point(map(raw.get("face-min")));
        BlockPoint faceMax = point(map(raw.get("face-max")));
        if (faceMin == null || faceMax == null) {
            return null;
        }
        return new ArenaWindow(size, section, faceMin, faceMax, point(map(raw.get("spawn"))), windowBlocks(raw.get("blocks")));
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

    private static BlockPoint point(Map<?, ?> raw) {
        if (raw == null || !(raw.get("world") instanceof String world)) {
            return null;
        }
        return new BlockPoint(world, number(raw.get("x")), number(raw.get("y")), number(raw.get("z")));
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
