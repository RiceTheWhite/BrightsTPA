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

    private void removeRequestFromMaps(Player requestPlayer, Player receivePlayer) {
        removeRequestFromMap(tpaMap, requestPlayer, receivePlayer);
        removeRequestFromMap(tpahereMap, requestPlayer, receivePlayer);
    }

    private void removeRequestFromMap(HashMap<UUID, Map<UUID, Long>> map, Player requestPlayer, Player receivePlayer) {
        Map<UUID, Long> requests = map.get(requestPlayer.getUniqueId());
        if (requests != null) {
            requests.remove(receivePlayer.getUniqueId());
            if (requests.isEmpty()) {
                map.remove(requestPlayer.getUniqueId());
            }
        }
    }

    private boolean isPlayerTeleporting(Player player, String errorMessageKey) {
        if (teleportTasksMap.containsKey(player.getUniqueId())) {
            send(player, plugin.Message(errorMessageKey, placeholders));
            return true;
        }
        return false;
    }

    private boolean failsBasicRequestValidation(Player requester, Player target) {
        if (isPlayerTeleporting(requester, "messages.cant_do_that")) {
            return true;
        }

        if (target == null) {
            send(requester, plugin.Message("messages.no_player_found", placeholders));
            return true;
        }

        if (isPlayerTeleporting(target, "messages.player_is_tping")) {
            return true;
        }

        if (target.getUniqueId().equals(requester.getUniqueId())) {
            send(requester, plugin.Message("messages.cant_do_that", placeholders));
            return true;
        }

        return false;
    }

    private String getTpaTypePrefix() {
        return plugin.Message("messages.tpa_type_prefix", placeholders);
    }

    private String getTpahereTypePrefix() {
        return plugin.Message("messages.tpahere_type_prefix", placeholders);
    }

    private boolean hasRequestInMap(HashMap<UUID, Map<UUID, Long>> map, Player requestPlayer, Player receivePlayer) {
        return map.containsKey(requestPlayer.getUniqueId())
                && map.get(requestPlayer.getUniqueId()).containsKey(receivePlayer.getUniqueId());
    }

    private String getRequestType(Player requestPlayer, Player receivePlayer) {
        if (hasRequestInMap(tpaMap, requestPlayer, receivePlayer)) {
            return getTpaTypePrefix();
        } else if (hasRequestInMap(tpahereMap, requestPlayer, receivePlayer)) {
            return getTpahereTypePrefix();
        }
        return null;
    }

    @FunctionalInterface
    private interface RequestExecutor {
        void execute(Player requestPlayer, Player receivePlayer, String type);
    }

    private void processAnyRequest(Player receivePlayer, RequestExecutor executor) {
        Player requestPlayer = getLastestRequestReceiver(tpaMap, receivePlayer);
        if (requestPlayer == null) {
            requestPlayer = getLastestRequestReceiver(tpahereMap, receivePlayer);
        }

        if (isPlayerTeleporting(receivePlayer, "messages.cant_do_that")) {
            return;
        }

        if (requestPlayer == null) {
            send(receivePlayer, plugin.Message("messages.no_request", placeholders));
            return;
        }

        if (isPlayerTeleporting(requestPlayer, "messages.player_is_tping")) {
            return;
        }

        removeRequestTimeout(requestPlayer);
        String type = getRequestType(requestPlayer, receivePlayer);
        if (type != null) {
            executor.execute(requestPlayer, receivePlayer, type);
        }
    }

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
        final String version = plugin.getPluginMeta().getVersion();

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
        } else {
            denyAnyRequests(receivePlayer);
        }
    }

    public void denyAnyRequests(Player receivePlayer) {
        processAnyRequest(receivePlayer, this::tpDenyExecute);
    }

    public void denySpecificRequest(Player receivePlayer, Player requestPlayer) {
        if (failsBasicRequestValidation(receivePlayer, requestPlayer)) {
            return;
        }

        removeRequestTimeout(requestPlayer);
        String type = getRequestType(requestPlayer, receivePlayer);

        if (type != null) {
            tpDenyExecute(requestPlayer, receivePlayer, type);
        } else {
            placeholders.clear();
            placeholders.put("%player%", requestPlayer.getName());
            send(receivePlayer, plugin.Message("messages.no_request_from_player", placeholders));
        }
    }

    public void tpDenyExecute(Player requestPlayer, Player receivePlayer, String type) {
        removeRequestFromMaps(requestPlayer, receivePlayer);

        Player tpedPlayer = type.equals(getTpaTypePrefix()) ? requestPlayer : receivePlayer;
        Player toPlayer = type.equals(getTpaTypePrefix()) ? receivePlayer : requestPlayer;

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
        } else {
            acceptAnyRequest(receivePlayer);
        }
    }

    public void acceptAnyRequest(Player receivePlayer) {
        processAnyRequest(receivePlayer, this::tpacceptExecute);
    }

    public void acceptSpecificRequest(Player receivePlayer, Player requestPlayer) {
        if (failsBasicRequestValidation(receivePlayer, requestPlayer)) {
            return;
        }

        removeRequestTimeout(requestPlayer);
        String type = getRequestType(requestPlayer, receivePlayer);

        if (type != null) {
            tpacceptExecute(requestPlayer, receivePlayer, type);
        } else {
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
            sendTeleportRequest(requestPlayer, Bukkit.getPlayer(args[0]), tpaMap, getTpaTypePrefix());
        }
    }

    public void handleTpahereCommand(Player requestPlayer, String [] args) {
        if (args.length > 0) {
            sendTeleportRequest(requestPlayer, Bukkit.getPlayer(args[0]), tpahereMap, getTpahereTypePrefix());
        }
    }

    public void sendTeleportRequest(Player requestPlayer, Player receivePlayer, HashMap<UUID, Map<UUID, Long>> map, String type) {
        final int timeoutSecond = BrightsTPA.getRequestTimeout();

        if (type.equals(getTpaTypePrefix())) {
            if (!requestPlayer.hasPermission("brightstpa.tpa")) {
                send(requestPlayer, plugin.Message("messages.no_permission", placeholders));
                return;
            }
        } else if (type.equals(getTpahereTypePrefix())) {
            if (!requestPlayer.hasPermission("brightstpa.tpahere")) {
                send(requestPlayer, plugin.Message("messages.no_permission", placeholders));
                return;
            }
        }

        if (isPlayerTeleporting(requestPlayer, "messages.no_request_while_tping")) {
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

        if (commandIsCooldown(requestPlayer, BrightsTPA.getCommandCooldown(), type.toLowerCase())
                || requestIsCooldown(requestPlayer, BrightsTPA.getRequestCooldown())) {
            return;
        }

        map.computeIfAbsent(requestPlayer.getUniqueId(), k -> new HashMap<>())
                .put(receivePlayer.getUniqueId(), System.currentTimeMillis());

        placeholders.clear();
        placeholders.put("%player%", receivePlayer.getName());
        placeholders.put("%type%", type);
        send(requestPlayer, plugin.Message("messages.send_request", placeholders));

        placeholders.clear();
        placeholders.put("%player%", requestPlayer.getName());
        placeholders.put("%time%", String.valueOf(timeoutSecond));

        if (type.equals(getTpaTypePrefix())) {
            send(receivePlayer, plugin.Message("messages.tpa_message", placeholders));
        } else if (type.equals(getTpahereTypePrefix())) {
            send(receivePlayer, plugin.Message("messages.tpahere_message", placeholders));
        }

        setRequestTimeout(requestPlayer, receivePlayer, map, type, timeoutSecond * 20L);
    }

    public void setRequestTimeout(Player requestPlayer, Player receivePlayer, HashMap<UUID, Map<UUID, Long>> map, String type, long ticks) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                removeRequestFromMap(map, requestPlayer, receivePlayer);

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

        removeRequestFromMaps(requestPlayer, toPlayer);
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
        } else {
            cancelAnyRequest(requestPlayer);
        }
    }

    public void cancelSpecificRequest(Player requestPlayer, Player receivePlayer) {
        if (failsBasicRequestValidation(requestPlayer, receivePlayer)) {
            return;
        }

        removeRequestTimeout(requestPlayer);
        String type = getRequestType(requestPlayer, receivePlayer);

        if (type != null) {
            tpacancelExecute(requestPlayer, receivePlayer, type);
        } else {
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

        if (isPlayerTeleporting(requestPlayer, "messages.cant_do_that")) {
            return;
        }

        if (isPlayerTeleporting(receivePlayer, "messages.player_is_tping")) {
            return;
        }

        removeRequestTimeout(requestPlayer);
        String type = getRequestType(requestPlayer, receivePlayer);
        if (type != null) {
            tpacancelExecute(requestPlayer, receivePlayer, type);
        }
    }

    public void tpacancelExecute(Player requestPlayer, Player receivePlayer, String type) {
        removeRequestFromMaps(requestPlayer, receivePlayer);

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
        final Player tpedPlayer = type.equals(getTpaTypePrefix()) ? requestPlayer : receivePlayer;
        final Player toPlayer = type.equals(getTpaTypePrefix()) ? receivePlayer : requestPlayer;
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
                } else {
                    elapsed += delaySeconds * 20;
                }

                Location currentLocation = tpedPlayer.getLocation();
                if (elapsed >= delaySeconds * 20) {
                    tpExecute(tpedPlayer, toPlayer, requestPlayer);
                    cancel();
                } else if (!currentLocation.getBlock().equals(startLocation.getBlock()) && isCancelOnMove) {
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
        } else {
            tpedPlayer.teleport(toPlayer.getLocation());
            send(tpedPlayer, plugin.Message("messages.tp_success_from_player", placeholders));

            placeholders.clear();
            placeholders.put("%player%", tpedPlayer.getName());
            send(toPlayer, plugin.Message("messages.tp_success", placeholders));
        }

        removeRequestFromMaps(requestPlayer, toPlayer);
        teleportTasksMap.remove(tpedPlayer.getUniqueId());
    }
}