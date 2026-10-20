package dev.rajce.ketchupZombies.managers;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.editor.Names;
import dev.rajce.ketchupZombies.models.ArenaData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class ArenaCopies {

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private final ArenaFiles arenas;
    private final InstanceFiles instances;
    private final HologramManager holograms;

    public ArenaCopies(KetchupZombies plugin, BlueprintManager blueprints, ArenaFiles arenas, InstanceFiles instances, HologramManager holograms) {
        this.plugin = plugin;
        this.blueprints = blueprints;
        this.arenas = arenas;
        this.instances = instances;
        this.holograms = holograms;
    }

    public void copy(Player player, String name) {
        if (!Names.valid(name)) {
            reply(player, "&cName must be 1-32 letters and numbers.");
            return;
        }
        plugin.getMessageManager().send(player, "copy.pasting");
        arenas.load(name).thenCompose(data -> blueprints.load(name).thenApply(stored -> new Loaded(data, stored))).whenComplete((loaded, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || loaded == null || loaded.data == null || loaded.stored == null) {
                if (error != null) {
                    plugin.getLogger().warning("Arena copy " + name + ": " + error.getMessage());
                }
                reply(player, "&cThat arena does not exist.");
                return;
            }
            FoliaManager.runAsync(() -> reserveAndPaste(player, name, loaded));
        }));
    }

    private void reserveAndPaste(Player player, String name, Loaded loaded) {
        int index;
        try {
            index = instances.reserveNow(name);
        } catch (IOException e) {
            plugin.getLogger().warning("Arena copy " + name + ": " + e.getMessage());
            FoliaManager.runAtEntity(player, () -> {
                if (player.isOnline()) {
                    reply(player, "&cCould not save that copy. Check console.");
                }
            });
            return;
        }
        FoliaManager.runAtEntity(player, () -> paste(player, loaded, index));
    }

    private void paste(Player player, Loaded loaded, int index) {
        if (!player.isOnline()) {
            return;
        }
        World world = plugin.getServer().getWorld(loaded.stored.worldName());
        Location first = loaded.stored.blueprint().getCorner1();
        Location second = loaded.stored.blueprint().getCorner2();
        if (world == null) {
            reply(player, "&cThat world is not loaded.");
            return;
        }
        first.setWorld(world);
        second.setWorld(world);
        int offsetX = plugin.config().copies.offsetX;
        int offsetZ = plugin.config().copies.offsetZ;
        Location origin = origin(loaded, index, offsetX, offsetZ);
        blueprints.paste(loaded.stored.blueprint(), origin, () -> {
            holograms.showArena(loaded.data, index);
            FoliaManager.runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getMessageManager().send(player, "copy.ready", Map.of(
                        "index", Integer.toString(index),
                        "x", Integer.toString(origin.getBlockX()),
                        "y", Integer.toString(origin.getBlockY()),
                        "z", Integer.toString(origin.getBlockZ())
                ));
            });
        });
    }

    public void refresh(Player player, String name) {
        if (!Names.valid(name)) {
            reply(player, "&cName must be 1-32 letters and numbers.");
            return;
        }
        plugin.getMessageManager().send(player, "copy.refresh");
        load(player, name, loaded -> pasteChain(player, loaded));
    }

    public void recapture(Player player, String name) {
        if (!Names.valid(name)) {
            reply(player, "&cName must be 1-32 letters and numbers.");
            return;
        }
        plugin.getMessageManager().send(player, "copy.recapture");
        blueprints.load(name).whenComplete((stored, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || stored == null) {
                if (error != null) {
                    plugin.getLogger().warning("Arena recapture " + name + ": " + error.getMessage());
                }
                reply(player, "&cThat arena does not exist.");
                return;
            }
            World world = plugin.getServer().getWorld(stored.worldName());
            if (world == null) {
                reply(player, "&cThat world is not loaded.");
                return;
            }
            stored.blueprint().getCorner1().setWorld(world);
            stored.blueprint().getCorner2().setWorld(world);
            dev.rajce.ketchupZombies.models.Blueprint fresh = new dev.rajce.ketchupZombies.models.Blueprint(stored.blueprint().getCorner1(), stored.blueprint().getCorner2());
            blueprints.save(name, fresh).whenComplete((ignored, saveError) -> FoliaManager.runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (saveError != null) {
                    plugin.getLogger().warning("Arena recapture " + name + ": " + saveError.getMessage());
                    reply(player, "&cCould not save that blueprint. Check console.");
                    return;
                }
                plugin.getMessageManager().send(player, "copy.refresh");
                load(player, name, loaded -> pasteChain(player, loaded));
            }));
        }));
    }

    private void load(Player player, String name, java.util.function.Consumer<Loaded> then) {
        arenas.load(name).thenCompose(data -> blueprints.load(name).thenApply(stored -> new Loaded(data, stored))).whenComplete((loaded, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || loaded == null || loaded.data == null || loaded.stored == null) {
                if (error != null) {
                    plugin.getLogger().warning("Arena copy " + name + ": " + error.getMessage());
                }
                reply(player, "&cThat arena does not exist.");
                return;
            }
            then.accept(loaded);
        }));
    }

    private void pasteChain(Player player, Loaded loaded) {
        FoliaManager.runAsync(() -> {
            List<Integer> indexes = instances.indexesNow(loaded.data.name());
            FoliaManager.runAtEntity(player, () -> pasteAt(player, loaded, indexes, 0));
        });
    }

    private void pasteAt(Player player, Loaded loaded, List<Integer> indexes, int cursor) {
        if (!player.isOnline()) {
            return;
        }
        if (cursor >= indexes.size()) {
            holograms.showArena(loaded.data, 0);
            for (int index : indexes) {
                holograms.showArena(loaded.data, index);
            }
            plugin.getMessageManager().send(player, "copy.refreshed", Map.of("count", Integer.toString(indexes.size())));
            return;
        }
        World world = plugin.getServer().getWorld(loaded.stored.worldName());
        if (world == null) {
            reply(player, "&cThat world is not loaded.");
            return;
        }
        loaded.stored.blueprint().getCorner1().setWorld(world);
        loaded.stored.blueprint().getCorner2().setWorld(world);
        int index = indexes.get(cursor);
        Location origin = origin(loaded, index, plugin.config().copies.offsetX, plugin.config().copies.offsetZ);
        blueprints.paste(loaded.stored.blueprint(), origin, () -> FoliaManager.runAtEntity(player, () -> pasteAt(player, loaded, indexes, cursor + 1)));
    }

    private Location origin(Loaded loaded, int index, int offsetX, int offsetZ) {
        Location first = loaded.stored.blueprint().getCorner1();
        Location second = loaded.stored.blueprint().getCorner2();
        int x = Math.min(first.getBlockX(), second.getBlockX()) + index * offsetX;
        int y = Math.min(first.getBlockY(), second.getBlockY());
        int z = Math.min(first.getBlockZ(), second.getBlockZ()) + index * offsetZ;
        return new Location(first.getWorld(), x, y, z);
    }

    public void playCopy(Player player, String name, java.util.function.BiConsumer<Integer, ArenaData> ready, Runnable failed) {
        plugin.getMessageManager().send(player, "join.preparing");
        FoliaManager.runAsync(() -> {
            int index;
            try {
                index = instances.claimNow(name);
                if (index < 0) {
                    index = instances.reserveBusyNow(name);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Arena play " + name + ": " + e.getMessage());
                FoliaManager.runAtEntity(player, () -> {
                    if (player.isOnline()) {
                        reply(player, "&cCould not save that copy. Check console.");
                    }
                    failed.run();
                });
                return;
            }
            int claimed = index;
            arenas.load(name).thenCompose(data -> blueprints.load(name).thenApply(stored -> new Loaded(data, stored))).whenComplete((loaded, error) -> {
                if (error != null || loaded == null || loaded.data == null || loaded.stored == null) {
                    if (error != null) {
                        plugin.getLogger().warning("Arena play " + name + ": " + error.getMessage());
                    }
                    release(name, claimed);
                    FoliaManager.runAtEntity(player, () -> {
                        if (player.isOnline()) {
                            reply(player, "&cThat arena does not exist.");
                        }
                        failed.run();
                    });
                    return;
                }
                if (loaded.data.sections().isEmpty()) {
                    release(name, claimed);
                    FoliaManager.runAtEntity(player, () -> {
                        if (player.isOnline()) {
                            reply(player, "&cThat arena has no section.");
                        }
                        failed.run();
                    });
                    return;
                }
                FoliaManager.runAtEntity(player, () -> {
                    if (!player.isOnline()) {
                        release(name, claimed);
                        failed.run();
                        return;
                    }
                    World world = plugin.getServer().getWorld(loaded.stored.worldName());
                    if (world == null) {
                        reply(player, "&cThat world is not loaded.");
                        release(name, claimed);
                        failed.run();
                        return;
                    }
                    loaded.stored.blueprint().getCorner1().setWorld(world);
                    loaded.stored.blueprint().getCorner2().setWorld(world);
                    Location origin = origin(loaded, claimed, plugin.config().copies.offsetX, plugin.config().copies.offsetZ);
                    blueprints.paste(loaded.stored.blueprint(), origin, () -> {
                        holograms.showArena(loaded.data, claimed);
                        FoliaManager.runAtEntity(player, () -> {
                            if (!player.isOnline()) {
                                release(name, claimed);
                                failed.run();
                                return;
                            }
                            ready.accept(claimed, loaded.data);
                        });
                    });
                });
            });
        });
    }

    public void reset(String name, int index) {
        arenas.load(name).thenCompose(data -> blueprints.load(name).thenApply(stored -> new Loaded(data, stored))).whenComplete((loaded, error) -> {
            if (error != null || loaded == null || loaded.stored == null) {
                release(name, index);
                return;
            }
            World world = plugin.getServer().getWorld(loaded.stored.worldName());
            if (world == null) {
                release(name, index);
                return;
            }
            loaded.stored.blueprint().getCorner1().setWorld(world);
            loaded.stored.blueprint().getCorner2().setWorld(world);
            Location origin = origin(loaded, index, plugin.config().copies.offsetX, plugin.config().copies.offsetZ);
            blueprints.paste(loaded.stored.blueprint(), origin, () -> {
                if (loaded.data != null) {
                    holograms.showArena(loaded.data, index);
                }
                release(name, index);
            });
        });
    }

    private void release(String name, int index) {
        FoliaManager.runAsync(() -> {
            try {
                instances.freeNow(name, index);
            } catch (IOException e) {
                plugin.getLogger().warning("Arena free " + name + ": " + e.getMessage());
            }
        });
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }

    private record Loaded(ArenaData data, BlueprintManager.Stored stored) {
    }
}
