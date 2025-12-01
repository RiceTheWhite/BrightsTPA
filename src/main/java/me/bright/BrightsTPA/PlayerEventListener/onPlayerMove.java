package me.bright.BrightsTPA.PlayerEventListener;

import me.bright.BrightsTPA.HandleExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public record onPlayerMove(HandleExecutor executor) implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (executor.plugin().getCancelOnMove() && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            HandleExecutor.cancelTp(player);
        }
    }
}