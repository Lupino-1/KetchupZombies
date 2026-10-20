package dev.rajce.ketchupZombies.managers;

import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;

public final class HologramManager {

    private final KetchupZombies plugin;
    private final ArenaFiles arenas;
    private final InstanceFiles instances;
    private final NamespacedKey key;

    public HologramManager(KetchupZombies plugin, ArenaFiles arenas, InstanceFiles instances) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.instances = instances;
        key = new NamespacedKey(plugin, "hologram");
    }

    public void showSaved() {
        FoliaManager.runAsync(() -> {
            for (String name : arenas.listNow()) {
                ArenaData data = arenas.readNow(name);
                if (data == null) {
                    continue;
                }
                showArena(data, 0);
                for (int index : instances.indexesNow(name)) {
                    showArena(data, index);
                }
            }
        });
    }

    public void showArena(ArenaData data, int index) {
        for (SpotKind kind : SpotKind.values()) {
            for (FinePoint point : data.spots(kind)) {
                showSpot(data.name(), index, kind, point);
            }
        }
        for (ArenaDoor door : data.doors().values()) {
            showDoor(data.name(), index, door.price(), door.hologramA());
            showDoor(data.name(), index, door.price(), door.hologramB());
        }
    }

    public void clearKnown(ArenaData data) {
        FoliaManager.runAsync(() -> {
            clearArena(data, 0);
            for (int index : instances.indexesNow(data.name())) {
                clearArena(data, index);
            }
        });
    }

    public void clearArena(ArenaData data, int index) {
        for (SpotKind kind : SpotKind.values()) {
            for (FinePoint point : data.spots(kind)) {
                clear(data.name(), index, point);
            }
        }
        for (ArenaDoor door : data.doors().values()) {
            clear(data.name(), index, door.hologramA());
            clear(data.name(), index, door.hologramB());
        }
    }

    public void showSpotAll(String arena, SpotKind kind, FinePoint point) {
        FoliaManager.runAsync(() -> {
            showSpot(arena, 0, kind, point);
            for (int index : instances.indexesNow(arena)) {
                showSpot(arena, index, kind, point);
            }
        });
    }

    public void showDoorAll(String arena, Integer price, FinePoint point) {
        FoliaManager.runAsync(() -> {
            showDoor(arena, 0, price, point);
            for (int index : instances.indexesNow(arena)) {
                showDoor(arena, index, price, point);
            }
        });
    }

    public void clearAll(String arena, FinePoint point) {
        FoliaManager.runAsync(() -> {
            clear(arena, 0, point);
            for (int index : instances.indexesNow(arena)) {
                clear(arena, index, point);
            }
        });
    }

    public void showSpot(String arena, int index, SpotKind kind, FinePoint point) {
        show(arena, index, point, plugin.getMessageManager().get("hologram." + kind.key(), false));
    }

    public void showDoor(String arena, int index, Integer price, FinePoint point) {
        Component text = price == null
                ? plugin.getMessageManager().get("hologram.door", false)
                : plugin.getMessageManager().get("hologram.door-price", Map.of("price", Integer.toString(price)), false);
        show(arena, index, point, text);
    }

    public String tag(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return null;
        }
        return display.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public Entity looking(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Entity best = null;
        double bestDot = 0.82;
        double bestDistance = range * range;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (tag(entity) == null) {
                continue;
            }
            Vector to = entity.getLocation().toVector().subtract(eye.toVector());
            double distance = to.lengthSquared();
            if (distance < 0.01 || distance > bestDistance) {
                continue;
            }
            double dot = look.dot(to.normalize());
            if (dot >= bestDot) {
                bestDot = dot;
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    public void clear(String arena, int index, FinePoint point) {
        FinePoint shifted = shift(point, index);
        if (shifted == null) {
            return;
        }
        at(shifted, location -> remove(location, tag(arena, index)));
    }

    private void show(String arena, int index, FinePoint point, Component text) {
        FinePoint shifted = shift(point, index);
        if (shifted == null || text == null) {
            return;
        }
        String tag = tag(arena, index);
        at(shifted, location -> {
            remove(location, tag);
            location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.text(text);
                display.setSeeThrough(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setPersistent(true);
                display.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
            });
        });
    }

    private void remove(Location location, String tag) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 0.8, 2.0, 0.8)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            if (tag.equals(display.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                display.remove();
            }
        }
    }

    private void at(FinePoint point, java.util.function.Consumer<Location> action) {
        World world = plugin.getServer().getWorld(point.world());
        if (world == null) {
            return;
        }
        Location location = new Location(world, point.x(), point.y(), point.z());
        FoliaManager.runAtLocation(location, () -> action.accept(location));
    }

    private FinePoint shift(FinePoint point, int index) {
        if (point == null) {
            return null;
        }
        if (index == 0) {
            return point;
        }
        int offsetX = plugin.config().copies.offsetX;
        int offsetZ = plugin.config().copies.offsetZ;
        return new FinePoint(point.world(), point.x() + index * offsetX, point.y(), point.z() + index * offsetZ);
    }

    private static String tag(String arena, int index) {
        return arena + ":" + index;
    }
}
