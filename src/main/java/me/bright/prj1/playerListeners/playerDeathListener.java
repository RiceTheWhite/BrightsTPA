package me.bright.prj1.playerListeners;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;

public class playerDeathListener implements Listener {

    @SuppressWarnings("SameParameterValue")
    private Component msg(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String playerName = player.getName();
        player.getWorld().sendMessage(msg(playerName + " has died in this world"));
    }
}
