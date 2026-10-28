package dev.rajce.ketchupZombies.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.Blueprint;
import dev.rajce.ketchupZombies.util.Banners;
import dev.rajce.ketchupZombies.util.Heads;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /** minX, minZ, sizeX, sizeZ from the blueprint file. Null when missing. */
    public synchronized int[] footprintNow(String name) {
        JsonObject json = blueprints.get(name);
        if (json == null) {
            File file = file(name);
            if (!file.isFile()) {
                return null;
            }
            try (Reader reader = Files.newBufferedReader(file.toPath())) {
                json = gson.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                plugin.getLogger().warning("Blueprint footprint " + name + ": " + e.getMessage());
                return null;
            }
        }
        if (json == null) {
            return null;
        }
        JsonObject first = json.getAsJsonObject("corner1");
        JsonObject second = json.getAsJsonObject("corner2");
        if (first == null || second == null || !first.has("x") || !second.has("x")) {
            return null;
        }
        int x1 = first.get("x").getAsInt();
        int z1 = first.get("z").getAsInt();
        int x2 = second.get("x").getAsInt();
        int z2 = second.get("z").getAsInt();
        JsonObject size = json.getAsJsonObject("size");
        int sizeX = size != null && size.has("x") ? size.get("x").getAsInt() : Math.abs(x1 - x2) + 1;
        int sizeZ = size != null && size.has("z") ? size.get("z").getAsInt() : Math.abs(z1 - z2) + 1;
        return new int[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(1, sizeX), Math.max(1, sizeZ)};
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
                    Blueprint.SkullSkin skull = readSkull(block);
                    Blueprint.BannerSkin banner = readBanner(block);
                    blueprint.getBlocks().put(
                            new Blueprint.Pos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt()),
                            new Blueprint.PlacedBlock(data, skull, banner)
                    );
                } catch (Exception e) {
                    plugin.getLogger().warning("Blueprint block: " + e.getMessage());
                }
            }
        }
        return new Stored(blueprint, worldName);
    }

    private static Blueprint.SkullSkin readSkull(JsonObject block) {
        if (!block.has("skull") || !block.get("skull").isJsonObject()) {
            return null;
        }
        JsonObject skull = block.getAsJsonObject("skull");
        if (!skull.has("textures")) {
            return null;
        }
        String textures = skull.get("textures").getAsString();
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        UUID id = null;
        if (skull.has("uuid")) {
            try {
                id = UUID.fromString(skull.get("uuid").getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (id == null) {
            id = UUID.nameUUIDFromBytes(("kz-skull:" + textures).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String name = skull.has("name") ? skull.get("name").getAsString() : "skull";
        String signature = skull.has("signature") ? skull.get("signature").getAsString() : null;
        return new Blueprint.SkullSkin(id, name, textures, signature);
    }

    private static Blueprint.BannerSkin readBanner(JsonObject block) {
        if (!block.has("banner") || !block.get("banner").isJsonObject()) {
            return null;
        }
        JsonObject banner = block.getAsJsonObject("banner");
        if (!(banner.get("patterns") instanceof JsonArray patterns)) {
            return new Blueprint.BannerSkin(List.of());
        }
        java.util.ArrayList<Blueprint.BannerLayer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            if (!patterns.get(i).isJsonObject()) {
                continue;
            }
            JsonObject layer = patterns.get(i).getAsJsonObject();
            if (!layer.has("color") || !layer.has("pattern")) {
                continue;
            }
            String color = layer.get("color").getAsString();
            String pattern = layer.get("pattern").getAsString();
            if (color == null || pattern == null || color.isBlank() || pattern.isBlank()) {
                continue;
            }
            layers.add(new Blueprint.BannerLayer(color, pattern));
        }
        return new Blueprint.BannerSkin(List.copyOf(layers));
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
        ConcurrentLinkedQueue<Map<Blueprint.Pos, Blueprint.PlacedBlock>> parts = new ConcurrentLinkedQueue<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int cx = chunkX;
                int cz = chunkZ;
                plugin.getServer().getRegionScheduler().execute(plugin, world, cx, cz, () -> {
                    Map<Blueprint.Pos, Blueprint.PlacedBlock> local = new HashMap<>();
                    try {
                        readChunk(world, cx, cz, minX, maxX, minY, maxY, minZ, maxZ, local);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Blueprint capture " + cx + "," + cz + ": " + e.getMessage());
                    } finally {
                        parts.add(local);
                        if (remaining.decrementAndGet() == 0) {
                            FoliaManager.runGlobal(() -> {
                                try {
                                    Map<Blueprint.Pos, Blueprint.PlacedBlock> blocks = blueprint.getBlocks();
                                    blocks.clear();
                                    for (Map<Blueprint.Pos, Blueprint.PlacedBlock> part : parts) {
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

    private void readChunk(World world, int chunkX, int chunkZ, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Map<Blueprint.Pos, Blueprint.PlacedBlock> into) {
        int startX = Math.max(minX, chunkX << 4);
        int endX = Math.min(maxX, (chunkX << 4) + 15);
        int startZ = Math.max(minZ, chunkZ << 4);
        int endZ = Math.min(maxZ, (chunkZ << 4) + 15);
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockData data = block.getBlockData();
                    if (data.getMaterial().isAir()) {
                        continue;
                    }
                    Blueprint.SkullSkin skull = null;
                    Blueprint.BannerSkin banner = null;
                    try {
                        skull = Heads.read(block);
                    } catch (Exception skullError) {
                        plugin.getLogger().warning("Blueprint skull read " + x + "," + y + "," + z + ": " + skullError.getMessage());
                    }
                    try {
                        banner = Banners.read(block);
                    } catch (Exception bannerError) {
                        plugin.getLogger().warning("Blueprint banner read " + x + "," + y + "," + z + ": " + bannerError.getMessage());
                    }
                    into.put(new Blueprint.Pos(x - minX, y - minY, z - minZ), new Blueprint.PlacedBlock(data, skull, banner));
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
        Map<Blueprint.Pos, Blueprint.PlacedBlock> blocks = blueprint.getBlocks();
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
                                    Blueprint.PlacedBlock placed = blocks.get(new Blueprint.Pos(x - baseX, y - baseY, z - baseZ));
                                    Block block = world.getBlockAt(x, y, z);
                                    if (placed == null) {
                                        block.setBlockData(air, false);
                                        continue;
                                    }
                                    block.setBlockData(placed.data().clone(), false);
                                    if (placed.skull() != null) {
                                        try {
                                            Heads.apply(block, placed.skull());
                                        } catch (Exception skullError) {
                                            plugin.getLogger().warning("Blueprint skull " + x + "," + y + "," + z + ": " + skullError.getMessage());
                                        }
                                    }
                                    if (placed.banner() != null) {
                                        try {
                                            Banners.apply(block, placed.banner());
                                        } catch (Exception bannerError) {
                                            plugin.getLogger().warning("Blueprint banner " + x + "," + y + "," + z + ": " + bannerError.getMessage());
                                        }
                                    }
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
        for (Map.Entry<Blueprint.Pos, Blueprint.PlacedBlock> entry : blueprint.getBlocks().entrySet()) {
            Blueprint.PlacedBlock placed = entry.getValue();
            BlockData data = placed.data();
            if (data.getMaterial().isAir()) {
                continue;
            }
            Blueprint.Pos pos = entry.getKey();
            JsonObject block = new JsonObject();
            block.addProperty("x", pos.x());
            block.addProperty("y", pos.y());
            block.addProperty("z", pos.z());
            block.addProperty("data", data.getAsString());
            if (placed.skull() != null) {
                JsonObject skull = new JsonObject();
                skull.addProperty("uuid", placed.skull().id().toString());
                skull.addProperty("name", placed.skull().name());
                skull.addProperty("textures", placed.skull().textures());
                if (placed.skull().signature() != null && !placed.skull().signature().isEmpty()) {
                    skull.addProperty("signature", placed.skull().signature());
                }
                block.add("skull", skull);
            }
            if (placed.banner() != null) {
                JsonObject banner = new JsonObject();
                JsonArray patterns = new JsonArray();
                for (Blueprint.BannerLayer layer : placed.banner().patterns()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("color", layer.color());
                    row.addProperty("pattern", layer.pattern());
                    patterns.add(row);
                }
                banner.add("patterns", patterns);
                block.add("banner", banner);
            }
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
