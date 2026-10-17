package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.List;

public final class ArenaDoor {

    private final String from;
    private final String to;
    private int priceFrom;
    private int priceTo;
    private BlockPoint hologram;
    private final List<BlockPoint> blocks = new ArrayList<>();

    public ArenaDoor(String from, String to, int priceFrom, int priceTo) {
        this.from = from;
        this.to = to;
        this.priceFrom = priceFrom;
        this.priceTo = priceTo;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public int priceFrom() {
        return priceFrom;
    }

    public int priceTo() {
        return priceTo;
    }

    public BlockPoint hologram() {
        return hologram;
    }

    public void hologram(BlockPoint hologram) {
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

    public String key() {
        return from + "|" + to;
    }

    public String format() {
        return from + " -> " + to;
    }
}
