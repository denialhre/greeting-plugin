package com.example.swapbingo.commands;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command handler for /swapbingo admin commands.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final SwapBingoPlugin plugin;

    // Config options that can be edited
    private static final List<String> CONFIG_OPTIONS = List.of(
        "swap-enabled",
        "swap-interval",
        "min-players",
        "win-pattern",
        "reset-on-win",
        "debug"
    );

    public AdminCommand(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("swapbingo.admin")) {
            sendMessage(sender, "&cYou don't have permission to use admin commands!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender, args);
            case "reset" -> handleReset(sender, args);
            case "config" -> handleConfig(sender, args);
            case "debug" -> handleDebug(sender);
            case "points" -> handlePoints(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getConfigManager().reload();
        sendMessage(sender, "&aConfiguration reloaded!");
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cThis command can only be used by players!");
            return true;
        }

        Location loc = player.getLocation();
        sendMessage(sender, "&6=== Debug Info ===");

        // Show biome
        String biome = loc.getWorld().getBiome(loc).name();
        sendMessage(sender, "&7Biome: &a" + biome);

        // Show coordinates
        sendMessage(sender, "&7Location: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

        // Scan for nearby structures (this is expensive, but only on-demand)
        sendMessage(sender, "&7Scanning for structures...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            StringBuilder found = new StringBuilder();
            int count = 0;
            int searchRadius = 150;

            for (Structure structure : registry) {
                try {
                    StructureSearchResult result = loc.getWorld().locateNearestStructure(
                        loc, structure, searchRadius, false);

                    if (result != null) {
                        double dist = loc.distance(result.getLocation());
                        if (dist <= searchRadius) {
                            NamespacedKey key = registry.getKey(structure);
                            String name = key != null ? key.getKey() : "unknown";

                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                sendMessage(sender, "&7  - &e" + name + " &7at &f" +
                                    result.getLocation().getBlockX() + ", " + result.getLocation().getBlockZ() +
                                    " &7(" + (int)dist + "m)");
                            });
                            count++;
                        }
                    }
                } catch (Exception ignored) {}
            }

            final int finalCount = count;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalCount == 0) {
                    sendMessage(sender, "&7No structures within " + searchRadius + " blocks");
                } else {
                    sendMessage(sender, "&aFound " + finalCount + " structure(s) nearby");
                }
            });
        });

        return true;
    }

    private boolean handleConfig(CommandSender sender, String[] args) {
        var configManager = plugin.getConfigManager();

        if (args.length < 2) {
            // Show current config values
            sendMessage(sender, "&6=== Current Configuration ===");
            sendMessage(sender, "&7swap-enabled: &e" + configManager.isSwapEnabled());
            sendMessage(sender, "&7swap-interval: &e" + configManager.getSwapIntervalSeconds() + "s");
            sendMessage(sender, "&7min-players: &e" + configManager.getBingoMinPlayers());
            sendMessage(sender, "&7win-pattern: &e" + configManager.getWinPattern());
            sendMessage(sender, "&7reset-on-win: &e" + configManager.shouldResetWorldOnWin());
            sendMessage(sender, "&7debug: &e" + configManager.isDebugEnabled());
            sendMessage(sender, "");
            sendMessage(sender, "&7Use &e/swapbingo config <option> <value> &7to change");
            return true;
        }

        String option = args[1].toLowerCase();

        if (args.length < 3) {
            // Show specific value
            switch (option) {
                case "swap-enabled" -> sendMessage(sender, "&7swap-enabled: &e" + configManager.isSwapEnabled());
                case "swap-interval" -> sendMessage(sender, "&7swap-interval: &e" + configManager.getSwapIntervalSeconds() + "s");
                case "min-players" -> sendMessage(sender, "&7min-players: &e" + configManager.getBingoMinPlayers());
                case "win-pattern" -> sendMessage(sender, "&7win-pattern: &e" + configManager.getWinPattern());
                case "reset-on-win" -> sendMessage(sender, "&7reset-on-win: &e" + configManager.shouldResetWorldOnWin());
                case "debug" -> sendMessage(sender, "&7debug: &e" + configManager.isDebugEnabled());
                default -> sendMessage(sender, "&cUnknown config option: " + option);
            }
            return true;
        }

        String value = args[2];

        try {
            switch (option) {
                case "swap-enabled" -> {
                    boolean enabled = parseBoolean(value);
                    configManager.setSwapEnabled(enabled);
                    sendMessage(sender, "&aSwap " + (enabled ? "enabled" : "disabled") + "!");
                    if (plugin.getBingoGame().isRunning()) {
                        sendMessage(sender, "&7Note: Changes take effect next round.");
                    }
                }
                case "swap-interval" -> {
                    int seconds = Integer.parseInt(value);
                    if (seconds < 10) {
                        sendMessage(sender, "&cMinimum interval is 10 seconds!");
                        return true;
                    }
                    if (seconds > 3600) {
                        sendMessage(sender, "&cMaximum interval is 3600 seconds (1 hour)!");
                        return true;
                    }
                    configManager.setSwapIntervalSeconds(seconds);
                    sendMessage(sender, "&aSwap interval set to &e" + seconds + "&a seconds!");
                    if (plugin.getBingoGame().isRunning()) {
                        sendMessage(sender, "&7Note: Changes take effect next round.");
                    }
                }
                case "min-players" -> {
                    int minPlayers = Integer.parseInt(value);
                    if (minPlayers < 1) {
                        sendMessage(sender, "&cMinimum players must be at least 1!");
                        return true;
                    }
                    if (minPlayers > 100) {
                        sendMessage(sender, "&cMaximum is 100 players!");
                        return true;
                    }
                    configManager.setBingoMinPlayers(minPlayers);
                    sendMessage(sender, "&aMinimum players set to &e" + minPlayers + "&a!");
                }
                case "win-pattern" -> {
                    String pattern = value.toUpperCase();
                    if (!pattern.equals("ROW") && !pattern.equals("COLUMN") && !pattern.equals("RANDOM")) {
                        sendMessage(sender, "&cValid patterns: ROW, COLUMN, RANDOM");
                        return true;
                    }
                    configManager.setWinPattern(pattern);
                    sendMessage(sender, "&aWin pattern set to &e" + pattern + "&a!");
                    if (plugin.getBingoGame().isRunning()) {
                        sendMessage(sender, "&7Note: Changes take effect next round.");
                    }
                }
                case "reset-on-win" -> {
                    boolean reset = parseBoolean(value);
                    configManager.setResetWorldOnWin(reset);
                    sendMessage(sender, "&aWorld reset on win " + (reset ? "enabled" : "disabled") + "!");
                }
                case "debug" -> {
                    boolean debug = parseBoolean(value);
                    configManager.setDebugEnabled(debug);
                    sendMessage(sender, "&aDebug mode " + (debug ? "&aenabled" : "&cdisabled") + "&a!");
                    if (debug) {
                        sendMessage(sender, "&7You will now see biome/structure info in chat.");
                    }
                }
                default -> sendMessage(sender, "&cUnknown config option: " + option);
            }
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number: " + value);
        } catch (IllegalArgumentException e) {
            sendMessage(sender, "&cInvalid value: " + value);
        }

        return true;
    }

    private boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") ||
               value.equalsIgnoreCase("yes") ||
               value.equalsIgnoreCase("on") ||
               value.equals("1");
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show own stats or general stats
            if (sender instanceof Player player) {
                showPlayerStats(sender, player.getUniqueId(), player.getName());
            } else {
                sendMessage(sender, "&7Usage: /swapbingo stats <player>");
            }
            return true;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target != null) {
            showPlayerStats(sender, target.getUniqueId(), target.getName());
        } else {
            // Try offline player
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            if (offline.hasPlayedBefore()) {
                showPlayerStats(sender, offline.getUniqueId(), playerName);
            } else {
                sendMessage(sender, "&cPlayer not found: " + playerName);
            }
        }

        return true;
    }

    private void showPlayerStats(CommandSender sender, UUID playerId, String playerName) {
        var winTracker = plugin.getWinTracker();

        int bingoWins = winTracker.getBingoWins(playerId);

        sendMessage(sender, "&6=== Stats for " + playerName + " ===");
        sendMessage(sender, "&7Bingo Wins: &e" + bingoWins);
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "&7Usage: /swapbingo reset <player>");
            return true;
        }

        String playerName = args[1];

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendMessage(sender, "&cPlayer not found: " + playerName);
            return true;
        }

        plugin.getWinTracker().resetPlayer(target.getUniqueId());
        sendMessage(sender, "&aReset stats for " + playerName);

        return true;
    }

    private boolean handlePoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendPointsHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sendMessage(sender, "&7Usage: /swapbingo points add <player> <amount>");
                    return true;
                }
                return modifyPoints(sender, args[2], args[3], "add");
            }
            case "remove" -> {
                if (args.length < 4) {
                    sendMessage(sender, "&7Usage: /swapbingo points remove <player> <amount>");
                    return true;
                }
                return modifyPoints(sender, args[2], args[3], "remove");
            }
            case "set" -> {
                if (args.length < 4) {
                    sendMessage(sender, "&7Usage: /swapbingo points set <player> <amount>");
                    return true;
                }
                return modifyPoints(sender, args[2], args[3], "set");
            }
            case "get", "check" -> {
                if (args.length < 3) {
                    sendMessage(sender, "&7Usage: /swapbingo points get <player>");
                    return true;
                }
                return getPoints(sender, args[2]);
            }
            default -> {
                sendPointsHelp(sender);
                return true;
            }
        }
    }

    private boolean modifyPoints(CommandSender sender, String playerName, String amountStr, String action) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sendMessage(sender, "&cPlayer not found or not online: " + playerName);
            return true;
        }

        if (!plugin.getBingoGame().isParticipant(target.getUniqueId())) {
            sendMessage(sender, "&c" + playerName + " is not in a game!");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount < 0) {
                sendMessage(sender, "&cAmount must be positive!");
                return true;
            }
        } catch (NumberFormatException e) {
            sendMessage(sender, "&cInvalid number: " + amountStr);
            return true;
        }

        var pointsManager = plugin.getPointsManager();
        int currentPoints = pointsManager.getPoints(target.getUniqueId());

        switch (action) {
            case "add" -> {
                pointsManager.addPoints(target.getUniqueId(), amount);
                int newPoints = pointsManager.getPoints(target.getUniqueId());
                sendMessage(sender, "&aAdded &e" + amount + "&a points to &e" + playerName +
                    "&a. Balance: &e" + currentPoints + " &7-> &e" + newPoints);
                MessageUtil.send(target, "&a+" + amount + " points! &7(Admin) &aNew balance: &e" + newPoints);
            }
            case "remove" -> {
                int toRemove = Math.min(amount, currentPoints);
                pointsManager.removePoints(target.getUniqueId(), toRemove);
                int newPoints = pointsManager.getPoints(target.getUniqueId());
                sendMessage(sender, "&cRemoved &e" + toRemove + "&c points from &e" + playerName +
                    "&c. Balance: &e" + currentPoints + " &7-> &e" + newPoints);
                MessageUtil.send(target, "&c-" + toRemove + " points! &7(Admin) &cNew balance: &e" + newPoints);
            }
            case "set" -> {
                var session = plugin.getGameManager().getSessionIfExists(target.getUniqueId());
                if (session != null) {
                    session.setPoints(amount);
                    sendMessage(sender, "&aSet &e" + playerName + "&a's points to &e" + amount +
                        "&a. (was &e" + currentPoints + "&a)");
                    MessageUtil.send(target, "&7Your points have been set to &e" + amount + " &7(Admin)");
                }
            }
        }

        return true;
    }

    private boolean getPoints(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sendMessage(sender, "&cPlayer not found or not online: " + playerName);
            return true;
        }

        if (!plugin.getBingoGame().isParticipant(target.getUniqueId())) {
            sendMessage(sender, "&c" + playerName + " is not in a game!");
            return true;
        }

        int points = plugin.getPointsManager().getPoints(target.getUniqueId());
        sendMessage(sender, "&e" + playerName + "&7's points: &a" + points);

        return true;
    }

    private void sendPointsHelp(CommandSender sender) {
        sendMessage(sender, "&6=== Points Management ===");
        sendMessage(sender, "&e/swapbingo points add <player> <amount> &7- Give points");
        sendMessage(sender, "&e/swapbingo points remove <player> <amount> &7- Remove points");
        sendMessage(sender, "&e/swapbingo points set <player> <amount> &7- Set points");
        sendMessage(sender, "&e/swapbingo points get <player> &7- Check points");
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "&6=== SwapBingo Admin Commands ===");
        sendMessage(sender, "&e/swapbingo reload &7- Reload configuration");
        sendMessage(sender, "&e/swapbingo config &7- View/edit settings");
        sendMessage(sender, "&e/swapbingo config <option> <value> &7- Change setting");
        sendMessage(sender, "&e/swapbingo debug &7- Show nearby biome/structures");
        sendMessage(sender, "&e/swapbingo stats [player] &7- Show player stats");
        sendMessage(sender, "&e/swapbingo reset <player> &7- Reset player stats");
        sendMessage(sender, "&e/swapbingo points &7- Manage player points");
        sendMessage(sender, "");
        sendMessage(sender, "&7Config options: swap-enabled, swap-interval,");
        sendMessage(sender, "&7  min-players, win-pattern, reset-on-win, debug");
    }

    private void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            MessageUtil.send(player, message);
        } else {
            sender.sendMessage(MessageUtil.fromLegacy(message));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();

            for (String sub : List.of("reload", "stats", "reset", "config", "debug", "points")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (subCmd.equals("stats") || subCmd.equals("reset")) {
                List<String> completions = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
                return completions;
            }

            if (subCmd.equals("config")) {
                List<String> completions = new ArrayList<>();
                for (String option : CONFIG_OPTIONS) {
                    if (option.startsWith(partial)) {
                        completions.add(option);
                    }
                }
                return completions;
            }

            if (subCmd.equals("points")) {
                List<String> completions = new ArrayList<>();
                for (String option : List.of("add", "remove", "set", "get")) {
                    if (option.startsWith(partial)) {
                        completions.add(option);
                    }
                }
                return completions;
            }
        }

        // Tab complete player names for points commands
        if (args.length == 3 && args[0].equalsIgnoreCase("points")) {
            String action = args[1].toLowerCase();
            if (List.of("add", "remove", "set", "get").contains(action)) {
                List<String> completions = new ArrayList<>();
                String partial = args[2].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
                return completions;
            }
        }

        // Tab complete amounts for points commands
        if (args.length == 4 && args[0].equalsIgnoreCase("points")) {
            String action = args[1].toLowerCase();
            if (List.of("add", "remove", "set").contains(action)) {
                return List.of("1", "5", "10", "25", "50", "100");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            String option = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            List<String> completions = new ArrayList<>();

            switch (option) {
                case "swap-enabled", "reset-on-win", "debug" -> {
                    for (String val : List.of("true", "false")) {
                        if (val.startsWith(partial)) {
                            completions.add(val);
                        }
                    }
                }
                case "swap-interval" -> {
                    for (String val : List.of("60", "120", "180", "300")) {
                        if (val.startsWith(partial)) {
                            completions.add(val);
                        }
                    }
                }
                case "min-players" -> {
                    for (String val : List.of("1", "2", "3", "4")) {
                        if (val.startsWith(partial)) {
                            completions.add(val);
                        }
                    }
                }
                case "win-pattern" -> {
                    for (String val : List.of("ROW", "COLUMN", "RANDOM")) {
                        if (val.toLowerCase().startsWith(partial)) {
                            completions.add(val);
                        }
                    }
                }
            }
            return completions;
        }

        return List.of();
    }
}
