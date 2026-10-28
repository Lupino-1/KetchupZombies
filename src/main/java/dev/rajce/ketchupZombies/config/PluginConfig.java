package dev.rajce.ketchupZombies.config;

import dev.lupino1.config.Comment;
import dev.lupino1.config.ConfigKey;
import dev.lupino1.config.Header;
import dev.lupino1.config.Naming;
import dev.lupino1.config.NamingStrategy;
import dev.lupino1.config.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Map;

@Header({
        "KetchupZombies",
        "Prices and stats. Arena files store places, door prices and custom armor offers.",
        "Perk and armor prices are the Dead End shop prices."
})
@Naming(NamingStrategy.KEBAB)
public final class PluginConfig extends YamlConfig {

    public Copies copies = new Copies();
    public Party party = new Party();
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
    public Shops shops = new Shops();
    public Scoreboard scoreboard = new Scoreboard();
    @ConfigKey("hub-scoreboard")
    public HubScoreboard hubScoreboard = new HubScoreboard();
    @ConfigKey("lobby-scoreboard")
    public LobbyScoreboard lobbyScoreboard = new LobbyScoreboard();
    @ConfigKey("pre-game")
    public PreGame preGame = new PreGame();
    public Titles titles = new Titles();
    public Sounds sounds = new Sounds();
    @Comment("Default hologram text lines per spot type. Placeholders: %item% %price% %refill% %section% (door destination). /kz reload reshowns.")
    public Holograms holograms = new Holograms();
    @ConfigKey("weapon-lore")
    @Comment("Labels on gun item lore. A line is omitted when that stat does not apply (e.g. pellets only if pellets > 1).")
    public WeaponLore weaponLore = new WeaponLore();
    @ConfigKey("hub-spawn")
    @Comment("Set by /kz setspawn. Until set, players use the spawn of world. Land here on join, after a match, and via /spawn.")
    public HubSpawn hubSpawn = new HubSpawn();

    @Naming(NamingStrategy.KEBAB)
    public static final class Party extends YamlConfig {

