package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.List;

/** One ultimate station: hologram plus the blocks that count as the machine. */
public final class UltimateMachine {

    private final String id;
    private FinePoint hologram;
    private final List<BlockPoint> blocks = new ArrayList<>();

    public UltimateMachine(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public FinePoint hologram() {
        return hologram;
    }

    public void hologram(FinePoint hologram) {
        this.hologram = hologram;
    }

    public List<BlockPoint> blocks() {
        return blocks;
    }

    public boolean addBlock(BlockPoint point) {
        if (blocks.contains(point)) {
            return false;
        }
        blocks.add(point);
        return true;
    }

    public void clearBlocks() {
        blocks.clear();
    }

    public UltimateMachine copy() {
        UltimateMachine copy = new UltimateMachine(id);
        copy.hologram = hologram;
        copy.blocks.addAll(blocks);
        return copy;
    }
}
