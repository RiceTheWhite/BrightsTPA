package me.bright.BrightsTPA.Format;

import org.bukkit.command.CommandSender;

import static me.bright.BrightsTPA.BrightsTPA.LEGACY;

public class String {
    public static void send(CommandSender sender, java.lang.String msg, Object... args) {
        sender.sendMessage(LEGACY.deserialize(java.lang.String.format(msg, args)));
    }
}
