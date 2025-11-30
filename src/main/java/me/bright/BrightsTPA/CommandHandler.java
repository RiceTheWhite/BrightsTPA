package me.bright.BrightsTPA;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public record CommandHandler(BrightsTPA plugin) implements CommandExecutor {

    static HashMap<UUID, UUID> targetMap = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        switch (command.getName().toLowerCase()) {

            case "tpa" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(LEGACY.deserialize("&cOnly players may use this command!"));
                    return true;
                }
                handleTpaCommand(sender, args);
                return true;
            }

            case "tpahere" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(LEGACY.deserialize("&cOnly players may use this command!"));
                    return true;
                }
                handleTpahereCommand(sender, args);
                return true;
            }

            case "tpaccept", "tpyes" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(LEGACY.deserialize("&cOnly players may use this command!"));
                    return true;
                }
                handleTpaAcceptCommand(sender);
                return true;
            }

            case "tpdeny", "tpno" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(LEGACY.deserialize("&cOnly players may use this command!"));
                    return true;
                }
                handleTpaDenyCommand(sender);
                return true;
            }

            case "brightstpa" -> {
                handleReloadCommand(sender, args);
                return true;
            }

        }
        return false;
    }

    private void handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brightstpa.reload")) {
            sender.sendMessage(LEGACY.deserialize("&cYou do not have permission to use this command."));
            return;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /brightstpa reload"));
            return;
        }

        try {
            plugin.reloadConfig();
            plugin.loadSettings();
            sender.sendMessage(LEGACY.deserialize("&aConfiguration reloaded successfully!"));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            sender.sendMessage(LEGACY.deserialize("&cFailed to reload config! Check console for details."));
        }
    }

    private void handleTpaDenyCommand(CommandSender sender) {
        if (!sender.hasPermission("brightstpa.deny")) {
            sender.sendMessage(LEGACY.deserialize("&cYou do not have permission to use this command!"));
            return;
        }

        final Player senderP = (Player) sender;

        if (!targetMap.containsValue(senderP.getUniqueId())) {
            sender.sendMessage(LEGACY.deserialize("&6You don't have any pending requests!"));
            return;
        }

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            if (entry.getValue().equals(senderP.getUniqueId())) {
                targetMap.remove(entry.getKey());

                Player originalSender = Bukkit.getPlayer(entry.getKey());
                if (originalSender != null) {
                    originalSender.sendMessage(LEGACY.deserialize("&6Your TPA request was denied!"));
                }

                sender.sendMessage(LEGACY.deserialize("&6Denied TPA request."));
                break;
            }
        }
    }

    private void handleTpaAcceptCommand(CommandSender sender) {
        if (!sender.hasPermission("brightstpa.accept")) {
            sender.sendMessage(LEGACY.deserialize("&cYou do not have permission to use this command!"));
            return;
        }

        final Player senderP = (Player) sender;

        UUID requesterUUID = null;

        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            if (entry.getValue().equals(senderP.getUniqueId())) {
                requesterUUID = entry.getKey();
                break;
            }
        }

        if (requesterUUID == null) {
            senderP.sendMessage(LEGACY.deserialize("&6You don't have any pending requests!"));
            return;
        }

        Player requester = Bukkit.getPlayer(requesterUUID);

        if (requester == null || !requester.isOnline()) {
            targetMap.remove(requesterUUID);
            senderP.sendMessage(LEGACY.deserialize("&cThe requester is no longer online."));
            return;
        }

        final int delaySeconds = plugin.getTpDelay();
        final long delayTicks = delaySeconds * 20L;

        senderP.sendMessage(LEGACY.deserialize("&6TPA request accepted! &a" + requester.getName() + " will teleport to you in &c" + delaySeconds + " seconds."));
        requester.sendMessage(LEGACY.deserialize("&6Request accepted! Teleporting in &c" + delaySeconds + " seconds."));

        new BukkitRunnable() {
            @Override
            public void run() {

                if (requester.isOnline() && senderP.isOnline()) {
                    requester.teleport(senderP.getLocation());
                    requester.sendMessage(LEGACY.deserialize("&aTeleported successfully!"));
                    senderP.sendMessage(LEGACY.deserialize("&a" + requester.getName() + " has arrived."));
                } else {
                    requester.sendMessage(LEGACY.deserialize("&cTeleport cancelled: A player went offline."));
                }
            }
        }.runTaskLater(plugin, delayTicks);
        targetMap.remove(requesterUUID);
    }

    private void handleTpaCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brightstpa.tpa")) {
            sender.sendMessage(LEGACY.deserialize("&cYou do not have permission to use this command!"));
            return;
        }

        if (args.length != 1) {
            sender.sendMessage(LEGACY.deserialize("&cInvalid syntax!"));
            return;
        }

        Player senderP = (Player) sender;

        long now = System.currentTimeMillis();
        long cooldownTime = plugin.getCommandCooldown();

        if (cooldowns.containsKey(senderP.getUniqueId())) {
            long lastUse = cooldowns.get(senderP.getUniqueId());
            long timeLeft = (lastUse + cooldownTime) - now;

            if (timeLeft >= 0) {
                String timeRemaining = String.format("%.1f", timeLeft / 1000.0);
                String message = "&cYou must wait &6%s seconds &cbefore sending another TPA or TPAHERE request.";
                sender.sendMessage(LEGACY.deserialize(String.format(message,timeRemaining)));
                return;
            }
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(LEGACY.deserialize("&cPlayer is not online!"));
            return;
        }

        if (target.getUniqueId().equals(senderP.getUniqueId())) {
            sender.sendMessage(LEGACY.deserialize("&cYou may not teleport to yourself!"));
            return;
        }

        if (targetMap.containsKey(senderP.getUniqueId())) {
            sender.sendMessage(LEGACY.deserialize("&6You already have a pending request!"));
            return;
        }

        cooldowns.put(senderP.getUniqueId(), now);

        final int timeoutSecond = plugin.getRequestTimeout();
        final long timeoutTicks = timeoutSecond * 20L;

        final String[] message = {"""
            &c%s &6wants to teleport to you.
            &6Type &c/tpaccept &6to accept.
            &6Type &c/tpdeny &6to deny.
            &6You have %s second(s) to respond.
            """};
        target.sendMessage(LEGACY.deserialize(String.format(message[0],senderP.getName(),timeoutSecond)));

        targetMap.put(senderP.getUniqueId(), target.getUniqueId());

        message[0] = "&6Sent TPA request to &c%s";
        senderP.sendMessage(LEGACY.deserialize(String.format(message[0],target.getName())));

        new BukkitRunnable() {
            @Override
            public void run() {
                UUID targetUUID = targetMap.remove(senderP.getUniqueId());

                if (targetUUID != null) {
                    Player targetPlayer = Bukkit.getPlayer(targetUUID);

                    String targetName = (targetPlayer != null && targetPlayer.isOnline())
                            ? targetPlayer.getName()
                            : "a player";

                    message[0] = "&cYour TPA request to %s has timed out.";
                    senderP.sendMessage(LEGACY.deserialize(String.format(message[0],targetName)));

                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        message[0] = "&c%s's TPA request has timed out.";
                        targetPlayer.sendMessage(LEGACY.deserialize(String.format(message[0],senderP.getName())));
                    }
                }
            }
        }.runTaskLater(plugin, timeoutTicks);
    }

    private void handleTpahereCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brightstpa.tpahere")) {
            sender.sendMessage(LEGACY.deserialize("&cYou do not have permission to use this command!"));
            return;
        }

        if (args.length != 1) {
            sender.sendMessage(LEGACY.deserialize("&cInvalid syntax! Usage: /tpahere <player>"));
            return;
        }

        Player requester = (Player) sender;

        long now = System.currentTimeMillis();
        long cooldownTime = plugin.getCommandCooldown();

        if (cooldowns.containsKey(requester.getUniqueId())) {
            long lastUse = cooldowns.get(requester.getUniqueId());
            long timeLeft = (lastUse + cooldownTime) - now;

            if (timeLeft >= 0) {
                String timeRemaining = String.format("%.1f", timeLeft / 1000.0);
                String message = "&cYou must wait &6%s seconds &cbefore sending another TPA or TPAHERE request.";
                sender.sendMessage(LEGACY.deserialize(String.format(message,timeRemaining)));
                return;
            }
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            requester.sendMessage(LEGACY.deserialize("&cPlayer is not online!"));
            return;
        }

        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(LEGACY.deserialize("&cYou may not request a teleport from yourself!"));
            return;
        }

        if (targetMap.containsValue(target.getUniqueId())) {
            requester.sendMessage(LEGACY.deserialize("&6That player already has a pending TPA request!"));
            return;
        }

        cooldowns.put(requester.getUniqueId(), now);

        final int timeoutSecond = plugin.getRequestTimeout();
        final long timeoutTicks = timeoutSecond * 20L;

        targetMap.put(target.getUniqueId(), requester.getUniqueId());

        String reqMsg = "&6Sent TPAHERE request to &c%s";
        requester.sendMessage(LEGACY.deserialize(String.format(reqMsg, target.getName())));

        final String[] targetMessage = {"""
            &c%s &6wants you to teleport to them.
            &6Type &c/tpaccept &6to accept or &c/tpdeny &6to deny.
            &6You have %s second(s) to respond.
            """};
        target.sendMessage(LEGACY.deserialize(String.format(targetMessage[0], requester.getName(), timeoutSecond)));

        new BukkitRunnable() {
            @Override
            public void run() {
                UUID storedRequesterUUID = targetMap.remove(target.getUniqueId());

                if (storedRequesterUUID != null) {
                    Player storedRequester = Bukkit.getPlayer(storedRequesterUUID);

                    targetMessage[0] = "&c%s's TPAHERE request has timed out.";
                    target.sendMessage(LEGACY.deserialize(String.format(targetMessage[0], requester.getName())));

                    if (storedRequester != null && storedRequester.isOnline()) {
                        storedRequester.sendMessage(LEGACY.deserialize("&cYour TPAHERE request to " + target.getName() + " has timed out."));
                    }
                }
            }
        }.runTaskLater(plugin, timeoutTicks);
    }
}
