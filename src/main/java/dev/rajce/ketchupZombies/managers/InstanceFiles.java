package dev.rajce.ketchupZombies.managers;

import dev.rajce.ketchupZombies.KetchupZombies;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InstanceFiles {

    private final File folder;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public InstanceFiles(KetchupZombies plugin) {
        folder = new File(plugin.getDataFolder(), "instances");
        folder.mkdirs();
    }

    public List<Integer> indexesNow(String name) {
        synchronized (lock(name)) {
            List<Integer> indexes = new ArrayList<>();
            for (Copy copy : read(name)) {
                indexes.add(copy.index);
            }
            return indexes;
        }
    }

    public int claimNow(String name) throws IOException {
        synchronized (lock(name)) {
            List<Copy> copies = read(name);
            for (int i = 0; i < copies.size(); i++) {
                Copy copy = copies.get(i);
                if (copy.free) {
                    copies.set(i, new Copy(copy.index, false));
                    write(name, copies);
                    return copy.index;
                }
            }
            return -1;
        }
    }

    public int reserveNow(String name) throws IOException {
        return reserve(name, true);
    }

    public int reserveBusyNow(String name) throws IOException {
        return reserve(name, false);
    }

    public void freeNow(String name, int index) throws IOException {
        synchronized (lock(name)) {
            List<Copy> copies = read(name);
            for (int i = 0; i < copies.size(); i++) {
                if (copies.get(i).index == index) {
                    copies.set(i, new Copy(index, true));
                }
            }
            write(name, copies);
        }
    }

    private int reserve(String name, boolean free) throws IOException {
        synchronized (lock(name)) {
            List<Copy> copies = read(name);
            int next = 1;
            for (Copy copy : copies) {
                next = Math.max(next, copy.index + 1);
            }
            copies.add(new Copy(next, free));
            write(name, copies);
            return next;
        }
    }

    private List<Copy> read(String name) {
        File file = file(name);
        List<Copy> copies = new ArrayList<>();
        if (!file.isFile()) {
            return copies;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yaml.getMapList("copies")) {
            Object index = raw.get("index");
            if (index instanceof Number number && number.intValue() > 0) {
                copies.add(new Copy(number.intValue(), !Boolean.FALSE.equals(raw.get("free"))));
            }
        }
        return copies;
    }

    private void write(String name, List<Copy> copies) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Arena copies. Index 0 is the blueprint and is not listed."));
        yaml.set("name", name);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Copy copy : copies) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("index", copy.index);
            map.put("free", copy.free);
            rows.add(map);
        }
        yaml.set("copies", rows);
        yaml.save(file(name));
    }

    private Object lock(String name) {
        return locks.computeIfAbsent(name, ignored -> new Object());
    }

    private File file(String name) {
        return new File(folder, name + ".yml");
    }

    private record Copy(int index, boolean free) {
    }
}
