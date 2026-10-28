package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One wall armor shop: up to one piece per slot, one price for the whole set. */
public final class ArmorOffer {

    public static final List<String> SLOT_ORDER = List.of("helmet", "chestplate", "leggings", "boots");

    private final Map<String, String> slots;
    private final int price;

    public ArmorOffer(Map<String, String> slots, int price) {
        Map<String, String> copy = new LinkedHashMap<>();
        for (String slot : SLOT_ORDER) {
            String set = slots == null ? null : slots.get(slot);
            if (set != null && !set.isBlank()) {
                copy.put(slot, set.toLowerCase(Locale.ROOT));
            }
        }
        this.slots = Map.copyOf(copy);
        this.price = price;
    }

    /** Legacy: one material set for listed pieces. */
    public ArmorOffer(String set, List<String> pieces, int price) {
        Map<String, String> map = new LinkedHashMap<>();
        String material = set == null ? "leather" : set.toLowerCase(Locale.ROOT);
        if (pieces != null) {
            for (String piece : pieces) {
                if (piece == null || piece.isBlank()) {
                    continue;
                }
                String slot = piece.trim().toLowerCase(Locale.ROOT);
                if (SLOT_ORDER.contains(slot)) {
                    map.put(slot, material);
                }
            }
        }
        this.slots = Map.copyOf(map);
        this.price = price;
    }

    public Map<String, String> slots() {
        return slots;
    }

    public int price() {
        return price;
    }

    public String set(String slot) {
        return slots.get(slot);
    }

    /** First material in slot order — used for icons / legacy callers. */
    public String set() {
        for (String slot : SLOT_ORDER) {
            String set = slots.get(slot);
            if (set != null) {
                return set;
            }
        }
        return "leather";
    }

    public List<String> pieces() {
        List<String> list = new ArrayList<>();
        for (String slot : SLOT_ORDER) {
            if (slots.containsKey(slot)) {
                list.add(slot);
            }
        }
        return list;
    }

    public boolean empty() {
        return slots.isEmpty();
    }

    public ArmorOffer withSlot(String slot, String set) {
        Map<String, String> next = new LinkedHashMap<>(slots);
        String key = slot == null ? "" : slot.toLowerCase(Locale.ROOT);
        if (!SLOT_ORDER.contains(key)) {
            return this;
        }
        if (set == null || set.isBlank()) {
            next.remove(key);
        } else {
            next.put(key, set.toLowerCase(Locale.ROOT));
        }
        return new ArmorOffer(next, price);
    }

    public ArmorOffer withPrice(int price) {
        return new ArmorOffer(slots, price);
    }

    public ArmorOffer copy() {
        return new ArmorOffer(slots, price);
    }

    public String label() {
        if (slots.isEmpty()) {
            return "Armor";
        }
        List<String> parts = new ArrayList<>();
        for (String slot : SLOT_ORDER) {
            String set = slots.get(slot);
            if (set != null) {
                parts.add(set + " " + slot);
            }
        }
        return String.join(", ", parts);
    }
}
