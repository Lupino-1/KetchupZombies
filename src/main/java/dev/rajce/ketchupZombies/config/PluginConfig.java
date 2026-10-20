package dev.rajce.ketchupZombies.config;

import dev.lupino1.config.Comment;
import dev.lupino1.config.ConfigKey;
import dev.lupino1.config.Header;
import dev.lupino1.config.Naming;
import dev.lupino1.config.NamingStrategy;
import dev.lupino1.config.YamlConfig;

@Header({
        "KetchupZombies",
        "Prices and stats. Arena files store places, door prices and custom armor offers.",
        "Perk and armor prices are the Dead End shop prices."
})
@Naming(NamingStrategy.KEBAB)
public final class PluginConfig extends YamlConfig {

    public Copies copies = new Copies();
    public Database database = new Database();
    public Zombies zombies = new Zombies();
    public Shot shot = new Shot();
    @ConfigKey("lucky-chest")
    public LuckyChest luckyChest = new LuckyChest();
    public Upgrade upgrade = new Upgrade();
    public Power power = new Power();
    public Perks perks = new Perks();
    public Armor armor = new Armor();
    public Weapons weapons = new Weapons();

    @Naming(NamingStrategy.KEBAB)
    public static final class Copies extends YamlConfig {

        @Comment("Blocks from the blueprint minimum corner to copy 1 on X. Copy n uses n times this. Index 0 is never played. 400 keeps the next copy out of view.")
        @ConfigKey("offset-x")
        public int offsetX = 400;

