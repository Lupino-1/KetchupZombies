package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.config.ConfigDocument;
import dev.lupino1.config.ConfigHolder;
import dev.lupino1.messages.MessageManager;
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


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }


}
