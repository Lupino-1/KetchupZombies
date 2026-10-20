package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.List;

public final class ArenaDoor {

    private final String from;
    private final String to;
    private Integer price;
    private FinePoint hologramA;
    private FinePoint hologramB;
    private final List<BlockPoint> blocks = new ArrayList<>();

    public ArenaDoor(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public Integer price() {
        return price;
    }

    public void price(Integer price) {
        this.price = price;
    }

    public FinePoint hologramA() {
        return hologramA;
    }

    public void hologramA(FinePoint hologramA) {
        this.hologramA = hologramA;
    }

    public FinePoint hologramB() {
        return hologramB;
    }

    public void hologramB(FinePoint hologramB) {
        this.hologramB = hologramB;
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

    public ArenaDoor copy() {
        ArenaDoor copy = new ArenaDoor(from, to);
        copy.price = price;
        copy.hologramA = hologramA;
        copy.hologramB = hologramB;
        copy.blocks.addAll(blocks);
        return copy;
    }

    public String key() {
        return from + "|" + to;
    }

    public String format() {
        return from + " -> " + to;
    }
}
