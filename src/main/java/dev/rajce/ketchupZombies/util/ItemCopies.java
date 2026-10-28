package dev.rajce.ketchupZombies.util;

import org.bukkit.inventory.ItemStack;

/** Deep copies of inventory arrays — shallow {@code getContents().clone()} mirrors go air after clear. */
public final class ItemCopies {

    private ItemCopies() {
    }

    public static ItemStack[] of(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            ItemStack item = source[i];
            copy[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        return copy;
    }
}
