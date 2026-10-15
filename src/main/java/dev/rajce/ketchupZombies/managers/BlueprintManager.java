package dev.rajce.ketchupZombies.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.Blueprint;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        Map<Long, List<PasteBlock>> byChunk = new HashMap<>();
        for (Map.Entry<Blueprint.Pos, BlockData> entry : blueprint.getBlocks().entrySet()) {
            Blueprint.Pos pos = entry.getKey();
            int x = baseX + pos.x();
            int y = baseY + pos.y();
            int z = baseZ + pos.z();
            long key = chunkKey(x >> 4, z >> 4);
            byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new PasteBlock(x, y, z, entry.getValue().clone()));
        }

        if (byChunk.isEmpty()) {
            FoliaManager.runGlobal(done);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(byChunk.size());
        for (Map.Entry<Long, List<PasteBlock>> chunk : byChunk.entrySet()) {
            int chunkX = (int) (chunk.getKey() >> 32);
            int chunkZ = (int) chunk.getKey().longValue();
            List<PasteBlock> blocks = chunk.getValue();
            plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                try {
                    for (PasteBlock block : blocks) {
                        if (block.y < world.getMinHeight() || block.y >= world.getMaxHeight()) {
                            continue;
                        }
                        world.getBlockAt(block.x, block.y, block.z).setBlockData(block.data, false);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Blueprint paste " + chunkX + "," + chunkZ + ": " + e.getMessage());
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        FoliaManager.runGlobal(done);
                    }
                }
            });
        }
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private record PasteBlock(int x, int y, int z, BlockData data) {
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
