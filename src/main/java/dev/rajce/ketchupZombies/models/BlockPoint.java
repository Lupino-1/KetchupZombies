package dev.rajce.ketchupZombies.models;

import org.bukkit.Location;

public record BlockPoint(String world, int x, int y, int z) {

    public static BlockPoint of(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new BlockPoint(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String format() {
        return world + " " + x + " " + y + " " + z;
    }
}
