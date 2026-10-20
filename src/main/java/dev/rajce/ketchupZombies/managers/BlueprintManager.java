package dev.rajce.ketchupZombies.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.Blueprint;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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

    public CompletableFuture<Stored> load(String name) {
        CompletableFuture<Stored> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            try {
                File file = file(name);
                if (!file.isFile()) {
                    future.complete(null);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(file.toPath())) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    future.complete(json == null ? null : parse(json));
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Stored parse(JsonObject json) {
        JsonObject first = json.getAsJsonObject("corner1");
        JsonObject second = json.getAsJsonObject("corner2");
        String worldName = first != null && first.has("world") ? first.get("world").getAsString() : "";
        Location corner1 = corner(null, first);
        Location corner2 = corner(null, second);
        Blueprint blueprint = new Blueprint(corner1, corner2);
        JsonObject size = json.getAsJsonObject("size");
        if (size != null) {
            blueprint.size(size.get("x").getAsInt(), size.get("y").getAsInt(), size.get("z").getAsInt());
        }
        if (json.get("blocks") instanceof JsonArray blocks) {
            for (int i = 0; i < blocks.size(); i++) {
                JsonObject block = blocks.get(i).getAsJsonObject();
                try {
                    BlockData data = Bukkit.createBlockData(block.get("data").getAsString());
                    blueprint.getBlocks().put(new Blueprint.Pos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt()), data);
                } catch (Exception e) {
                    plugin.getLogger().warning("Blueprint block: " + e.getMessage());
                }
            }
        }
        return new Stored(blueprint, worldName);
    }

    public record Stored(Blueprint blueprint, String worldName) {
    }

    private static Location corner(World world, JsonObject json) {
        if (json == null) {
            return new Location(world, 0, 0, 0);
        }
        return new Location(world, json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
    }

    public CompletableFuture<Blueprint> capture(Blueprint blueprint) {
        Location first = blueprint.getCorner1();
        Location second = blueprint.getCorner2();
        World world = first.getWorld();
        if (world == null || second.getWorld() == null || !world.getUID().equals(second.getWorld().getUID())) {
            throw new IllegalArgumentException("Blueprint corners must be in the same world");
        }
        CompletableFuture<Blueprint> future = new CompletableFuture<>();

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int minY = Math.max(world.getMinHeight(), Math.min(first.getBlockY(), second.getBlockY()));
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(first.getBlockY(), second.getBlockY()));
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        blueprint.size(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        AtomicInteger remaining = new AtomicInteger((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        ConcurrentLinkedQueue<Map<Blueprint.Pos, BlockData>> parts = new ConcurrentLinkedQueue<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int cx = chunkX;
                int cz = chunkZ;
                plugin.getServer().getRegionScheduler().execute(plugin, world, cx, cz, () -> {
                    Map<Blueprint.Pos, BlockData> local = new HashMap<>();
                    try {
                        readChunk(world, cx, cz, minX, maxX, minY, maxY, minZ, maxZ, local);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Blueprint capture " + cx + "," + cz + ": " + e.getMessage());
                    } finally {
                        parts.add(local);
                        if (remaining.decrementAndGet() == 0) {
                            FoliaManager.runGlobal(() -> {
                                try {
                                    Map<Blueprint.Pos, BlockData> blocks = blueprint.getBlocks();
                                    blocks.clear();
                                    for (Map<Blueprint.Pos, BlockData> part : parts) {
                                        blocks.putAll(part);
                                    }
                                    future.complete(blueprint);
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            });
                        }
                    }
                });
            }
        }
        return future;
    }

    private void readChunk(World world, int chunkX, int chunkZ, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Map<Blueprint.Pos, BlockData> into) {
        int startX = Math.max(minX, chunkX << 4);
        int endX = Math.min(maxX, (chunkX << 4) + 15);
        int startZ = Math.max(minZ, chunkZ << 4);
        int endZ = Math.min(maxZ, (chunkZ << 4) + 15);
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockData data = world.getBlockAt(x, y, z).getBlockData();
                    if (data.getMaterial().isAir()) {
                        continue;
                    }
                    into.put(new Blueprint.Pos(x - minX, y - minY, z - minZ), data);
                }
            }
        }
    }

    public void paste(Blueprint blueprint, Location origin, Runnable done) {
        World world = origin.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Paste origin has no world");
        }

        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        int sizeX = blueprint.getSizeX();
        int sizeY = blueprint.getSizeY();
        int sizeZ = blueprint.getSizeZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            sizeX = Math.abs(blueprint.getCorner1().getBlockX() - blueprint.getCorner2().getBlockX()) + 1;
            sizeY = Math.abs(blueprint.getCorner1().getBlockY() - blueprint.getCorner2().getBlockY()) + 1;
            sizeZ = Math.abs(blueprint.getCorner1().getBlockZ() - blueprint.getCorner2().getBlockZ()) + 1;
        }
        int maxX = baseX + sizeX - 1;
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + sizeY - 1);
        int maxZ = baseZ + sizeZ - 1;
        int minY = Math.max(world.getMinHeight(), baseY);
        if (maxX < baseX || maxZ < baseZ || maxY < minY) {
            FoliaManager.runGlobal(done);
            return;
        }

        BlockData air = Material.AIR.createBlockData();
        Map<Blueprint.Pos, BlockData> blocks = blueprint.getBlocks();
        int minChunkX = baseX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = baseZ >> 4;
        int maxChunkZ = maxZ >> 4;
        AtomicInteger remaining = new AtomicInteger((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int cx = chunkX;
                int cz = chunkZ;
                plugin.getServer().getRegionScheduler().execute(plugin, world, cx, cz, () -> {
                    try {
                        int startX = Math.max(baseX, cx << 4);
                        int endX = Math.min(maxX, (cx << 4) + 15);
                        int startZ = Math.max(baseZ, cz << 4);
                        int endZ = Math.min(maxZ, (cz << 4) + 15);
                        for (int x = startX; x <= endX; x++) {
                            for (int z = startZ; z <= endZ; z++) {
                                for (int y = minY; y <= maxY; y++) {
                                    BlockData data = blocks.get(new Blueprint.Pos(x - baseX, y - baseY, z - baseZ));
                                    world.getBlockAt(x, y, z).setBlockData(data == null ? air : data.clone(), false);
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Blueprint paste " + cx + "," + cz + ": " + e.getMessage());
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            FoliaManager.runGlobal(done);
                        }
                    }
                });
            }
        }
    }

    public CompletableFuture<Void> delete(String name) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            try {
                synchronized (this) {
                    Files.deleteIfExists(file(name).toPath());
                    Map<String, JsonObject> updated = new HashMap<>(blueprints);
                    updated.remove(name);
                    blueprints = Map.copyOf(updated);
                }
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> save(String name, Blueprint blueprint) {
        return capture(blueprint).thenAcceptAsync(captured -> {
            try {
                write(name, captured);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private synchronized void write(String name, Blueprint blueprint) throws IOException {
        JsonObject json = new JsonObject();
        json.add("corner1", location(blueprint.getCorner1()));
        json.add("corner2", location(blueprint.getCorner2()));
        JsonObject size = new JsonObject();
        size.addProperty("x", blueprint.getSizeX());
        size.addProperty("y", blueprint.getSizeY());
        size.addProperty("z", blueprint.getSizeZ());
        json.add("size", size);

        JsonArray blocks = new JsonArray();
        for (Map.Entry<Blueprint.Pos, BlockData> entry : blueprint.getBlocks().entrySet()) {
            BlockData data = entry.getValue();
            if (data.getMaterial().isAir()) {
                continue;
            }
            Blueprint.Pos pos = entry.getKey();
            JsonObject block = new JsonObject();
            block.addProperty("x", pos.x());
            block.addProperty("y", pos.y());
            block.addProperty("z", pos.z());
            block.addProperty("data", data.getAsString());
            blocks.add(block);
        }
        json.add("blocks", blocks);
        save(name, json);
    }

    public synchronized void save(String name, JsonObject json) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file(name).toPath())) {
            gson.toJson(json, writer);
        }
        Map<String, JsonObject> updated = new HashMap<>(blueprints);
        updated.put(name, json);
        blueprints = Map.copyOf(updated);
    }

    private JsonObject location(Location location) {
        JsonObject json = new JsonObject();
        if (location.getWorld() != null) {
            json.addProperty("world", location.getWorld().getName());
        }
        json.addProperty("x", location.getBlockX());
        json.addProperty("y", location.getBlockY());
        json.addProperty("z", location.getBlockZ());
        return json;
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
