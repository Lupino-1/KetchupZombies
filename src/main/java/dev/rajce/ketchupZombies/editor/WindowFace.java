package dev.rajce.ketchupZombies.editor;

import dev.rajce.ketchupZombies.models.BlockPoint;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public final class WindowFace {

    private WindowFace() {
    }

    public static String sizeOf(Location first, Location second) {
        if (matches(first, second, "2x2")) {
            return "2x2";
        }
        if (matches(first, second, "2x3")) {
            return "2x3";
        }
        return null;
    }

    public static boolean matches(Location first, Location second, String size) {
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return false;
        }
        int dx = Math.abs(first.getBlockX() - second.getBlockX()) + 1;
        int dy = Math.abs(first.getBlockY() - second.getBlockY()) + 1;
        int dz = Math.abs(first.getBlockZ() - second.getBlockZ()) + 1;
        int[] dims = sorted(dx, dy, dz);
        if (dims[0] != 1) {
            return false;
        }
        if ("2x2".equals(size)) {
            return dims[1] == 2 && dims[2] == 2;
        }
        return "2x3".equals(size) && dims[1] == 2 && dims[2] == 3;
    }

    public static BlockPoint min(Location first, Location second) {
        return new BlockPoint(
                first.getWorld().getName(),
                Math.min(first.getBlockX(), second.getBlockX()),
                Math.min(first.getBlockY(), second.getBlockY()),
                Math.min(first.getBlockZ(), second.getBlockZ())
        );
    }

    public static BlockPoint max(Location first, Location second) {
        return new BlockPoint(
                first.getWorld().getName(),
                Math.max(first.getBlockX(), second.getBlockX()),
                Math.max(first.getBlockY(), second.getBlockY()),
                Math.max(first.getBlockZ(), second.getBlockZ())
        );
    }

    public static List<BlockPoint> positions(BlockPoint min, BlockPoint max) {
        List<BlockPoint> positions = new ArrayList<>();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    positions.add(new BlockPoint(min.world(), x, y, z));
                }
            }
        }
        return positions;
    }

    private static int[] sorted(int a, int b, int c) {
        int[] dims = {a, b, c};
        java.util.Arrays.sort(dims);
        return dims;
    }
}
