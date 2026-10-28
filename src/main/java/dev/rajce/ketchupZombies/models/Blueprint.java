package dev.rajce.ketchupZombies.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Getter
public class Blueprint {

    private final Location corner1;
    private final Location corner2;
    private final Map<Pos, PlacedBlock> blocks = new HashMap<>();
    private int sizeX;
    private int sizeY;
    private int sizeZ;

    public void size(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public record Pos(int x, int y, int z) {
    }

    /** BlockData plus optional skull textures and/or banner patterns. */
    public record PlacedBlock(BlockData data, SkullSkin skull, BannerSkin banner) {
        public static PlacedBlock of(BlockData data) {
            return new PlacedBlock(data, null, null);
        }

        public PlacedBlock(BlockData data, SkullSkin skull) {
            this(data, skull, null);
        }
    }

    public record SkullSkin(UUID id, String name, String textures, String signature) {
    }

    /** Crafted banner layers (color + pattern identifier). Empty list = plain banner. */
    public record BannerSkin(List<BannerLayer> patterns) {
    }

    public record BannerLayer(String color, String pattern) {
    }
}
