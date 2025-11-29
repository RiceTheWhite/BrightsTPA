package me.bright.brights_tpa;

import org.bukkit.plugin.java.JavaPlugin;

public final class brights_tpa extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Plugin Loaded!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin Unloaded!");
    }
}
