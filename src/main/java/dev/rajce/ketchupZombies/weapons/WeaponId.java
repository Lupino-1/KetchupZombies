package dev.rajce.ketchupZombies.weapons;

import org.bukkit.Material;

public enum WeaponId {
    KNIFE("knife", "Knife", Material.IRON_SWORD, true),
    PISTOL("pistol", "Pistol", Material.WOODEN_HOE, false),
    RIFLE("rifle", "Blaster rifle", Material.STONE_HOE, false),
    SHOTGUN("shotgun", "Blaster shotgun", Material.IRON_HOE, false),
    SNIPER("sniper", "Sniper blaster", Material.DIAMOND_HOE, false),
    ROCKET("rocket", "Rocket launcher", Material.GOLDEN_HOE, false),
    FLAMETHROWER("flamethrower", "Flamethrower", Material.BLAZE_ROD, false),
    DART("dart", "Blow dart", Material.SPECTRAL_ARROW, false),
    SOAKER("soaker", "Zombie soaker", Material.BUCKET, false),
    ZAPPER("zapper", "Zombie zapper", Material.END_ROD, false),
    PUNCHER("puncher", "Puncher", Material.MACE, true),
    ELDER("elder", "Elder gun", Material.PRISMARINE_SHARD, false),
    DIGGER("digger", "Gold digger", Material.NETHERITE_HOE, false);

    private final String id;
    private final String title;
    private final Material material;
    private final boolean melee;

    WeaponId(String id, String title, Material material, boolean melee) {
        this.id = id;
        this.title = title;
        this.material = material;
        this.melee = melee;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Material material() {
        return material;
    }

    public boolean melee() {
        return melee;
    }

    public boolean wallshot() {
        return !melee;
    }

    public boolean crits() {
        return this != ROCKET;
    }

    public boolean piercesMobs() {
        return this == SNIPER;
    }

    public boolean piercesAfterWallshot() {
        return this == ELDER;
    }

    public boolean explodes() {
        return this == ROCKET;
    }

    public boolean burns() {
        return this == FLAMETHROWER;
    }

    public boolean poison() {
        return this == DART;
    }

    public boolean soaks() {
        return this == SOAKER;
    }

    public boolean chains() {
        return this == ZAPPER;
    }

    public boolean knocks() {
        return this == PUNCHER;
    }

    public boolean stuns() {
        return this == ELDER;
    }

    public static WeaponId byId(String text) {
        if (text == null) {
            return null;
        }
        for (WeaponId weapon : values()) {
            if (weapon.id.equalsIgnoreCase(text)) {
                return weapon;
            }
        }
        return null;
    }
}
