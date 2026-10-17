package dev.rajce.ketchupZombies.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public class Blueprint {

    private final Location corner1;
    private final Location corner2;
    private final Map<Pos, BlockData> blocks = new HashMap<>();
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
}
