package me.bright.BrightsTPA;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static me.bright.BrightsTPA.Format.String.send;

public record HandleExecutor(BrightsTPA plugin) {

    public static final HashMap<UUID, Map<UUID, Long>> tpaMap = new HashMap<>();
    public static final HashMap<UUID, Map<UUID, Long>> tpahereMap = new HashMap<>();
    public static final HashMap<UUID, Long> commandCooldownsMap = new HashMap<>();
    public static final HashMap<UUID, Long> requestCooldownMap = new HashMap<>();
    public static final HashMap<UUID, BukkitRunnable> timeoutTasksMap = new HashMap<>();
    public static final HashMap<UUID, BukkitRunnable> teleportTasksMap = new HashMap<>();
    public static final HashMap<String, String> placeholders = new HashMap<>();

    public boolean commandIsCooldown(Player player, long cooldownSeconds, String command) {
        final long currentTime = System.currentTimeMillis();
        final long cooldownMillis = cooldownSeconds * 1000L;
        final long lastUse = commandCooldownsMap.getOrDefault(player.getUniqueId(), 0L);

        if (!player.hasPermission("brightstpa.bypass.cooldown.command")) {
            if (currentTime - lastUse < cooldownMillis) {
                long remaining = (cooldownMillis - (currentTime - lastUse)) / 1000;
                placeholders.clear();
                placeholders.put("%time%", String.valueOf(remaining));
                placeholders.put("%command%", command);
                send(player, plugin.Message("messages.command_cooldown", placeholders));
                return true;
            }
            commandCooldownsMap.put(player.getUniqueId(), currentTime);
            return false;
        }
        return false;
    }

    public boolean requestIsCooldown(Player player, long cooldownSeconds) {
        final long currentTime = System.currentTimeMillis();
        final long cooldownMillis = cooldownSeconds * 1000L;
        final long lastUse = requestCooldownMap.getOrDefault(player.getUniqueId(), 0L);

        if (!player.hasPermission("brightstpa.bypass.cooldown.request")) {
            if (currentTime - lastUse < cooldownMillis) {
                long remaining = (cooldownMillis - (currentTime - lastUse)) / 1000;
                placeholders.clear();
                placeholders.put("%time%", String.valueOf(remaining));
                send(player, plugin.Message("messages.request_cooldown", placeholders));
                return true;
            }
            requestCooldownMap.put(player.getUniqueId(), currentTime);
            return false;
        }
        return false;
    }

    public void handleVersionCommand(Player player) {
        final String version = plugin.getDescription().getVersion();

        if (!player.hasPermission("brightstpa.version")) {
            send(player, plugin.Message("messages.no_permission", placeholders));
            return;
        }
        placeholders.clear();
        placeholders.put("%version%", version);
        send(player, plugin.Message("messages.version", placeholders));
    }

