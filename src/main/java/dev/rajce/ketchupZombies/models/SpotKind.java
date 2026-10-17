package dev.rajce.ketchupZombies.models;

import org.bukkit.Material;

public enum SpotKind {
    WEAPONS("weapons", "Weapon shops", Material.GOLD_INGOT),
    LUCKY_CHESTS("lucky-chests", "Lucky chests", Material.CHEST),
    UPGRADERS("upgraders", "Weapon upgraders", Material.ANVIL),
    PERKS("perks", "Perks", Material.EXPERIENCE_BOTTLE),
    POWER("power", "Power switch", Material.LEVER);

    private final String key;
    private final String title;
    private final Material icon;

    SpotKind(String key, String title, Material icon) {
        this.key = key;
        this.title = title;
        this.icon = icon;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public Material icon() {
        return icon;
    }
}
