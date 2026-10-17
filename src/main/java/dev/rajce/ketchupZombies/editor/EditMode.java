package dev.rajce.ketchupZombies.editor;

public enum EditMode {
    NONE,
    NAME,
    MARK,
    DOOR_BLOCKS;

    public boolean marksBlock() {
        return this == MARK;
    }

    public boolean placesDoor() {
        return this == DOOR_BLOCKS;
    }
}
