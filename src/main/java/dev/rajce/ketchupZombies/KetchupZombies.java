package dev.rajce.ketchupZombies;

import dev.lupino1.LPLibrary;
import dev.lupino1.messages.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.units.qual.C;

public final class KetchupZombies extends JavaPlugin {


    private MessageManager messageManager;

    @Override
    public void onEnable() {

        LPLibrary.init(this);
        messageManager = new MessageManager(this);







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
