package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.command.CommandMessageKeys;
import dev.lupino1.config.ConfigHolder;
import dev.lupino1.messages.MessageManager;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.db.SqlDatabase;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;
import dev.rajce.ketchupZombies.game.GameManager;
import dev.rajce.ketchupZombies.listeners.EditorListener;
import dev.rajce.ketchupZombies.listeners.GameListener;
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
import org.bukkit.plugin.java.JavaPlugin;

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
        profiles = new Profiles();
        weaponItems = new WeaponItems(this);
        weaponCombat = new WeaponCombat(this, weaponItems);
        shopMenu = new ShopMenu(this, weaponItems);
        database = new SqlDatabase(this);
        database.start();

        BlueprintManager blueprintManager = new BlueprintManager(this);
        ArenaFiles arenas = new ArenaFiles(this);
        InstanceFiles instances = new InstanceFiles(this);
        holograms = new HologramManager(this, arenas, instances);
        editor = new Editor(this, blueprintManager, arenas, holograms);
        copies = new ArenaCopies(this, blueprintManager, arenas, instances, holograms);
        games = new GameManager(this, copies);
        getServer().getPluginManager().registerEvents(new EditorListener(editor), this);
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        new CommandManager(this, editor, new BlueprintTest(this, blueprintManager), copies).register();
        holograms.showSaved();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new ZombiesPlaceholders(this);
            placeholders.register();
        }
    }

    public PluginConfig config() {
        return configHolder.get();
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
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
