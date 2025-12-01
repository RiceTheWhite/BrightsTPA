package me.bright.BrightsTPA;

import me.bright.BrightsTPA.Format.tabComplete;
import me.bright.BrightsTPA.PlayerEventListener.onPlayerMove;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class BrightsTPA extends JavaPlugin {
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private int requestTimeout;
    private int requestCooldown;
    private int commandCooldown;
    private int tpDelay;
    private boolean cancelOnMove;

    @Override
    public void onEnable() {
        getLogger().info("Plugin Loaded!");
        saveDefaultConfig();
        loadSettings();
        
        getServer().getPluginManager().registerEvents(new tabComplete(), this);

        HandleExecutor logicHandler = new HandleExecutor(this);
        CommandHandler handler = new CommandHandler(logicHandler);

        getServer().getPluginManager().registerEvents(new onPlayerMove(logicHandler), this);
        Objects.requireNonNull(getCommand("tpa")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpahere")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpdeny")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpyes")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpno")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpacancel")).setExecutor(handler);
        Objects.requireNonNull(getCommand("brightstpa")).setExecutor(handler);
    }

    public void loadSettings() {
        requestTimeout = getConfig().getInt("request-timeout", 0);
        requestCooldown = getConfig().getInt("request-cooldown", 0);
        commandCooldown = getConfig().getInt("command-cooldown", 0);
        tpDelay = getConfig().getInt("tp-delay", 0);
        cancelOnMove = getConfig().getBoolean("cancel-on-move", false);
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }
    public int getRequestCooldown() {
        return requestCooldown;
    }
    public int getCommandCooldown() {
        return commandCooldown;
    }
    public int getTpDelay() {
        return tpDelay;
    }
    public boolean getCancelOnMove() {
        return cancelOnMove;
    }
}
