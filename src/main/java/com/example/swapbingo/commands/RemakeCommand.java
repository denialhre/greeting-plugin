package com.example.swapbingo.commands;

import com.example.swapbingo.SwapBingoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for starting and voting in remake votes.
 */
public class RemakeCommand implements CommandExecutor, TabCompleter {

    private final SwapBingoPlugin plugin;

    public RemakeCommand(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return true;
        }

        // Check if player is in a game
        if (!plugin.getBingoGame().isParticipant(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be in a game to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        var voteManager = plugin.getRemakeVoteManager();

        if (args.length == 0) {
            // Start a new vote
            voteManager.startVote(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "yes", "y" -> {
                voteManager.castVote(player, true);
            }
            case "no", "n" -> {
                voteManager.castVote(player, false);
            }
            case "status" -> {
                if (voteManager.isVoteInProgress()) {
                    player.sendMessage(Component.text("A remake vote is currently in progress.")
                        .color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("No remake vote is currently in progress.")
                        .color(NamedTextColor.GRAY));
                }
            }
            default -> {
                player.sendMessage(Component.text("Usage: /remake [yes|no|status]")
                    .color(NamedTextColor.YELLOW));
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String option : List.of("yes", "no", "status")) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
        }

        return completions;
    }
}
