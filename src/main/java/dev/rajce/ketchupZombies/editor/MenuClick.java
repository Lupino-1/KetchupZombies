package dev.rajce.ketchupZombies.editor;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum MenuClick {
    LEFT,
    RIGHT,
    OTHER;

    public static MenuClick of(InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            return LEFT;
        }
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            return RIGHT;
        }
        return OTHER;
    }
}
