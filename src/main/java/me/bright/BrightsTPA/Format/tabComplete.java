package me.bright.BrightsTPA.Format;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.lang.String;
import java.util.List;

public class tabComplete implements Listener {

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();

        if (buffer.startsWith("/brightstpa")) {
            event.setCompletions(List.of("reload", "version"));
        }
    }
}