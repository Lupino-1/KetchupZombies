package dev.rajce.ketchupZombies.models;

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
    private final Map<SpotKind, List<BlockPoint>> spots = new EnumMap<>(SpotKind.class);

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

    public List<BlockPoint> spots(SpotKind kind) {
        return spots.get(kind);
    }

    public ArenaData copy() {
        ArenaData copy = new ArenaData(name);
        copy.sections.putAll(sections);
        copy.doors.putAll(doors);
        copy.windows.addAll(windows);
        for (SpotKind kind : SpotKind.values()) {
            copy.spots.get(kind).addAll(spots.get(kind));
        }
        return copy;
    }
}
