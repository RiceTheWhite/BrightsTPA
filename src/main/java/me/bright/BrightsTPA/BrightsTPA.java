package me.bright.BrightsTPA;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class BrightsTPA extends JavaPlugin {

    private int tpDelay;
    private int requestTimeout;
    private int commandCooldown;
    private boolean cancelOnMove;

    @Override
    public void onEnable() {
        getLogger().info("Plugin Loaded!");
        saveDefaultConfig();
        loadSettings();

        Objects.requireNonNull(getCommand("tpa")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("tpahere")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("tpdeny")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("tpyes")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("tpno")).setExecutor(new CommandHandler(this));
        Objects.requireNonNull(getCommand("brightstpa")).setExecutor(new CommandHandler(this));
    }

    public void loadSettings() {
        this.tpDelay = getConfig().getInt("tp-delay", 5);
        this.requestTimeout = getConfig().getInt("request-timeout", 30);
        this.commandCooldown = getConfig().getInt("tp-cooldown", 30);
        this.cancelOnMove = getConfig().getBoolean("cancel-on-move", true);
    }

    public int getTpDelay() {
        return tpDelay;
    }
    public int getRequestTimeout() {
        return requestTimeout;
    }
    public int getCommandCooldown() {
        return commandCooldown;
    }
    public boolean getCancelOnMove() {
        return cancelOnMove;
    }
}
