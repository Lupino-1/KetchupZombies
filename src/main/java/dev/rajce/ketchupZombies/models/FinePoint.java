package dev.rajce.ketchupZombies.models;

import org.bukkit.Location;

import java.math.BigDecimal;

public record FinePoint(String world, double x, double y, double z, float yaw, float pitch, Boolean fixed) {

    public FinePoint(String world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f, null);
    }

    public FinePoint {
        x = round(x);
        y = round(y);
        z = round(z);
    }

    public static FinePoint of(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new FinePoint(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                null
        );
    }

    /** Default true when unset in arena YML. */
    public boolean billboardFixed() {
        return fixed == null || fixed;
    }

    public FinePoint withFixed(Boolean fixed) {
        return new FinePoint(world, x, y, z, yaw, pitch, fixed);
    }

    public FinePoint shifted(double dx, double dy, double dz) {
        return new FinePoint(world, x + dx, y + dy, z + dz, yaw, pitch, fixed);
    }

    public String format() {
        return world + " " + text(x) + " " + text(y) + " " + text(z);
    }

    public String key() {
        return world + ":" + text(x) + ":" + text(y) + ":" + text(z);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String text(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