        @Comment("Blocks from the blueprint minimum corner to copy 1 on Z. Y is not shifted.")
        @ConfigKey("offset-z")
        public int offsetZ = 400;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Database extends YamlConfig {

        @Comment("MariaDB. Scores are written async. Placeholders read a memory snapshot.")
        public boolean enabled = true;
        public String host = "127.0.0.1";
        public int port = 3306;
        public String name = "ketchupzombies";
        public String username = "root";
        public String password = "";
        @ConfigKey("pool-size")
        public int poolSize = 4;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Zombies extends YamlConfig {

        @Comment("A window spawns zombies only while a player is within this many blocks of its spawn. 3D distance.")
        @ConfigKey("spawn-distance")
        public int spawnDistance = 40;

        @Comment("Seconds between each barrier block a zombie removes from a window.")
        @ConfigKey("window-break-seconds")
        public double windowBreakSeconds = 2.5;

        @Comment("Seconds to wait after a wave is fully cleared before the next wave (or round) starts. Hypixel Dead End is about 10s+.")
        @ConfigKey("wave-clear-delay-seconds")
        public double waveClearDelaySeconds = 10;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Shot extends YamlConfig {

        @Comment("A shot stops at this range. Wallshots stop here too.")
        public int range = 64;

        @Comment("How far off the ray a mob body may still count as a hit, in blocks. Lower is stricter. 0.2 needs aim near the hitbox.")
        @ConfigKey("hit-radius")
        public double hitRadius = 0.2;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class LuckyChest extends YamlConfig {

        @Comment("Price of one pull. Chest weapons have no own price.")
        public int price = 1000;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Upgrade extends YamlConfig {

        @Comment("Price of one weapon upgrade. Gold digger uses this price for each of its five levels.")
        public int price = 1500;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Power extends YamlConfig {

        @Comment("Gold to flip the switch. Dead End does not charge, so this stays 0.")
        public int price = 0;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Perks extends YamlConfig {

        @Comment("Dead End prices. Perks work only after the power switch. A player can hold three.")
        @ConfigKey("fast-revive")
        public int fastRevive = 500;
        public int speed = 500;
        @ConfigKey("quick-fire")
        public int quickFire = 1000;
        @ConfigKey("extra-health")
        public int extraHealth = 1000;
        @ConfigKey("extra-weapon")
        public int extraWeapon = 1500;
        @ConfigKey("frozen-bullets")
        public int frozenBullets = 1000;
        @ConfigKey("flame-bullets")
        public int flameBullets = 750;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Armor extends YamlConfig {

        @Comment("Dead End sells these bundles. A shop can replace them with its own pieces and price.")
        @ConfigKey("leather-top")
        public Bundle leatherTop = new Bundle(50, "leather", "helmet,chestplate");
        @ConfigKey("leather-bottom")
        public Bundle leatherBottom = new Bundle(100, "leather", "leggings,boots");
        @ConfigKey("gold-top")
        public Bundle goldTop = new Bundle(500, "gold", "helmet,chestplate");
        @ConfigKey("iron-top")
        public Bundle ironTop = new Bundle(800, "iron", "helmet,chestplate");
        @ConfigKey("iron-bottom")
        public Bundle ironBottom = new Bundle(700, "iron", "leggings,boots");
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Bundle extends YamlConfig {

        public int price;
        public String set = "leather";
        @Comment("Comma separated: helmet, chestplate, leggings, boots.")
        public String pieces = "helmet";

        public Bundle() {
        }

        public Bundle(int price, String set, String pieces) {
            this.price = price;
            this.set = set;
            this.pieces = pieces;
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Weapons extends YamlConfig {

        public Weapon knife = new Weapon(0, 9, 6, -1, -1, 1.2, -1, 0, 0);
        public Weapon pistol = new Weapon(0, 11, 6, 10, 300, 0.5, 1.5, 0, 0);
        public Weapon rifle = new Weapon(500, 8, 6, 32, 288, 0.2, 1.5, 0, 0);
        public Weapon shotgun = new Weapon(500, 7, 4.5, 5, 65, 1.4, 1.5, 10, 0);
        public Weapon sniper = new Weapon(700, 28, 30, 4, 40, 1.0, 2.0, 0, 0);
        @ConfigKey("rocket-launcher")
        public Weapon rocketLauncher = new Weapon(900, 21, 15, 4, 20, 2.0, 3.0, 0, 0);
        public Weapon flamethrower = new Weapon(-1, 4, 2, 50, 350, 0.1, 3.0, 0, 0);
        @ConfigKey("blow-dart")
        public Weapon blowDart = new Weapon(-1, 18, 10, 10, 100, 0.5, 3.0, 0, 0);
        @ConfigKey("zombie-soaker")
        public Weapon zombieSoaker = new Weapon(-1, 6, 5, 32, 256, 0.25, 1.5, 0, 0);
        @ConfigKey("zombie-zapper")
        public Weapon zombieZapper = new Weapon(-1, 14, 12, 10, 100, 0.5, 2.0, 0, 4);
        public Weapon puncher = new Weapon(-1, 11, 6, -1, -1, 1.2, -1, 0, 3);
        @ConfigKey("elder-gun")
        public Weapon elderGun = new Weapon(-1, 22, 15, 4, 60, 0.9, 2.0, 0, 0);
        @ConfigKey("gold-digger")
        public GoldDigger goldDigger = new GoldDigger();
    }

    @Naming(NamingStrategy.KEBAB)
    public static class Weapon extends YamlConfig {

        @Comment("Shop price. -1 means the lucky chest, not the weapon shop. 0 is free.")
        public int price;
        @Comment("Gold for one hit. A critical hit is 150 percent, rounded up. Shotgun gold is for one pellet.")
        public int gold;
        @Comment("Damage of one shot, in HP, before upgrades.")
        public double damage;
        @Comment("Rounds in one magazine. -1 means the weapon has no ammo.")
        public int magazine;
        @Comment("Reserve ammo. -1 means the weapon has no ammo.")
        public int ammo;
        @Comment("Seconds between shots. For the knife and the puncher this is the swing cooldown.")
        public double delay;
        @Comment("Reload seconds. -1 means the weapon does not reload.")
        public double reload;
        @Comment("Pellets per shot. 0 means one projectile.")
        public int pellets;
        @Comment("Extra targets in one shot. 0 means one target.")
        public int targets;

        public Weapon() {
        }

        public Weapon(int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets) {
            this.price = price;
            this.gold = gold;
            this.damage = damage;
            this.magazine = magazine;
            this.ammo = ammo;
            this.delay = delay;
            this.reload = reload;
            this.pellets = pellets;
            this.targets = targets;
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class GoldDigger extends Weapon {

        @Comment("Upgrade steps after the base stats. Each step costs upgrade.price.")
        public int levels = 5;
        @ConfigKey("level-1")
        public GunLevel level1 = new GunLevel(8, 10, 100, 0.5, 1.4);
        @ConfigKey("level-2")
        public GunLevel level2 = new GunLevel(10, 13, 130, 0.4, 1.3);
        @ConfigKey("level-3")
        public GunLevel level3 = new GunLevel(12, 16, 160, 0.3, 1.2);
        @ConfigKey("level-4")
        public GunLevel level4 = new GunLevel(15, 20, 200, 0.3, 1.1);
        @ConfigKey("level-5")
        public GunLevel level5 = new GunLevel(20, 25, 250, 0.2, 1.0);

        public GoldDigger() {
            super(-1, 9, 6, 7, 70, 0.5, 1.5, 0, 0);
        }

        public GunLevel level(int step) {
            return switch (step) {
                case 1 -> level1;
                case 2 -> level2;
                case 3 -> level3;
                case 4 -> level4;
                case 5 -> level5;
                default -> null;
            };
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class GunLevel extends YamlConfig {

        public double damage;
        public int magazine;
        public int ammo;
        public double delay;
        public double reload;

        public GunLevel() {
        }

        public GunLevel(double damage, int magazine, int ammo, double delay, double reload) {
            this.damage = damage;
            this.magazine = magazine;
            this.ammo = ammo;
            this.delay = delay;
            this.reload = reload;
        }
    }
}