    public void handleReloadCommand(Player player) {

        if (!player.hasPermission("brightstpa.reload")) {
            send(player, plugin.Message("messages.no_permission", placeholders));
            return;
        }
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            plugin.loadLanguage();
            plugin.loadSettings();
            send(player, plugin.Message("messages.config_reloaded", placeholders));
        }
        catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            send(player, plugin.Message("messages.config_reloaded_failed", placeholders));
        }
    }

    public void handleTpaDenyCommand(Player receivePlayer, String[] args) {

        if (commandIsCooldown(receivePlayer, BrightsTPA.getCommandCooldown(), "tpdeny")) {
            return;
        }
        if (!receivePlayer.hasPermission("brightstpa.tpdeny")) {
            send(receivePlayer, plugin.Message("messages.no_permission", placeholders));
            return;
        }
        if (args.length > 0) {
            denySpecificRequest(receivePlayer, Bukkit.getPlayer(args[0]));
        }
        else {
            denyAnyRequests(receivePlayer);
        }
    }

    public void denyAnyRequests(Player receivePlayer) {
        Player requestPlayer = getLastestRequestReceiver(tpaMap, receivePlayer);
        if (requestPlayer == null) {
            requestPlayer = getLastestRequestReceiver(tpahereMap, receivePlayer);
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (requestPlayer == null) {
            send(receivePlayer, plugin.Message("messages.no_request", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpDenyExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpDenyExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
    }

    public void denySpecificRequest(Player receivePlayer, Player requestPlayer) {
        if (requestPlayer == null) {
            send(receivePlayer, plugin.Message("messages.no_player_found", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        if (requestPlayer.getUniqueId().equals(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpDenyExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpDenyExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
        else {
            placeholders.clear();
            placeholders.put("%player%", requestPlayer.getName());
            send(receivePlayer, plugin.Message("messages.no_request_from_player", placeholders));
        }
    }

    public void tpDenyExecute(Player requestPlayer, Player receivePlayer, String type) {
        Map<UUID, Long> tpaRequests = tpaMap.get(requestPlayer.getUniqueId());
        if (tpaRequests != null) {
            tpaRequests.remove(receivePlayer.getUniqueId());
            if (tpaRequests.isEmpty()) {
                tpaMap.remove(requestPlayer.getUniqueId());
            }
        }

        Map<UUID, Long> tpahereRequests = tpahereMap.get(requestPlayer.getUniqueId());
        if (tpahereRequests != null) {
            tpahereRequests.remove(receivePlayer.getUniqueId());
            if (tpahereRequests.isEmpty()) {
                tpahereMap.remove(requestPlayer.getUniqueId());
            }
        }

        Player tpedPlayer = type.equals(plugin.Message("messages.tpa_type_prefix", placeholders)) ? requestPlayer : receivePlayer;
        Player toPlayer = type.equals(plugin.Message("messages.tpa_type_prefix", placeholders)) ? receivePlayer : requestPlayer;

        placeholders.clear();
        placeholders.put("%type%", type);
        placeholders.put("%player%", tpedPlayer.getName());
        send(toPlayer, plugin.Message("messages.denied_request_from_player", placeholders));
        placeholders.clear();
        placeholders.put("%type%", type);
        placeholders.put("%player%", toPlayer.getName());
        send(tpedPlayer, plugin.Message("messages.denied_request", placeholders));
    }

    public void handleTpaAcceptCommand(Player receivePlayer, String[] args) {

        if (commandIsCooldown(receivePlayer, BrightsTPA.getCommandCooldown(), "tpaccept")) return;

        if (!receivePlayer.hasPermission("brightstpa.tpaccept")) {
            send(receivePlayer, plugin.Message("messages.no_permission", placeholders));
            return;
        }
        if (args.length > 0) {
            acceptSpecificRequest(receivePlayer, Bukkit.getPlayer(args[0]));
        }
        else {
            acceptAnyRequest(receivePlayer);
        }
    }

    public void acceptAnyRequest(Player receivePlayer) {
        Player requestPlayer = getLastestRequestReceiver(tpaMap, receivePlayer);
        if (requestPlayer == null) {
            requestPlayer = getLastestRequestReceiver(tpahereMap, receivePlayer);
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (requestPlayer == null) {
            send(receivePlayer, plugin.Message("messages.no_request", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacceptExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacceptExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
    }

    public void acceptSpecificRequest(Player receivePlayer, Player requestPlayer) {
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (requestPlayer == null) {
            send(receivePlayer, plugin.Message("messages.no_player_found", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        if (requestPlayer.getUniqueId().equals(receivePlayer.getUniqueId())) {
            send(receivePlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacceptExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacceptExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
        else {
            placeholders.clear();
            placeholders.put("%player%", requestPlayer.getName());
            send(receivePlayer, plugin.Message("messages.no_request_from_player", placeholders));
        }
    }

    public Player getLastestRequestReceiver(HashMap<UUID, Map<UUID, Long>> map, Player receivePlayer) {
        UUID requestPlayerUUID = map.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(receivePlayer.getUniqueId()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);

        return requestPlayerUUID != null ? Bukkit.getPlayer(requestPlayerUUID) : null;
    }

    public void handleTpaCommand(Player requestPlayer, String [] args) {
        if (args.length > 0) {
            sendTeleportRequest(requestPlayer, Bukkit.getPlayer(args[0]), tpaMap, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
    }

    public void handleTpahereCommand(Player requestPlayer, String [] args) {
        if (args.length > 0) {
            sendTeleportRequest(requestPlayer, Bukkit.getPlayer(args[0]), tpahereMap, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
    }

    public void sendTeleportRequest(Player requestPlayer, Player receivePlayer, HashMap<UUID, Map<UUID, Long>> map, String type) {
        final int timeoutSecond = BrightsTPA.getRequestTimeout();

        if (type.equals(plugin.Message("messages.tpa_type_prefix", placeholders))) {
            if (!requestPlayer.hasPermission("brightstpa.tpa")) {
                send(requestPlayer, plugin.Message("messages.no_permission", placeholders));
                return;
            }
        } else if (type.equals(plugin.Message("messages.tpahere_type_prefix", placeholders))) {
            if (!requestPlayer.hasPermission("brightstpa.tpahere")) {
                send(requestPlayer, plugin.Message("messages.no_permission", placeholders));
                return;
            }
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.no_request_while_tping", placeholders));
            return;
        }
        if (receivePlayer == null) {
            send(requestPlayer, plugin.Message("messages.player_not_online", placeholders));
            return;
        }
        if (receivePlayer.getUniqueId().equals(requestPlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }

        Map<UUID, Long> requests = map.get(requestPlayer.getUniqueId());
        if (requests != null && requests.containsKey(receivePlayer.getUniqueId())) {
            placeholders.clear();
            placeholders.put("%player%", receivePlayer.getName());
            send(requestPlayer, plugin.Message("messages.already_request", placeholders));
            return;
        }

        if (commandIsCooldown(requestPlayer, BrightsTPA.getCommandCooldown(), type.toLowerCase()) || requestIsCooldown(requestPlayer, BrightsTPA.getRequestCooldown())) {
            return;
        }

        map.computeIfAbsent(requestPlayer.getUniqueId(), k -> new HashMap<>()).put(receivePlayer.getUniqueId(), System.currentTimeMillis());

        placeholders.clear();
        placeholders.put("%player%", receivePlayer.getName());
        placeholders.put("%type%", type);
        send(requestPlayer, plugin.Message("messages.send_request", placeholders));
        placeholders.clear();
        placeholders.put("%player%", requestPlayer.getName());
        placeholders.put("%time%", String.valueOf(timeoutSecond));
        if (type.equals(plugin.Message("messages.tpa_type_prefix", placeholders))) {
            send(receivePlayer, plugin.Message("messages.tpa_message", placeholders));
        }
        else if (type.equals(plugin.Message("messages.tpahere_type_prefix", placeholders))) {
            send(receivePlayer, plugin.Message("messages.tpahere_message", placeholders));
        }
        setRequestTimeout(requestPlayer, receivePlayer, map, type, timeoutSecond * 20L);
    }

    public void setRequestTimeout(Player requestPlayer, Player receivePlayer, HashMap<UUID, Map<UUID, Long>> map, String type, long ticks) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Map<UUID, Long> requests = map.get(requestPlayer.getUniqueId());
                if (requests != null) {
                    requests.remove(receivePlayer.getUniqueId());
                    if (requests.isEmpty()) {
                        map.remove(requestPlayer.getUniqueId());
                    }

                    Player receiver = Bukkit.getPlayer(receivePlayer.getUniqueId());
                    placeholders.clear();
                    placeholders.put("%player%", receivePlayer.getName());
                    placeholders.put("%type%", type);
                    send(requestPlayer, plugin.Message("messages.request_timeout_from_player", placeholders));

                    if (receiver != null && receiver.isOnline()) {
                        placeholders.clear();
                        placeholders.put("%player%", requestPlayer.getName());
                        placeholders.put("%type%", type);
                        send(receiver, plugin.Message("messages.request_timeout", placeholders));
                    }
                }
                timeoutTasksMap.remove(requestPlayer.getUniqueId());
            }
        };
        task.runTaskLater(plugin, ticks);
        timeoutTasksMap.put(requestPlayer.getUniqueId(), task);
    }

    public void removeRequestTimeout(Player requestPlayer) {
        BukkitRunnable task = timeoutTasksMap.remove(requestPlayer.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelTpOnMove(Player tpedPlayer, Player toPlayer, Player requestPlayer) {
        send(tpedPlayer, plugin.Message("messages.tp_cancelled_move_from_player", placeholders));
        placeholders.clear();
        placeholders.put("%player%", tpedPlayer.getName());
        send(toPlayer, plugin.Message("messages.tp_cancelled_move", placeholders));

        Map<UUID, Long> tpaRequests = tpaMap.get(requestPlayer.getUniqueId());
        if (tpaRequests != null) {
            tpaRequests.remove(toPlayer.getUniqueId());
            if (tpaRequests.isEmpty()) {
                tpaMap.remove(requestPlayer.getUniqueId());
            }
        }

        Map<UUID, Long> tpahereRequests = tpahereMap.get(requestPlayer.getUniqueId());
        if (tpahereRequests != null) {
            tpahereRequests.remove(toPlayer.getUniqueId());
            if (tpahereRequests.isEmpty()) {
                tpahereMap.remove(requestPlayer.getUniqueId());
            }
        }

        teleportTasksMap.remove(tpedPlayer.getUniqueId());
    }

    public void handleTpaCancelCommand(Player requestPlayer, String[] args) {

        if (commandIsCooldown(requestPlayer, BrightsTPA.getCommandCooldown(), "tpacancel")) {
            return;
        }
        if (!requestPlayer.hasPermission("brightstpa.tpacancel")) {
            send(requestPlayer, plugin.Message("messages.no_permission", placeholders));
            return;
        }
        if (args.length > 0) {
            cancelSpecificRequest(requestPlayer, Bukkit.getPlayer(args[0]));
        }
        else {
            cancelAnyRequest(requestPlayer);
        }
    }

    public void cancelSpecificRequest(Player requestPlayer, Player receivePlayer) {
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (receivePlayer == null) {
            send(requestPlayer, plugin.Message("messages.no_player_found", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        if (requestPlayer.getUniqueId().equals(receivePlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacancelExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacancelExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
        else {
            placeholders.clear();
            placeholders.put("%player%", receivePlayer.getName());
            send(requestPlayer, plugin.Message("messages.no_request_to_player", placeholders));
        }
    }

    public void cancelAnyRequest(Player requestPlayer) {
        Player receivePlayer = null;

        Map<UUID, Long> tpaRequests = tpaMap.get(requestPlayer.getUniqueId());
        if (tpaRequests != null && !tpaRequests.isEmpty()) {
            UUID firstReceiver = tpaRequests.keySet().iterator().next();
            receivePlayer = Bukkit.getPlayer(firstReceiver);
        }

        if (receivePlayer == null) {
            Map<UUID, Long> tpahereRequests = tpahereMap.get(requestPlayer.getUniqueId());
            if (tpahereRequests != null && !tpahereRequests.isEmpty()) {
                UUID firstReceiver = tpahereRequests.keySet().iterator().next();
                receivePlayer = Bukkit.getPlayer(firstReceiver);
            }
        }

        if (receivePlayer == null) {
            send(requestPlayer, plugin.Message("messages.no_request", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(requestPlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.cant_do_that", placeholders));
            return;
        }
        if (teleportTasksMap.containsKey(receivePlayer.getUniqueId())) {
            send(requestPlayer, plugin.Message("messages.player_is_tping", placeholders));
            return;
        }
        removeRequestTimeout(requestPlayer);

        if (tpaMap.containsKey(requestPlayer.getUniqueId()) && tpaMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacancelExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpa_type_prefix", placeholders));
        }
        else if (tpahereMap.containsKey(requestPlayer.getUniqueId()) && tpahereMap.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId())) {
            tpacancelExecute(requestPlayer, receivePlayer, plugin.Message("messages.tpahere_type_prefix", placeholders));
        }
    }

    public void tpacancelExecute(Player requestPlayer, Player receivePlayer, String type) {
        Map<UUID, Long> tpaRequests = tpaMap.get(requestPlayer.getUniqueId());
        if (tpaRequests != null) {
            tpaRequests.remove(receivePlayer.getUniqueId());
            if (tpaRequests.isEmpty()) {
                tpaMap.remove(requestPlayer.getUniqueId());
            }
        }

        Map<UUID, Long> tpahereRequests = tpahereMap.get(requestPlayer.getUniqueId());
        if (tpahereRequests != null) {
            tpahereRequests.remove(receivePlayer.getUniqueId());
            if (tpahereRequests.isEmpty()) {
                tpahereMap.remove(requestPlayer.getUniqueId());
            }
        }

        placeholders.clear();
        placeholders.put("%type%", type);
        placeholders.put("%player%", receivePlayer.getName());
        send(requestPlayer, plugin.Message("messages.cancel_request_to_player", placeholders));
        placeholders.clear();
        placeholders.put("%type%", type);
        placeholders.put("%player%", requestPlayer.getName());
        send(receivePlayer, plugin.Message("messages.cancel_request", placeholders));
    }

    public void tpacceptExecute(Player requestPlayer, Player receivePlayer, String type) {
        final int delaySeconds = BrightsTPA.getTpDelay();
        final Player tpedPlayer = type.equals(plugin.Message("messages.tpa_type_prefix", placeholders)) ? requestPlayer : receivePlayer;
        final Player toPlayer = type.equals(plugin.Message("messages.tpa_type_prefix", placeholders)) ? receivePlayer : requestPlayer;
        final boolean isCancelOnMove = BrightsTPA.getCancelOnMove();
        final Location startLocation = tpedPlayer.getLocation();
        if (!tpedPlayer.hasPermission("brightstpa.bypass.tpdelay")) {
            placeholders.clear();
            placeholders.put("%time%", String.valueOf(delaySeconds));
            placeholders.put("%type%", type);
            placeholders.put("%player%", toPlayer.getName());
            send(tpedPlayer, plugin.Message("messages.request_accept_from_player", placeholders));
            placeholders.clear();
            placeholders.put("%time%", String.valueOf(delaySeconds));
            placeholders.put("%type%", type);
            placeholders.put("%player%", tpedPlayer.getName());
            send(toPlayer, plugin.Message("messages.request_accept", placeholders));
        }
        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (!tpedPlayer.hasPermission("brightstpa.bypass.tpdelay")) {
                    elapsed += 1;
                }
                else {
                    elapsed += delaySeconds * 20;
                }
                Location currentLocation = tpedPlayer.getLocation();
                if (elapsed >= delaySeconds * 20) {
                    tpExecute(tpedPlayer, toPlayer, requestPlayer);
                    cancel();
                }
                else if (!currentLocation.getBlock().equals(startLocation.getBlock()) && isCancelOnMove) {
                    cancelTpOnMove(tpedPlayer, toPlayer, requestPlayer);
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        teleportTasksMap.put(tpedPlayer.getUniqueId(), task);
    }

    public void tpExecute(Player tpedPlayer, Player toPlayer, Player requestPlayer) {
        if (!tpedPlayer.isOnline() || !toPlayer.isOnline()) {
            send(toPlayer, plugin.Message("messages.player_not_online", placeholders));
            send(tpedPlayer, plugin.Message("messages.player_not_online", placeholders));
        }
        else {
            tpedPlayer.teleport(toPlayer.getLocation());
            send(tpedPlayer, plugin.Message("messages.tp_success_from_player", placeholders));
            placeholders.clear();
            placeholders.put("%player%", tpedPlayer.getName());
            send(toPlayer, plugin.Message("messages.tp_success", placeholders));
        }

        Map<UUID, Long> tpaRequests = tpaMap.get(requestPlayer.getUniqueId());
        if (tpaRequests != null) {
            tpaRequests.remove(toPlayer.getUniqueId());
            if (tpaRequests.isEmpty()) {
                tpaMap.remove(requestPlayer.getUniqueId());
            }
        }

        Map<UUID, Long> tpahereRequests = tpahereMap.get(requestPlayer.getUniqueId());
        if (tpahereRequests != null) {
            tpahereRequests.remove(toPlayer.getUniqueId());
            if (tpahereRequests.isEmpty()) {
                tpahereMap.remove(requestPlayer.getUniqueId());
            }
        }

        teleportTasksMap.remove(tpedPlayer.getUniqueId());
    }
}