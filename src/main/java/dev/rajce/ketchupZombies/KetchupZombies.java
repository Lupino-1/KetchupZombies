package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.command.CommandMessageKeys;
import dev.lupino1.config.ConfigHolder;
import dev.lupino1.messages.MessageManager;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.db.SqlDatabase;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;
import dev.rajce.ketchupZombies.game.BoardPreview;
import dev.rajce.ketchupZombies.game.GameManager;
import dev.rajce.ketchupZombies.game.HubBoard;
import dev.rajce.ketchupZombies.game.LobbyBoard;
import dev.rajce.ketchupZombies.game.Match;
import dev.rajce.ketchupZombies.game.SoftCollision;
import dev.rajce.ketchupZombies.listeners.EditorListener;
import dev.rajce.ketchupZombies.listeners.GameListener;
import dev.rajce.ketchupZombies.listeners.InventoryLockListener;
import dev.rajce.ketchupZombies.listeners.ArenaProtectListener;
import dev.rajce.ketchupZombies.listeners.IsolationListener;
import dev.rajce.ketchupZombies.listeners.JoinTabListener;
import dev.rajce.ketchupZombies.listeners.WeaponListener;
import dev.rajce.ketchupZombies.managers.ArenaCopies;
import dev.rajce.ketchupZombies.managers.ArenaFiles;
import dev.rajce.ketchupZombies.managers.BlueprintManager;
import dev.rajce.ketchupZombies.managers.CommandManager;
import dev.rajce.ketchupZombies.managers.HologramManager;
import dev.rajce.ketchupZombies.managers.InstanceFiles;
import dev.rajce.ketchupZombies.placeholder.ZombiesPlaceholders;
import dev.rajce.ketchupZombies.shop.Profiles;
import dev.rajce.ketchupZombies.shop.ShopMenu;
import dev.rajce.ketchupZombies.weapons.WeaponCombat;
import dev.rajce.ketchupZombies.weapons.WeaponItems;
import lombok.Getter;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class KetchupZombies extends JavaPlugin {

    @Getter
    private MessageManager messageManager;
    @Getter
    private Profiles profiles;
    @Getter
    private WeaponItems weaponItems;
    @Getter
    private WeaponCombat weaponCombat;
    @Getter
    private ShopMenu shopMenu;
    @Getter
    private SqlDatabase database;
    @Getter
    private HologramManager holograms;
    @Getter
    private Editor editor;
    @Getter
    private ArenaCopies copies;
    @Getter
    private GameManager games;
    @Getter
    private ScoreboardLibrary scoreboards;
    @Getter
    private BoardPreview boardPreview;
    @Getter
    private HubBoard hubBoard;
    @Getter
    private LobbyBoard lobbyBoard;
    private ConfigHolder<PluginConfig> configHolder;
    private ZombiesPlaceholders placeholders;

    @Override
    public void onEnable() {

        LPLibrary.init(this);
        CommandMessageKeys.setDefaults(new CommandMessageKeys(
                "cmd.no-perm",
                "cmd.usage",
                "cmd.player",
                "cmd.console"
        ));
        messageManager = new MessageManager(this);
        configHolder = ConfigHolder.create(this, PluginConfig.class, "config.yml");
        try {
            scoreboards = ScoreboardLibrary.loadScoreboardLibrary(this);
        } catch (NoPacketAdapterAvailableException ex) {
            scoreboards = new NoopScoreboardLibrary();
            getLogger().warning("Scoreboard library unsupported on this server version.");
        }
        SoftCollision.start(this);
        profiles = new Profiles();
        weaponItems = new WeaponItems(this);
        weaponCombat = new WeaponCombat(this, weaponItems);
        shopMenu = new ShopMenu(this, weaponItems);
        database = new SqlDatabase(this);
        database.start();

        BlueprintManager blueprintManager = new BlueprintManager(this);
        ArenaFiles arenas = new ArenaFiles(this);
        InstanceFiles instances = new InstanceFiles(this);
        holograms = new HologramManager(this, arenas, instances, blueprintManager);
        editor = new Editor(this, blueprintManager, arenas, holograms);
        copies = new ArenaCopies(this, blueprintManager, arenas, instances, holograms);
        games = new GameManager(this, copies);
        hubBoard = new HubBoard(this);
        lobbyBoard = new LobbyBoard(this);
        boardPreview = new BoardPreview(this);
        getServer().getPluginManager().registerEvents(new EditorListener(editor), this);
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new IsolationListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinTabListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryLockListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectListener(this), this);
        new CommandManager(this, editor, new BlueprintTest(this, blueprintManager), copies).register();
        holograms.showSaved();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new ZombiesPlaceholders(this);
            placeholders.register();
        }
        for (Player online : getServer().getOnlinePlayers()) {
            online.getScheduler().run(this, task -> {
                SoftCollision.player(online, false);
                hubBoard.show(online);
                games.isolation().hub(online);
            }, null);
        }
    }

    public PluginConfig config() {
        return configHolder.get();
    }

    /** Live config, or the match snapshot when the player is already in a game. */
    public PluginConfig config(Player player) {
        Match match = games != null ? games.match(player) : null;
        return match != null ? match.config() : config();
    }

    /**
     * Hub landing. {@code /kz setspawn} wins. Until then, the spawn point of the overworld named {@code world}.
     */
    public org.bukkit.Location hubSpawn() {
        PluginConfig.HubSpawn spawn = config().hubSpawn;
        if (spawn != null && spawn.set && spawn.world != null && !spawn.world.isBlank()) {
            org.bukkit.World world = getServer().getWorld(spawn.world);
            if (world != null) {
                return new org.bukkit.Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
            }
        }
        String name = spawn != null && spawn.world != null && !spawn.world.isBlank() ? spawn.world : "world";
        org.bukkit.World overworld = getServer().getWorld(name);
        if (overworld == null) {
            overworld = getServer().getWorld("world");
        }
        if (overworld == null) {
            return null;
        }
        org.bukkit.Location at = overworld.getSpawnLocation();
        return new org.bukkit.Location(overworld, at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5, at.getYaw(), at.getPitch());
    }

    public java.util.concurrent.CompletableFuture<Void> setHubSpawn(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        String world = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        return configHolder.editAsync(cfg -> {
            cfg.hubSpawn.set = true;
            cfg.hubSpawn.world = world;
            cfg.hubSpawn.x = x;
            cfg.hubSpawn.y = y;
            cfg.hubSpawn.z = z;
            cfg.hubSpawn.yaw = yaw;
            cfg.hubSpawn.pitch = pitch;
        });
    }

    public void clearBoardPreview(Player player) {
        if (boardPreview != null) {
            boardPreview.clear(player);
        }
    }

    /**
     * Reloads config.yml and messages.yml, then refreshes hologram displays (text from config holograms).
     * Running matches keep the PluginConfig snapshot taken at match create.
     * Geometry/marker changes still need {@code /kz refresh <name>}.
     */
    public CompletableFuture<Void> reloadSettings() {
        return configHolder.reloadAsync().thenCompose(ignored -> messageManager.reloadAsync()).thenRun(() -> {
            if (hubBoard != null) {
                hubBoard.refreshAll();
            }
            if (holograms != null) {
                holograms.showSaved();
            }
        });
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdown();
        }
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            SoftCollision.player(online, true);
        }
        SoftCollision.clear();
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (boardPreview != null) {
            boardPreview.clearAll();
        }
        if (hubBoard != null) {
            hubBoard.clearAll();
        }
        if (lobbyBoard != null) {
            lobbyBoard.clearAll();
        }
        if (database != null) {
            database.close();
        }
        if (scoreboards != null) {
            scoreboards.close();
        }
        CommandMessageKeys.resetDefaults();
    }


}
