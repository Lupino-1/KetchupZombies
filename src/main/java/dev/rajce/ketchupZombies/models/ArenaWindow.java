package dev.rajce.ketchupZombies.models;

import java.util.List;

public record ArenaWindow(String size, String section, BlockPoint faceMin, BlockPoint faceMax, BlockPoint spawn, List<WindowBlock> blocks) {

    public ArenaWindow {
        blocks = List.copyOf(blocks);
    }

    public String format() {
        String text = size + " in " + section;
        if (spawn != null) {
            text += " spawn " + spawn.format();
        }
        return text;
    }
}
