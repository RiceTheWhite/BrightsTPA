package me.bright.prj1;

import org.bukkit.plugin.java.JavaPlugin;
import me.bright.prj1.playerListeners.*;

public final class prj1 extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Plugin Loaded!");
        getServer().getPluginManager().registerEvents(new playerDeathListener(), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin Unloaded!");
    }
}
