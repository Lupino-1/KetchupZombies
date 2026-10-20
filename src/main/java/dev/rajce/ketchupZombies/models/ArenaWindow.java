package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.List;

public final class ArenaWindow {

    private String size;
    private String section;
    private BlockPoint corner1;
    private BlockPoint corner2;
    private BlockPoint faceMin;
    private BlockPoint faceMax;
    private FinePoint spawn;
    private List<WindowBlock> blocks = new ArrayList<>();

    public String size() {
        return size;
    }

    public String section() {
        return section;
    }

    public void section(String section) {
        this.section = section;
    }

    public BlockPoint corner1() {
        return corner1;
    }

    public void corner1(BlockPoint corner1) {
        this.corner1 = corner1;
    }

    public BlockPoint corner2() {
        return corner2;
    }

    public void corner2(BlockPoint corner2) {
        this.corner2 = corner2;
    }

    public BlockPoint faceMin() {
        return faceMin;
    }

    public BlockPoint faceMax() {
        return faceMax;
    }

    public FinePoint spawn() {
        return spawn;
    }

    public void spawn(FinePoint spawn) {
        this.spawn = spawn;
    }

    public List<WindowBlock> blocks() {
        return blocks;
    }

    public void applyFace(String size, BlockPoint faceMin, BlockPoint faceMax, List<WindowBlock> blocks) {
        this.size = size;
        this.faceMin = faceMin;
        this.faceMax = faceMax;
        this.blocks = new ArrayList<>(blocks);
    }

    public void clearFace() {
        size = null;
        faceMin = null;
        faceMax = null;
        blocks = new ArrayList<>();
    }

    public ArenaWindow copy() {
        ArenaWindow copy = new ArenaWindow();
        copy.size = size;
        copy.section = section;
        copy.corner1 = corner1;
        copy.corner2 = corner2;
        copy.faceMin = faceMin;
        copy.faceMax = faceMax;
        copy.spawn = spawn;
        copy.blocks = new ArrayList<>(blocks);
        return copy;
    }

    public String format() {
        String text = size == null ? "No area" : size;
        if (section != null) {
            text += " in " + section;
        }
        if (spawn != null) {
            text += " spawn " + spawn.format();
        }
        return text;
    }
}
