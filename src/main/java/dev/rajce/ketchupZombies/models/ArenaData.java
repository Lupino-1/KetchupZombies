package dev.rajce.ketchupZombies.models;

import dev.rajce.ketchupZombies.weapons.WeaponId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaData {

    private final String name;
    private final Map<String, ArenaSection> sections = new LinkedHashMap<>();
    private final Map<String, ArenaDoor> doors = new LinkedHashMap<>();
    private final List<ArenaWindow> windows = new ArrayList<>();
    private final Map<SpotKind, List<FinePoint>> spots = new EnumMap<>(SpotKind.class);
    private final Map<String, List<ArmorOffer>> shopArmor = new LinkedHashMap<>();
    private final Map<String, List<WeaponId>> shopWeapons = new LinkedHashMap<>();

    public ArenaData(String name) {
        this.name = name;
        for (SpotKind kind : SpotKind.values()) {
            spots.put(kind, new ArrayList<>());
        }
    }

    public String name() {
        return name;
    }

    public Map<String, ArenaSection> sections() {
        return sections;
    }

    public Map<String, ArenaDoor> doors() {
        return doors;
    }

    public List<ArenaWindow> windows() {
        return windows;
    }

    public List<FinePoint> spots(SpotKind kind) {
        return spots.get(kind);
    }

    public static String spotKey(FinePoint point) {
        return point.key();
    }

    public boolean customArmor(FinePoint point) {
        return shopArmor.containsKey(spotKey(point));
    }

    public List<ArmorOffer> armorOffers(FinePoint point) {
        return shopArmor.getOrDefault(spotKey(point), List.of());
    }

    public void armorOffers(FinePoint point, List<ArmorOffer> offers) {
        List<ArmorOffer> copy = new ArrayList<>();
        for (ArmorOffer offer : offers) {
            copy.add(offer.copy());
        }
        shopArmor.put(spotKey(point), copy);
    }

    public void defaultArmor(FinePoint point) {
        shopArmor.remove(spotKey(point));
    }

    public boolean customWeapons(FinePoint point) {
        return shopWeapons.containsKey(spotKey(point));
    }

    public List<WeaponId> weaponOffers(FinePoint point) {
        return shopWeapons.getOrDefault(spotKey(point), List.of());
    }

    public void weaponOffers(FinePoint point, List<WeaponId> weapons) {
        shopWeapons.put(spotKey(point), List.copyOf(weapons));
    }

    public void defaultWeapons(FinePoint point) {
        shopWeapons.remove(spotKey(point));
    }

    public void forgetSpot(FinePoint point) {
        shopArmor.remove(spotKey(point));
        shopWeapons.remove(spotKey(point));
    }

    public ArenaData copy() {
        ArenaData copy = new ArenaData(name);
        copy.sections.putAll(sections);
        for (ArenaDoor door : doors.values()) {
            ArenaDoor cloned = door.copy();
            copy.doors.put(cloned.key(), cloned);
        }
        for (ArenaWindow window : windows) {
            copy.windows.add(window.copy());
        }
        for (SpotKind kind : SpotKind.values()) {
            copy.spots.get(kind).addAll(spots.get(kind));
        }
        for (Map.Entry<String, List<ArmorOffer>> entry : shopArmor.entrySet()) {
            List<ArmorOffer> offers = new ArrayList<>();
            for (ArmorOffer offer : entry.getValue()) {
                offers.add(offer.copy());
            }
            copy.shopArmor.put(entry.getKey(), offers);
        }
        for (Map.Entry<String, List<WeaponId>> entry : shopWeapons.entrySet()) {
            copy.shopWeapons.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }
}
