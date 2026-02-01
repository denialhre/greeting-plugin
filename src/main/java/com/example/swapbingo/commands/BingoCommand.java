package com.example.swapbingo.commands;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.gui.BingoCardGUI;
import com.example.swapbingo.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command handler for /bingo.
 */
public class BingoCommand implements CommandExecutor, TabCompleter {

    private final SwapBingoPlugin plugin;
    private final BingoCardGUI cardGUI;

    public BingoCommand(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.cardGUI = new BingoCardGUI(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "card" -> handleCard(sender);
            case "status" -> handleStatus(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleStart(CommandSender sender) {
        if (!sender.hasPermission("swapbingo.bingo.start")) {
            sendMessage(sender, "&cYou don't have permission to start Bingo!");
            return true;
        }

        if (!plugin.getConfigManager().isBingoEnabled()) {
            sendMessage(sender, "&cBingo is disabled in the config!");
            return true;
        }

        var bingo = plugin.getBingoGame();
        if (bingo.isRunning()) {
            sendMessage(sender, "&cBingo is already running!");
            return true;
        }

        if (plugin.getGameManager().isAnyGameRunning()) {
            sendMessage(sender, "&cAnother game is already running!");
            return true;
        }

        // Get all online players
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());

        int minPlayers = plugin.getConfigManager().getBingoMinPlayers();
        if (players.size() < minPlayers) {
            sendMessage(sender, "&cNot enough players! Need at least " + minPlayers);
            return true;
        }

        if (bingo.start(players)) {
            sendMessage(sender, "&aBingo started!");
        } else {
            sendMessage(sender, "&cFailed to start Bingo!");
        }

        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("swapbingo.bingo.stop")) {
            sendMessage(sender, "&cYou don't have permission to stop Bingo!");
            return true;
        }

        var bingo = plugin.getBingoGame();
        if (!bingo.isRunning()) {
            String msg = plugin.getConfigManager().getBingoMessage("no-game-running");
            sendMessage(sender, msg.isEmpty() ? "&cNo bingo game is running!" : msg);
            return true;
        }

        bingo.stop();
        sendMessage(sender, "&aBingo stopped!");
        return true;
    }

    private boolean handleCard(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        var bingo = plugin.getBingoGame();
        if (!bingo.isRunning()) {
            String msg = plugin.getConfigManager().getBingoMessage("no-game-running");
            sendMessage(sender, msg.isEmpty() ? "&cNo bingo game is running!" : msg);
            return true;
        }

        if (!bingo.isParticipant(player.getUniqueId())) {
            sendMessage(sender, "&cYou are not in the current game!");
            return true;
        }

        BingoCard card = bingo.getCard(player);
        if (card == null) {
            sendMessage(sender, "&cYou don't have a bingo card!");
            return true;
        }

        // Open GUI with game context
        cardGUI.open(player, card, bingo);
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        var bingo = plugin.getBingoGame();

        if (!bingo.isRunning()) {
            sendMessage(sender, "&7Bingo: &cNot running");
            return true;
        }

        int players = bingo.getParticipants().size();
        String pattern = bingo.getCurrentPattern().name().toLowerCase();

        sendMessage(sender, "&7Bingo: &aRunning");
        sendMessage(sender, "&7Players: &e" + players);
        sendMessage(sender, "&7Win pattern: &e" + pattern);

        // If sender is a player in the game, show their progress
        if (sender instanceof Player player && bingo.isParticipant(player.getUniqueId())) {
            BingoCard card = bingo.getCard(player);
            if (card != null) {
                sendMessage(sender, "&7Your progress: &e" +
                    card.getCompletedCount() + "/" + card.getTotalTasks());
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "&6=== Bingo Commands ===");
        sendMessage(sender, "&e/bingo start &7- Start a new game");
        sendMessage(sender, "&e/bingo stop &7- Stop the current game");
        sendMessage(sender, "&e/bingo card &7- View your bingo card");
        sendMessage(sender, "&e/bingo status &7- Show game status");
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

            for (String sub : List.of("start", "stop", "card", "status")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return List.of();
    }
}