        @Comment("Seconds a member may stay offline before they are removed from the party. The leader is removed on disconnect and the party is passed to another member.")
        @ConfigKey("offline-seconds")
        public double offlineSeconds = 60;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class WeaponLore extends YamlConfig {
        public String damage = "Poškození";
        public String ammo = "Náboje";
        public String clip = "Zásobník";
        @ConfigKey("fire-rate")
        public String fireRate = "Kadence";
        public String reload = "Nabíjení";
        public String gold = "Zlato";
        public String pellets = "Broky";
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class HubSpawn extends YamlConfig {
        public boolean set = false;
        public String world = "world";
        public double x = 0.5;
        public double y = 64;
        public double z = 0.5;
        public float yaw = 0;
        public float pitch = 0;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Holograms extends YamlConfig {

        @Comment("Multi-weapon shop / empty stock.")
        public java.util.List<String> weapons = java.util.List.of("&6Weapon shop");

        @ConfigKey("weapon-buy")
        @Comment("Single wall gun. Placeholders: %item% %price%.")
        public java.util.List<String> weaponBuy = java.util.List.of("&e%item%", "&6Cost: %price%");

        @ConfigKey("weapon-refill")
        @Comment("Appended under weapon-buy when the gun has ammo. Placeholder: %refill%.")
        public java.util.List<String> weaponRefill = java.util.List.of("&7Refill: %refill%");

        @Comment("Multi-armor shop / empty stock.")
        public java.util.List<String> armor = java.util.List.of("&7Armor shop");

        @ConfigKey("armor-buy")
        @Comment("Single armor offer. Placeholders: %item% %price%.")
        public java.util.List<String> armorBuy = java.util.List.of("&e%item%", "&6Cost: %price%");

        @ConfigKey("lucky-chests")
        public java.util.List<String> luckyChests = java.util.List.of("&eLucky Chest", "&6%price% Gold");

        public java.util.List<String> upgraders = java.util.List.of("&bWeapon Upgrader", "&6Cost: %price%");

        public java.util.List<String> perks = java.util.List.of("&aPerk Machine");

        @Comment("Legacy key. The live lines are power-buy and power-bought.")
        public java.util.List<String> power = java.util.List.of("&cPower Switch");

        @ConfigKey("power-buy")
        @Comment("Power hologram before it is bought. Placeholder: %price%.")
        public java.util.List<String> powerBuy = java.util.List.of("&cPower", "&6Buy for %price%");

        @ConfigKey("power-bought")
        @Comment("Power hologram after it is bought.")
        public java.util.List<String> powerBought = java.util.List.of("&aPower", "&aPurchased");

        @Comment("Door with no price. Placeholder: %section% = destination section.")
        public java.util.List<String> door = java.util.List.of("&7Door", "&8→ %section%");

        @ConfigKey("door-price")
        @Comment("Priced door. Placeholders: %price% %section% (destination section on that side).")
        public java.util.List<String> doorPrice = java.util.List.of("&6Door", "&e%price% Gold", "&7→ %section%");

        public java.util.List<String> lines(String key) {
            return switch (key) {
                case "weapons" -> weapons;
                case "weapon-buy" -> weaponBuy;
                case "weapon-refill" -> weaponRefill;
                case "armor" -> armor;
                case "armor-buy" -> armorBuy;
                case "lucky-chests" -> luckyChests;
                case "upgraders" -> upgraders;
                case "perks" -> perks;
                case "power-buy" -> powerBuy;
                case "power-bought" -> powerBought;
                case "door" -> door;
                case "door-price" -> doorPrice;
                default -> java.util.List.of("&c" + key);
            };
        }
    }

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
        public String name = "minecraft";
        public String username = "root";
        public String password = "admin";
        @ConfigKey("pool-size")
        public int poolSize = 4;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Zombies extends YamlConfig {

        @Comment("A window spawns zombies only while a player is within this many blocks of its spawn. 3D distance.")
        @ConfigKey("spawn-distance")
        public int spawnDistance = 40;

        @Comment("Multiplies round-book movement speed for classic zombies. Lower = slower.")
        @ConfigKey("speed-scale")
        public double speedScale = 0.82;

        @Comment("FOLLOW_RANGE attribute floor so zombies sense players through walls (blocks).")
        @ConfigKey("follow-range")
        public double followRange = 100;

        @Comment("Seconds between each barrier block a zombie removes from a window.")
        @ConfigKey("window-break-seconds")
        public double windowBreakSeconds = 2.5;

        @Comment("Seconds a player must hold sneak inside the window repair zone to restore one barrier block. Leave zone or unsneak resets.")
        @ConfigKey("window-repair-seconds")
        public double windowRepairSeconds = 1.0;

        @Comment("Unused. Repair uses the per-window repair-zone corners in the arena file.")
        @ConfigKey("window-repair-distance")
        public double windowRepairDistance = 2.0;

        @Comment("Gold for one repaired barrier block.")
        @ConfigKey("window-repair-gold")
        public int windowRepairGold = 10;

        @Comment("Dot product threshold for a zombie looking at a barrier (1 = dead ahead, 0 = side). ~0.35 is a wide forward cone.")
        @ConfigKey("window-break-dot")
        public double windowBreakDot = 0.35;

        @Comment("Seconds a zombie may stand still before its player target is assigned again. No velocity shove.")
        @ConfigKey("stuck-seconds")
        public double stuckSeconds = 4;

        @Comment("Blocks. A zombie closer than this shifts the path goal so the horde does not share one block. 0 disables.")
        @ConfigKey("crowd-distance")
        public double crowdDistance = 0.96;

        @Comment("Blocks the path goal moves away from that neighbor.")
        @ConfigKey("crowd-apart")
        public double crowdApart = 1.08;

        @Comment("Legacy single delay. Used only when wave-delays has no entry for the round.")
        @ConfigKey("wave-clear-delay-seconds")
        public double waveClearDelaySeconds = 10;

        @Comment("Cumulative seconds from round start when each wave spawns. First is always 10 on Hypixel Dead End.")
        @ConfigKey("wave-delays")
        public Map<String, String> waveDelays = defaultWaveDelays();

        @Comment("Fallback cumulative seconds when the round is missing from wave-delays.")
        @ConfigKey("wave-delay-fallback")
        public String waveDelayFallback = "10,20,35";

        private static Map<String, String> defaultWaveDelays() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("1", "10,20");
            map.put("2", "10,20");
            map.put("3", "10,20,35");
            map.put("4", "10,20,35");
            map.put("5", "10,22,37");
            map.put("6", "10,22,44");
            map.put("7", "10,25,47");
            map.put("8", "10,25,50");
            map.put("9", "10,22,38");
            map.put("10", "10,24,45");
            map.put("11", "10,25,48");
            map.put("12", "10,25,50");
            map.put("13", "10,25,50");
            map.put("14", "10,25,45");
            map.put("15", "10,25,46");
            map.put("16", "10,24,47");
            map.put("17", "10,24,47");
            map.put("18", "10,24,47");
            map.put("19", "10,24,47");
            map.put("20", "10,24,49");
            map.put("21", "10,23,44");
            map.put("22", "10,23,45");
            map.put("23", "10,23,42");
            map.put("24", "10,23,43");
            map.put("25", "10,23,43");
            map.put("26", "10,23,36");
            map.put("27", "10,24,44");
            map.put("28", "10,24,42");
            map.put("29", "10,24,42");
            map.put("30", "10,24,45");
            return map;
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Titles extends YamlConfig {

        public RoundTitle round = new RoundTitle();
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class RoundTitle extends YamlConfig {

        @Comment("sendTitle main line. Placeholder %round%.")
        public String title = "&e&lKolo %round%";

        @Comment("sendTitle subtitle. Empty = none.")
        public String subtitle = "";

        @Comment("Fade-in ticks.")
        @ConfigKey("fade-in")
        public int fadeIn = 10;

        @Comment("Stay ticks.")
        public int stay = 40;

        @Comment("Fade-out ticks.")
        @ConfigKey("fade-out")
        public int fadeOut = 10;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Sounds extends YamlConfig {

        @Comment("When a player starts holding sneak in a repair zone. Same door hit as window-repair by default.")
        @ConfigKey("window-repair-start")
        public SoundFx windowRepairStart = sound("ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1f, 1.0f);

        @Comment("When one barrier board is restored. Played once to the repairing player.")
        @ConfigKey("window-repair")
        public SoundFx windowRepair = sound("ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1f, 1.0f);

        @ConfigKey("window-break")
        public SoundFx windowBreak = sound("ENTITY_ZOMBIE_BREAK_WOODEN_DOOR", 1f, 1.0f);

        @ConfigKey("shop-buy")
        public SoundFx shopBuy = sound("ENTITY_EXPERIENCE_ORB_PICKUP", 1f, 1.2f);

        @Comment("Door purchase. Experience orb pickup.")
        @ConfigKey("door-buy")
        public SoundFx doorBuy = sound("ENTITY_EXPERIENCE_ORB_PICKUP", 1f, 1.2f);

        @ConfigKey("ammo-refill")
        public SoundFx ammoRefill = sound("ITEM_BOTTLE_FILL", 1f, 1.2f);

        @Comment("Played with the round title. Wither shoot/release by default.")
        @ConfigKey("round-start")
        public SoundFx roundStart = sound("ENTITY_WITHER_SPAWN", 1.0f, 1.0f);

        @Comment("Pre-game lobby countdown tick (each second while counting).")
        @ConfigKey("lobby-tick")
        public SoundFx lobbyTick = sound("BLOCK_NOTE_BLOCK_HAT", 0.4f, 1.6f);

        @Comment("When the match actually starts after the lobby countdown.")
        @ConfigKey("lobby-go")
        public SoundFx lobbyGo = sound("ENTITY_PLAYER_LEVELUP", 0.7f, 1.2f);

        private static SoundFx sound(String name, float volume, float pitch) {
            SoundFx fx = new SoundFx();
            fx.sound = name;
            fx.volume = volume;
            fx.pitch = pitch;
            return fx;
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class SoundFx extends YamlConfig {

        @Comment("Bukkit Sound enum name, e.g. BLOCK_WOOD_PLACE or ENTITY_WITHER_SPAWN.")
        public String sound = "UI_BUTTON_CLICK";
        @Comment("Volume 0–1+.")
        public float volume = 1f;
        @Comment("Pitch 0.5–2. Change this to retune the same sound.")
        public float pitch = 1f;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class PreGame extends YamlConfig {

        @Comment("Seconds after the lobby is full (or solo join) before the match starts. Time to leave.")
        @ConfigKey("countdown-seconds")
        public int countdownSeconds = 15;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Shot extends YamlConfig {

        @Comment("A shot stops at this range. Wallshots stop here too.")
        public int range = 64;

        @Comment("How far off the ray a mob body may still count as a hit, in blocks. Lower is stricter. 0.2 needs aim near the hitbox.")
        @ConfigKey("hit-radius")
        public double hitRadius = 0.3;

        @Comment("Hit counts as a headshot when the impact Y is at or above this fraction of the mob height. Higher = smaller head. 0.88 is about 85-90 percent.")
        @ConfigKey("headshot-height")
        public double headshotHeight = 0.88;

        @Comment("Vanilla sound when a shot or melee hits a mob. Same for every gun.")
        @ConfigKey("hit-sound")
        public String hitSound = "ENTITY_ARROW_HIT_PLAYER";

        @Comment("Volume of the hit sound.")
        @ConfigKey("hit-volume")
        public float hitVolume = 0.7f;

        @Comment("Pitch of the hit sound. Below 1 is lower than a normal bow ding.")
        @ConfigKey("hit-pitch")
        public float hitPitch = 0.8f;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class LuckyChest extends YamlConfig {

        @Comment("Price of one pull. Chest weapons have no own price.")
        public int price = 1000;

        @Comment("Y offset of the Lucky Chest text above the chest block center. 1.35 sits just over the lid.")
        @ConfigKey("hologram-offset-y")
        public double hologramOffsetY = 1.35;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Upgrade extends YamlConfig {

        @Comment("Price of one weapon upgrade. Gold digger uses this price for each of its five levels.")
        public int price = 1500;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Shops extends YamlConfig {

        @Comment("Gold to refill ammo at a wall weapon you already own. Hypixel wall guns use 400.")
        @ConfigKey("ammo-refill")
        public int ammoRefill = 400;

        @Comment("Seconds after going down before DEAD. No revive. Perks are lost. Respawn next round.")
        @ConfigKey("dead-seconds")
        public double deadSeconds = 25;

        @Comment("Seconds a teammate must hold sneak in range to revive. Hypixel default ~1.5.")
        @ConfigKey("revive-seconds")
        public double reviveSeconds = 1.5;

        @Comment("Revive hold with Fast Revive perk. Hypixel ~0.3.")
        @ConfigKey("fast-revive-seconds")
        public double fastReviveSeconds = 0.3;

        @Comment("Blocks from downed player (feet) for revive range and prompt.")
        @ConfigKey("revive-range")
        public double reviveRange = 2.5;
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class Scoreboard extends YamlConfig {

        @Comment("Sidebar title. Placeholders: %round% %zombies% %kills% %time% %area%")
        public String title = "&f";

        @Comment("Lines top to bottom. Empty = blank. Put %players% once; it expands to one player-line per teammate.")
        public java.util.List<String> lines = java.util.List.of(
                "",
                "",
                "&#F18F0E&lKolo %round%",
                "&7• <font:rajce:rajcefont>#FEF4E7Zbývající zombíci:</font> &#F18F0E%zombies%",
                "",
                "&7 - %players%",
                "",
                "&7• <font:rajce:rajcefont>#FEF4E7Killy:</font> &#F18F0E%kills%",
                "&7• <font:rajce:rajcefont>#FEF4E7Čas:</font> &#F18F0E%time%",
                "",
                "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>"
        );

        @Comment("Copied once per player. Placeholders: %player% %gold% %status%")
        @ConfigKey("player-line")
        public String playerLine = "&#FEF4E7%player%: %status%";

        @Comment("Value of %status% while alive. Placeholders: %player% %gold%")
        @ConfigKey("status-alive")
        public String statusAlive = "&#F18F0E%gold%";

        @Comment("Value of %status% while downed and revivable.")
        @ConfigKey("status-revive")
        public String statusRevive = "&#FFFB00Oživit";

        @Comment("Value of %status% after the dead timeout.")
        @ConfigKey("status-dead")
        public String statusDead = "&#E61A1AMrtvý";

        @Comment("Value of %status% when the player left mid-game.")
        @ConfigKey("status-quit")
        public String statusQuit = "&#E61A1AOdešel";
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class HubScoreboard extends YamlConfig {

        @Comment("Hub sidebar (not in arena). Title = scoreboard.title. Soft PAPI. Same footer as match.")
        public java.util.List<String> lines = java.util.List.of(
                "",
                "",
                "&#F18F0E&lLobby",
                "&7• <font:rajce:rajcefont>#FEF4E7Player:</font> &#F18F0E%player_name%",
                "",
                "&7Example — edit hub-scoreboard.lines",
                "",
                "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>"
        );
    }

    @Naming(NamingStrategy.KEBAB)
    public static final class LobbyScoreboard extends YamlConfig {

        @Comment("Arena start-lobby sidebar (queue + countdown). Title = scoreboard.title. Placeholders: %time% (seconds or —), %count%, %needed%.")
        public java.util.List<String> lines = java.util.List.of(
                "",
                "",
                "&#F18F0E&lStart",
                "&7• <font:rajce:rajcefont>#FEF4E7Start:</font> &#F18F0E%time%s",
                "&7• <font:rajce:rajcefont>#FEF4E7Hráči:</font> &#F18F0E%count%/%needed%",
                "",
                "      <font:rajce:rajcefont>&#F18F0E&lmc.Rajce.pro</font>"
        );
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

        public Weapon knife = model(gun("Nůž", "Ultimate Nůž", 0, 9, 6, -1, -1, 0, -1, 0, 0, new GunLevel(9, -1, -1, 0, -1, -1, -1), "ENTITY_PLAYER_ATTACK_SWEEP", 1f, 1f), 37500);
        public Weapon pistol = model(gun("Pistole", "Ultimate Pistole", 0, 11, 6, 10, 300, 0.5, 1.5, 0, 0, new GunLevel(6, 14, 450, 0.4, 1.0, 2, -1), "UI_BUTTON_CLICK", 0.8f, 1.6f), 37501);
        public Weapon rifle = model(gun("Blasterová puška", "Ultimate Blasterová puška", 500, 8, 6, 32, 288, 0.2, 1.5, 0, 0, new GunLevel(8, 35, 315, 0.2, 1.0), "ENTITY_FIREWORK_ROCKET_BLAST", 0.7f, 1.35f), 37502);
        public Weapon shotgun = shotgunGun();
        public Weapon sniper = model(gun("Odstřelovací blaster", "Ultimate Odstřelovací blaster", 700, 28, 30, 4, 40, 1.0, 2.0, 0, 0, new GunLevel(40, 5, 60, 1.0, 1.5), "ENTITY_FIREWORK_ROCKET_LARGE_BLAST", 0.85f, 1.1f), 37504);
        @ConfigKey("rocket-launcher")
        public Weapon rocketLauncher = model(gun("Raketomet", "Ultimate Raketomet", 900, 21, 15, 4, 20, 2.0, 3.0, 0, 0, new GunLevel(20, 4, 32, 1.5, 2.0), "ENTITY_GENERIC_EXPLODE", 0.9f, 0.85f), 37505);
        public Weapon flamethrower = model(gun("Plamenomet", "Ultimate Plamenomet", -1, 4, 2, 50, 350, 0.1, 3.0, 0, 0, new GunLevel(2, 50, 500, 0.1, 1.8), "ITEM_FIRECHARGE_USE", 0.6f, 1.4f), 37506);
        @ConfigKey("blow-dart")
        public Weapon blowDart = model(gun("Jedomet", "Ultimate Jedomet", -1, 18, 10, 10, 100, 0.5, 3.0, 0, 0, new GunLevel(10, 10, 150, 0.3, 1.8), "ENTITY_ARROW_SHOOT", 0.8f, 1.5f), 37507);
        @ConfigKey("zombie-soaker")
        public Weapon zombieSoaker = model(gun("Vodomet", "Ultimate Vodomet", -1, 6, 5, 32, 256, 0.25, 1.5, 0, 0, new GunLevel(8, 32, 320, 0.2, 1.0), "ENTITY_GENERIC_SPLASH", 0.8f, 1.2f), 37508);
        @ConfigKey("zombie-zapper")
        public Weapon zombieZapper = model(gun("Paprskomet", "Ultimate Paprskomet", -1, 14, 12, 10, 100, 0.5, 2.0, 0, 4, new GunLevel(18, 10, 120, 0.5, 1.5, -1, 5), "ENTITY_LIGHTNING_BOLT_IMPACT", 0.45f, 1.6f), 37509);
        public Weapon puncher = model(gun("Palice", "Ultimate Palice", -1, 11, 6, -1, -1, 1.2, -1, 0, 3, new GunLevel(6, -1, -1, 1.2, -1, -1, 5), "ENTITY_PLAYER_ATTACK_KNOCKBACK", 1f, 0.9f), 37510);
        @ConfigKey("elder-gun")
        public Weapon elderGun = model(gun("Mrazomet", "Ultimate Mrazomet", -1, 22, 15, 4, 60, 0.9, 2.0, 0, 0, new GunLevel(20, 4, 100, 0.8, 2.0), "ENTITY_PLAYER_HURT_FREEZE", 0.9f, 0.7f), 37511);
        @ConfigKey("gold-digger")
        public GoldDigger goldDigger = new GoldDigger();

        @Comment("Knife damage stays vanilla (no swing throttle). Gold from knife hits at most once per this many seconds.")
        @ConfigKey("knife-gold-seconds")
        public double knifeGoldSeconds = 1.5;

        private static Weapon model(Weapon weapon, int customModelData) {
            weapon.customModelData = customModelData;
            return weapon;
        }

        private static Weapon gun(String name, String ultimateName, int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets, GunLevel ultimate) {
            return gun(name, ultimateName, price, gold, damage, magazine, ammo, delay, reload, pellets, targets, ultimate, "UI_BUTTON_CLICK", 1f, 1.2f, -1);
        }

        private static Weapon gun(String name, String ultimateName, int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets, GunLevel ultimate, String shootSound, float shootVolume, float shootPitch) {
            return gun(name, ultimateName, price, gold, damage, magazine, ammo, delay, reload, pellets, targets, ultimate, shootSound, shootVolume, shootPitch, -1);
        }

        private static Weapon gun(String name, String ultimateName, int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets, GunLevel ultimate, String shootSound, float shootVolume, float shootPitch, int range) {
            Weapon weapon = new Weapon(price, gold, damage, magazine, ammo, delay, reload, pellets, targets, ultimate);
            weapon.name = name;
            weapon.ultimateName = ultimateName;
            weapon.shootSound = shootSound;
            weapon.shootVolume = shootVolume;
            weapon.shootPitch = shootPitch;
            weapon.range = range;
            return weapon;
        }

        /** 9 pellets. Cone stays full on a zombie through about 4 blocks, then falls off. */
        private static Weapon shotgunGun() {
            Weapon weapon = model(gun(
                    "Blasterová brokovnice", "Ultimate Blasterová brokovnice",
                    500, 7, 4.5, 5, 65, 1.4, 1.5, 9, 0,
                    new GunLevel(4.5, 5, 80, 1.0, 1.0),
                    "ENTITY_GENERIC_EXPLODE", 0.55f, 1.55f, 28), 37503);
            weapon.spreadYaw = 14;
            weapon.spreadPitch = 8;
            return weapon;
        }
    }

    @Naming(NamingStrategy.KEBAB)
    public static class Weapon extends YamlConfig {

        @Comment("Display name in inventory, lore and wall shops.")
        public String name = "Weapon";
        @Comment("Display name after ultimate upgrade.")
        @ConfigKey("ultimate-name")
        public String ultimateName = "Ultimate Weapon";
        @Comment("Custom model data float for the gun (base and ultimate), same as /give … custom_model_data={floats:[N]}. 0 = vanilla model. Ultimate keeps the same CMD and only adds enchant glow + name/lore.")
        @ConfigKey("custom-model-data")
        public int customModelData = 0;
        @Comment("Unused. Ultimate does not change model data — kept so old configs still load.")
        @ConfigKey("ultimate-custom-model-data")
        public int ultimateCustomModelData = 0;
        @Comment("Shop price. -1 means the lucky chest, not the weapon shop. 0 is free.")
        public int price;
        @Comment("Gold for one hit. A critical hit is 150 percent, rounded up. Shotgun gold is for one pellet.")
        public int gold;
        @Comment("Damage of one shot, in HP, before upgrades.")
        public double damage;
        @Comment("Rounds in one magazine. -1 means the weapon has no ammo.")
        public int magazine;
        @Comment("Total ammo when full (magazine + reserve). -1 means the weapon has no ammo. Pistol 300 with clip 10 means 10+290.")
        public int ammo;
        @Comment("Seconds between shots. Knife ignores this (Mining Fatigue slows swings). Puncher uses it as swing cooldown.")
        public double delay;
        @Comment("Reload seconds. -1 means the weapon does not reload.")
        public double reload;
        @Comment("Pellets per shot. 0 means one projectile.")
        public int pellets;
        @Comment("Horizontal pellet cone width in degrees (random ± half). Used when pellets > 1. Default 14.")
        @ConfigKey("spread-yaw")
        public double spreadYaw = 14;
        @Comment("Vertical pellet cone width in degrees (random ± half). Used when pellets > 1. Default 8.")
        @ConfigKey("spread-pitch")
        public double spreadPitch = 8;
        @Comment("Extra targets in one shot. 0 means one target.")
        public int targets;
        @Comment("Bukkit Sound name played on shoot or melee swing. Pitched vanilla; Hypixel exact IDs are not public.")
        @ConfigKey("shoot-sound")
        public String shootSound = "UI_BUTTON_CLICK";
        @Comment("Volume of the shoot sound.")
        @ConfigKey("shoot-volume")
        public float shootVolume = 1f;
        @Comment("Pitch of the shoot sound.")
        @ConfigKey("shoot-pitch")
        public float shootPitch = 1.2f;
        @Comment("Shot travel range in blocks. -1 uses shot.range. Shotgun defaults shorter.")
        public int range = -1;
        @Comment("Stats after one ultimate upgrade. Gold digger uses level-1..5 instead.")
        public GunLevel ultimate;

        public Weapon() {
        }

        public Weapon(int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets) {
            this(price, gold, damage, magazine, ammo, delay, reload, pellets, targets, null);
        }

        public Weapon(int price, int gold, double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets, GunLevel ultimate) {
            this.price = price;
            this.gold = gold;
            this.damage = damage;
            this.magazine = magazine;
            this.ammo = ammo;
            this.delay = delay;
            this.reload = reload;
            this.pellets = pellets;
            this.targets = targets;
            this.ultimate = ultimate;
        }

        /** Same model for base and ultimate. Glow comes from enchantment on the item. */
        public int modelData(int upgradeLevel) {
            return Math.max(0, customModelData);
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
            name = "Stupňový blaster";
            ultimateName = "Ultimate Stupňový blaster";
            customModelData = 37512;
            shootSound = "ENTITY_FIREWORK_ROCKET_BLAST";
            shootVolume = 0.7f;
            shootPitch = 1.25f;
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
        @Comment("Pellets after this upgrade. -1 keeps the base weapon pellets.")
        public int pellets = -1;
        @Comment("Extra targets after this upgrade. -1 keeps the base weapon targets.")
        public int targets = -1;

        public GunLevel() {
        }

        public GunLevel(double damage, int magazine, int ammo, double delay, double reload) {
            this(damage, magazine, ammo, delay, reload, -1, -1);
        }

        public GunLevel(double damage, int magazine, int ammo, double delay, double reload, int pellets, int targets) {
            this.damage = damage;
            this.magazine = magazine;
            this.ammo = ammo;
            this.delay = delay;
            this.reload = reload;
            this.pellets = pellets;
            this.targets = targets;
        }
    }
}
