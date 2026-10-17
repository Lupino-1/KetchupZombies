package dev.rajce.ketchupZombies.models;

import org.bukkit.Location;

public record ArenaSection(String world, int x1, int y1, int z1, int x2, int y2, int z2) {

    public static ArenaSection of(Location first, Location second) {
        BlockPoint a = BlockPoint.of(first);
        BlockPoint b = BlockPoint.of(second);
        return new ArenaSection(
                a.world(),
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()),
                Math.max(a.z(), b.z())
        );
    }

    public String format() {
        return world + " " + x1 + " " + y1 + " " + z1 + " -> " + x2 + " " + y2 + " " + z2;
    }
}
