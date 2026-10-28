package dev.rajce.ketchupZombies.editor;

public enum EditMode {
    NONE,
    NAME,
    PRICE,
    ARMOR_PRICE,
    MARK,
    DOOR_BLOCKS,
    MACHINE_BLOCKS;

    public boolean marksBlock() {
        return this == MARK;
    }

    public boolean placesDoor() {
        return this == DOOR_BLOCKS || this == MACHINE_BLOCKS;
    }
}
