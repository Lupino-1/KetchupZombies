package dev.rajce.ketchupZombies.models;

import org.bukkit.Location;

import java.math.BigDecimal;

public record FinePoint(String world, double x, double y, double z) {

    public FinePoint {
        x = round(x);
        y = round(y);
        z = round(z);
    }

    public static FinePoint of(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new FinePoint(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
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
