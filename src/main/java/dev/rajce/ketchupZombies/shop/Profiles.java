package dev.rajce.ketchupZombies.shop;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Profiles {

    public static final String FAST_REVIVE = "fast-revive";
    public static final String SPEED = "speed";
    public static final String QUICK_FIRE = "quick-fire";
    public static final String EXTRA_HEALTH = "extra-health";
    public static final String EXTRA_WEAPON = "extra-weapon";
    public static final String FROZEN_BULLETS = "frozen-bullets";
    public static final String FLAME_BULLETS = "flame-bullets";

    private final ConcurrentHashMap<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public int gold(UUID id) {
        return profile(id).gold;
    }

    public void gold(UUID id, int gold) {
        profile(id).gold = Math.max(0, gold);
    }

    public void addGold(UUID id, int amount) {
        Profile profile = profile(id);
        profile.gold = Math.max(0, profile.gold + amount);
    }

    public boolean takeGold(UUID id, int price) {
        Profile profile = profile(id);
        if (profile.gold < price) {
            return false;
        }
        profile.gold -= price;
        return true;
    }

    public boolean power(UUID id) {
        return profile(id).power;
    }

    public void power(UUID id, boolean power) {
        profile(id).power = power;
    }

    public Set<String> perks(UUID id) {
        return Set.copyOf(profile(id).perks);
    }

    public boolean perk(UUID id, String perk) {
        return profile(id).perks.contains(perk);
    }

    public boolean addPerk(UUID id, String perk) {
        Profile profile = profile(id);
        if (profile.perks.contains(perk)) {
            return true;
        }
        if (profile.perks.size() >= 3) {
            return false;
        }
        profile.perks.add(perk);
        return true;
    }

    public void clear(UUID id) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            return;
        }
        profile.perks.clear();
        profile.power = false;
    }

    private Profile profile(UUID id) {
        return profiles.computeIfAbsent(id, ignored -> new Profile());
    }

    private static final class Profile {
        private int gold;
        private boolean power;
        private final Set<String> perks = ConcurrentHashMap.newKeySet();
    }
}
