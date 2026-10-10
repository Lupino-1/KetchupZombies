package dev.rajce.ketchupZombies.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.rajce.ketchupZombies.KetchupZombies;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class BlueprintManager {

    private final Gson gson = new Gson();
    private final File folder;
    private final KetchupZombies plugin;

    @Getter
    private volatile Map<String, JsonObject> blueprints = Map.of();

    public BlueprintManager(KetchupZombies plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "blueprints");
        folder.mkdirs();
    }

    public synchronized void reload() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }

        Map<String, JsonObject> loaded = new HashMap<>();
        for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath())) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                if (json != null) {
                    loaded.put(name(file), json);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Blueprint " + file.getName() + ": " + e.getMessage());
            }
        }
        blueprints = Map.copyOf(loaded);
    }

    public synchronized void save(String name, JsonObject json) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file(name).toPath())) {
            gson.toJson(json, writer);
        }
    }

    public JsonObject get(String name) {
        return blueprints.get(name);
    }

    private File file(String name) {
        return new File(folder, name + ".json");
    }

    private String name(File file) {
        String filename = file.getName();
        return filename.substring(0, filename.length() - ".json".length());
    }
}
