package dev.rajce.ketchupZombies.weapons;

import org.bukkit.Material;

public enum WeaponId {
    KNIFE("knife", "Knife", Material.IRON_SWORD, true),
    PISTOL("pistol", "Pistol", Material.WOODEN_HOE, false),
    RIFLE("rifle", "Blaster rifle", Material.STONE_HOE, false),
    SHOTGUN("shotgun", "Blaster shotgun", Material.IRON_HOE, false),
    SNIPER("sniper", "Sniper blaster", Material.WOODEN_SHOVEL, false),
    ROCKET("rocket", "Rocket launcher", Material.STONE_SHOVEL, false),
    FLAMETHROWER("flamethrower", "Flamethrower", Material.GOLDEN_HOE, false),
    DART("dart", "Blow dart", Material.IRON_SHOVEL, false),
    SOAKER("soaker", "Zombie soaker", Material.DIAMOND_HOE, false),
    ZAPPER("zapper", "Zombie zapper", Material.DIAMOND_PICKAXE, false),
    PUNCHER("puncher", "Puncher", Material.DIAMOND_AXE, true),
    ELDER("elder", "Elder gun", Material.SHEARS, false),
    DIGGER("digger", "Gold digger", Material.GOLDEN_PICKAXE, false);

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

    /** Hotbar slot 0: knife, or Puncher when it replaces the knife. */
    public boolean meleeSlot() {
        return this == KNIFE || this == PUNCHER;
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
