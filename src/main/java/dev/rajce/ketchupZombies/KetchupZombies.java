package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.config.ConfigDocument;
import dev.lupino1.config.ConfigHolder;
import dev.lupino1.messages.MessageManager;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;
import dev.rajce.ketchupZombies.listeners.EditorListener;
import dev.rajce.ketchupZombies.managers.ArenaFiles;
import dev.rajce.ketchupZombies.managers.BlueprintManager;
import dev.rajce.ketchupZombies.managers.CommandManager;
import dev.rajce.ketchupZombies.managers.GameManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class KetchupZombies extends JavaPlugin {

    @Getter
    private MessageManager messageManager;
    private GameManager gameManager;
    private ConfigHolder<ConfigDocument> configHolder;

    @Override
    public void onEnable() {

        LPLibrary.init(this);
        messageManager = new MessageManager(this);

        configHolder = ConfigHolder.create(this, ConfigDocument.class, "config.yml");

        BlueprintManager blueprintManager = new BlueprintManager(this);
        ArenaFiles arenas = new ArenaFiles(this);
        Editor editor = new Editor(this, blueprintManager, arenas);
        getServer().getPluginManager().registerEvents(new EditorListener(editor), this);
        new CommandManager(this, editor, new BlueprintTest(this, blueprintManager)).register();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }


}
