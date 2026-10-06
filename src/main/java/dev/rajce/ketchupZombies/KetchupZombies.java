package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.messages.MessageManager;
import dev.rajce.ketchupZombies.managers.GameManager;
import org.bukkit.plugin.java.JavaPlugin;


public final class KetchupZombies extends JavaPlugin {

    private MessageManager messageManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {

        LPLibrary.init(this);
        messageManager = new MessageManager(this);

        gameManager = new GameManager();





        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }


}
