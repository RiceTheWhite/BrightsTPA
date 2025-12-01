package me.bright.BrightsTPA;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static me.bright.BrightsTPA.BrightsTPA.LEGACY;

public record HandleExecutor(BrightsTPA plugin) implements me.bright.BrightsTPA.Format.String {

    private static final HashMap<UUID, UUID> tpaMap = new HashMap<>();
    private static final HashMap<UUID, UUID> tpahereMap = new HashMap<>();
    private static final HashMap<UUID, Long> commandCooldownsMap = new HashMap<>();
    private static final HashMap<UUID, Long> requestCooldownMap = new HashMap<>();
    private static final HashMap<UUID, BukkitRunnable> timeoutTasksMap = new HashMap<>();
    private static final HashMap<UUID, BukkitRunnable> teleportTasksMap = new HashMap<>();

    @Override
    public void send(CommandSender sender, String msg, Object... args) {
        sender.sendMessage(LEGACY.deserialize(String.format(msg, args)));
    }

    private boolean getCommandCooldown(Player player, long cooldownSeconds, String command) {
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        long lastUse = commandCooldownsMap.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastUse < cooldownMillis) {
            long remaining = (cooldownMillis - (currentTime - lastUse)) / 1000;
            send(player, "&6You must wait &c%s second(s) &6before using &c/%s &6again.", remaining, command);
            return true;
        }
        commandCooldownsMap.put(player.getUniqueId(), currentTime);
        return false;
    }

    private boolean getRequestCooldown(Player player, long cooldownSeconds) {
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        long lastUse = requestCooldownMap.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastUse < cooldownMillis) {
            long remaining = (cooldownMillis - (currentTime - lastUse)) / 1000;
            send(player, "&6You must wait &c%s second(s) &6before sending another request &6again.", remaining);
            return true;
        }
        requestCooldownMap.put(player.getUniqueId(), currentTime);
        return false;
    }

    public void handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("brightstpa.reload")) {
            send(sender, "&cYou do not have permission to use this command.");
            return;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            return;
        }

        try {
            plugin.reloadConfig();
            plugin.loadSettings();
            send(sender, "&6Configuration reloaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            send(sender, "&cFailed to reload config! Check console for details.");
        }
    }

    public void handleTpaDenyCommand(CommandSender sender, String[] args) {
        Player requestPlayer = (Player) sender;

        if (getCommandCooldown(requestPlayer, plugin.getCommandCooldown(), "tpdeny")) {
            return;
        }

        if (!requestPlayer.hasPermission("brightstpa.tpdeny")) {
            send(requestPlayer, "&cYou do not have permission to use this command!");
            return;
        }

        if (args.length == 0) {
            denyAllRequests(requestPlayer);
        } else {
            denySpecificRequest(requestPlayer, args[0]);
        }
    }

    private void denyAllRequests(Player requestPlayer) {
        if (!tpaMap.containsValue(requestPlayer.getUniqueId()) &&
                !tpahereMap.containsValue(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You don't have any pending requests!");
            return;
        }

        denyRequestsFromMap(tpaMap, requestPlayer, "TPA");
        denyRequestsFromMap(tpahereMap, requestPlayer, "TPAHERE");
        send(requestPlayer, "&6Denied all requests.");
    }

    private void denyRequestsFromMap(Map<UUID, UUID> map, Player requestPlayer, String type) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : map.entrySet()) {
            if (entry.getValue().equals(requestPlayer.getUniqueId())) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID senderUUID : toRemove) {
            cancelTimeout(senderUUID);
            map.remove(senderUUID);
            Player sender = Bukkit.getPlayer(senderUUID);
            if (sender != null) {
                send(sender, "&6Your %s request was denied!", type);
            }
        }
    }

    private void denySpecificRequest(Player requestPlayer, String playerName) {
        Player receivePlayer = Bukkit.getPlayer(playerName);
        if (receivePlayer == null) {
            send(requestPlayer, "&cPlayer not found!");
            return;
        }

        if (receivePlayer.getUniqueId().equals(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You may not deny yourself!");
            return;
        }

        cancelTimeout(receivePlayer.getUniqueId());
        boolean foundRequest = removeRequest(tpaMap, receivePlayer, requestPlayer, "TPA");
        foundRequest |= removeRequest(tpahereMap, receivePlayer, requestPlayer, "TPAHERE");

        if (!foundRequest) {
            send(requestPlayer, "&6You don't have any pending requests from &c%s!", receivePlayer.getName());
        }
    }

    private boolean removeRequest(Map<UUID, UUID> map, Player requestPlayer, Player receivePlayer, String type) {
        if (map.containsKey(requestPlayer.getUniqueId()) && map.get(requestPlayer.getUniqueId()).equals(receivePlayer.getUniqueId())) {
            map.remove(requestPlayer.getUniqueId());
            send(requestPlayer, "&6Your %s request was denied!", type);
            send(receivePlayer, "&6Denied &c%s&6's %s request.", requestPlayer.getName(), type);
            return true;
        }
        return false;
    }

    public void handleTpaAcceptCommand(CommandSender sender, String[] args) {
        Player requestPlayer = (Player) sender;

        if (getCommandCooldown(requestPlayer, plugin.getCommandCooldown(), "tpaccept")) {
            return;
        }

        if (!requestPlayer.hasPermission("brightstpa.tpaccept")) {
            send(requestPlayer, "&cYou do not have permission to use this command!");
            return;
        }

        if (args.length == 0) {
            acceptAnyRequest(requestPlayer);
        } else {
            acceptSpecificRequest(requestPlayer, args[0]);
        }
    }

    private void acceptAnyRequest(Player requestPlayer) {
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You cannot accept requests while teleporting!");
            return;
        }
        UUID senderUUID = findRequestSender(tpaMap, requestPlayer);
        if (teleportTasksMap.containsKey(senderUUID)) {
            send(requestPlayer, "&6That player is already teleporting!");
            return;
        }
        if (senderUUID != null) {
            cancelTimeout(senderUUID);
            tpExecute(senderUUID, requestPlayer, "tpa");
            return;
        }

        senderUUID = findRequestSender(tpahereMap, requestPlayer);
        if (senderUUID != null) {
            cancelTimeout(senderUUID);
            tpExecute(senderUUID, requestPlayer, "tpahere");
            return;
        }

        send(requestPlayer, "&6You don't have any pending requests!");
    }

    private void acceptSpecificRequest(Player requestPlayer, String playerName) {
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You cannot accept requests while teleporting!");
            return;
        }
        Player receivePlayer = Bukkit.getPlayer(playerName);
        if (receivePlayer == null) {
            send(requestPlayer, "&cPlayer not found!");
            return;
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(requestPlayer, "&6That player is already teleporting!");
            return;
        }
        if (receivePlayer.getUniqueId().equals(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You may not accept yourself!");
            return;
        }

        cancelTimeout(receivePlayer.getUniqueId());
        UUID senderUUID = receivePlayer.getUniqueId();

        if (tpaMap.containsKey(senderUUID) && tpaMap.get(senderUUID).equals(requestPlayer.getUniqueId())) {
            tpExecute(senderUUID, requestPlayer, "tpa");
        } else if (tpahereMap.containsKey(senderUUID) && tpahereMap.get(senderUUID).equals(requestPlayer.getUniqueId())) {
            tpExecute(senderUUID, requestPlayer, "tpahere");
        } else {
            send(requestPlayer, "&6You don't have any pending requests from &c%s!", receivePlayer.getName());
        }
    }

    private UUID findRequestSender(Map<UUID, UUID> map, Player receiver) {
        return map.entrySet().stream()
                .filter(entry -> entry.getValue().equals(receiver.getUniqueId()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public void handleTpaCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return;

        sendTeleportRequest(sender, args[0], tpaMap, "TPA", "wants tp to you");
    }

    public void handleTpahereCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return;
        sendTeleportRequest(sender, args[0], tpahereMap, "TPAHERE", "wants you tp to them");
    }

    private void sendTeleportRequest(CommandSender sender, String name, Map<UUID, UUID> map, String type, String message) {
        Player requestPlayer = (Player) sender;
        if (type.equals("TPA")) {
            if (!requestPlayer.hasPermission("brightstpa.tpa")) {
                send(requestPlayer, "&cYou do not have permission to use this command!");
                return;
            }
        }
        else if (type.equals("TPAHERE")) {
            if (!requestPlayer.hasPermission("brightstpa.tpahere")) {
                send(requestPlayer, "&cYou do not have permission to use this command!");
                return;
            }
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You cannot send requests while teleporting!");
            return;
        }
        Player receivePlayer = Bukkit.getPlayer(name);
        if (receivePlayer == null) {
            send(requestPlayer, "&cPlayer is not online!");
            return;
        }

        if (receivePlayer.getUniqueId().equals(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You may not teleport to yourself!");
            return;
        }

        if (map.containsKey(requestPlayer.getUniqueId()) && map.get(requestPlayer.getUniqueId()).equals(receivePlayer.getUniqueId())) {
            send(requestPlayer, "&6You already have a pending request to %s!", receivePlayer.getName());
            return;
        }

        if (getCommandCooldown(requestPlayer, plugin.getCommandCooldown(), type.toLowerCase()) ||
                getRequestCooldown(requestPlayer, plugin.getRequestCooldown())) {
            return;
        }

        int timeoutSecond = plugin.getRequestTimeout();
        map.put(requestPlayer.getUniqueId(), receivePlayer.getUniqueId());

        send(requestPlayer, """
                        &6Sent %s request to &c%s.
                        &6Type &c/tpacancel &6to cancel.
                        """,
                type, receivePlayer.getName());
        send(receivePlayer, """
                        &c%s &6%s.
                        &6Type &c/tpaccept &6to accept.
                        &6Type &c/tpdeny &6to deny.
                        &6You have &c%s second(s) &6to respond.
                        """,
                requestPlayer.getName(), message, timeoutSecond);

        scheduleTimeout(requestPlayer, map, type, timeoutSecond * 20L);
    }

    private void scheduleTimeout(Player requestPlayer, Map<UUID, UUID> map, String type, long ticks) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                UUID receiverUUID = map.remove(requestPlayer.getUniqueId());
                if (receiverUUID != null) {
                    Player receiver = Bukkit.getPlayer(receiverUUID);
                    String receiverName = (receiver != null && receiver.isOnline()) ? receiver.getName() : "a player";
                    send(requestPlayer, "&6Your %s request to &c%s &6has timed out.", type, receiverName);
                    if (receiver != null && receiver.isOnline()) {
                        send(receiver, "&c%s&6's %s request has timed out.", requestPlayer.getName(), type);
                    }
                }
                timeoutTasksMap.remove(requestPlayer.getUniqueId());
            }
        };
        task.runTaskLater(plugin, ticks);
        timeoutTasksMap.put(requestPlayer.getUniqueId(), task);
    }

    private void cancelTimeout(UUID playerUUID) {
        BukkitRunnable task = timeoutTasksMap.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    public static void cancelTp(Player player) {
        BukkitRunnable task = teleportTasksMap.remove(player.getUniqueId());
        tpahereMap.remove(player.getUniqueId());
        tpaMap.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage("§cTeleport cancelled, you moved!");
        }
    }
    
    public void handleTpaCancelCommand(CommandSender sender) {
        Player requestPlayer = (Player) sender;

        if (getCommandCooldown(requestPlayer, plugin.getCommandCooldown(), "tpacancel")) {
            return;
        }

        if (!requestPlayer.hasPermission("brightstpa.tpacancel")) {
            send(requestPlayer, "&cYou do not have permission to use this command!");
            return;
        }

        if (!tpaMap.containsKey(requestPlayer.getUniqueId()) &&
                !tpahereMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, "&6You don't have any pending requests to cancel!");
            return;
        }

        cancelTpaRequest(requestPlayer);
    }

    private void cancelTpaRequest(Player requestPlayer) {
        UUID receiverUUID = tpaMap.remove(requestPlayer.getUniqueId());
        String type = "TPA";

        if (receiverUUID == null) {
            receiverUUID = tpahereMap.remove(requestPlayer.getUniqueId());
            type = "TPAHERE";
        }

        if (receiverUUID != null) {
            cancelTimeout(requestPlayer.getUniqueId());
            send(requestPlayer, "&6Cancelled all your %s requests.", type);

            Player receiver = Bukkit.getPlayer(receiverUUID);
            if (receiver != null && receiver.isOnline()) {
                send(receiver, "&c%s &6cancelled their %s request.", requestPlayer.getName(), type);
            }
        }
    }

    private void tpExecute(UUID senderUUID, Player receiver, String type) {
        Player sender = Bukkit.getPlayer(senderUUID);

        if (sender == null || !sender.isOnline()) {
            tpaMap.remove(senderUUID);
            tpahereMap.remove(senderUUID);
            send(receiver, "&cThe player is no longer online.");
            return;
        }

        int delaySeconds = plugin.getTpDelay();
        boolean isTpa = type.equals("tpa");

        if (isTpa) {
            send(receiver, "&6TPA request accepted! &c%s &6will teleport to you in &c%s seconds.", sender.getName(), delaySeconds);
            send(sender, "&6Request accepted! Teleporting in &c%s seconds.", delaySeconds);
        }
        else {
            send(sender, "&6TPAHERE request accepted! &c%s &6will teleport to you in &c%s seconds.", receiver.getName(), delaySeconds);
            send(receiver, "&6Request accepted! Teleporting in &c%s seconds.", delaySeconds);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
            if (!sender.isOnline() || !receiver.isOnline()) {
                send(isTpa ? sender : receiver, "&cTeleport cancelled, player went offline.");
            }
            else {
                if (isTpa) {
                    sender.teleport(receiver.getLocation());
                    tpaMap.remove(senderUUID);
                    send(sender, "&6Teleported successfully!");
                    send(receiver, "&6%s has arrived.", sender.getName());
                }
                else {
                    receiver.teleport(sender.getLocation());
                    tpahereMap.remove(senderUUID);
                    send(receiver, "&6Teleported successfully!");
                    send(sender, "&6%s has arrived.", receiver.getName());
                }
            }
            teleportTasksMap.remove(sender.getUniqueId());
            }
        };
        task.runTaskLater(plugin, delaySeconds * 20L);
        teleportTasksMap.put(sender.getUniqueId(), task);
    }
}