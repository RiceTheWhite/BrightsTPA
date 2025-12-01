package me.bright.BrightsTPA;

import me.bright.BrightsTPA.Format.tabComplete;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class BrightsTPA extends JavaPlugin {
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        getLogger().info("Plugin Loaded!");
        saveDefaultConfig();
        loadSettings();
        
        getServer().getPluginManager().registerEvents(new tabComplete(), this);

        HandleExecutor logicHandler = new HandleExecutor(this);
        CommandHandler handler = new CommandHandler(logicHandler);

        Objects.requireNonNull(getCommand("tpa")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpahere")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpdeny")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpyes")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpno")).setExecutor(handler);
        Objects.requireNonNull(getCommand("tpacancel")).setExecutor(handler);
        Objects.requireNonNull(getCommand("brightstpa")).setExecutor(handler);
    }

    public static int requestTimeout;
    public static int requestCooldown;
    public static int commandCooldown;
    public static int tpDelay;
    public static boolean cancelOnMove;

    public void loadSettings() {
        requestTimeout = getConfig().getInt("request-timeout", 0);
        requestCooldown = getConfig().getInt("request-cooldown", 0);
        commandCooldown = getConfig().getInt("command-cooldown", 0);
        tpDelay = getConfig().getInt("tp-delay", 0);
        cancelOnMove = getConfig().getBoolean("cancel-on-move", false);
    }
    public static int getRequestTimeout() {
        return requestTimeout;
    }
    public static int getRequestCooldown() {
        return requestCooldown;
    }
    public static int getCommandCooldown() {
        return commandCooldown;
    }
    public static int getTpDelay() {
        return tpDelay;
    }
    public static boolean getCancelOnMove() {
        return cancelOnMove;
    }
}
