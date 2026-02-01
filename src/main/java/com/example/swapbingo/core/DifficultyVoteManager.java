package com.example.swapbingo.core;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.DifficultyVoteItem;
import com.example.swapbingo.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * Manages the difficulty voting phase before game starts.
 */
public class DifficultyVoteManager {

    private final SwapBingoPlugin plugin;

    private boolean votingActive = false;
    private final Map<UUID, Difficulty> votes = new HashMap<>();
    private Difficulty selectedDifficulty = Difficulty.MEDIUM;
    private BukkitRunnable voteTask;
    private List<Player> votingPlayers = new ArrayList<>();
    private Runnable onVoteComplete;

    public DifficultyVoteManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the difficulty voting phase.
     */
    public void startVote(List<Player> players, Runnable onComplete) {
        if (votingActive) {
            return;
        }

        this.votingPlayers = new ArrayList<>(players);
        this.onVoteComplete = onComplete;
        this.votes.clear();
        this.votingActive = true;

        int duration = plugin.getConfigManager().getDifficultyVoteDuration();

        // Give terracotta items to all players
        for (Player player : players) {
            DifficultyVoteItem.giveAll(player, null);
        }

        // Create boss bar
        var bossBarManager = plugin.getBossBarManager();
        bossBarManager.createDifficultyVoteBar(duration);
        for (Player player : players) {
            bossBarManager.addPlayerToDifficultyVoteBar(player);
        }

        // Show title
        Title voteTitle = Title.title(
            Component.text("Vote for Difficulty!").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            Component.text("Click terracotta blocks to vote").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
        );

        for (Player player : players) {
            player.showTitle(voteTitle);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }

        MessageUtil.broadcast("&e&lDifficulty Vote Started!");
        MessageUtil.broadcast("&7Click the terracotta blocks to vote!");
        MessageUtil.broadcast("&a[Easy] &e[Medium] &c[Hard]");

        // Start countdown
        final int totalDuration = duration;
        voteTask = new BukkitRunnable() {
            int remaining = duration;

            @Override
            public void run() {
                if (!votingActive) {
                    cancel();
                    return;
                }

                // Update boss bar
                int[] voteCounts = getVoteCounts();
                bossBarManager.updateDifficultyVoteBar(remaining, totalDuration,
                    voteCounts[0], voteCounts[1], voteCounts[2]);

                if (remaining <= 0) {
                    endVote();
                    cancel();
                    return;
                }

                // Show warnings at specific times
                if (remaining <= 3 && remaining > 0) {
                    for (Player player : votingPlayers) {
                        if (player.isOnline()) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                }

                remaining--;
            }
        };

        voteTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Cast a vote for a difficulty.
     */
    public void castVote(Player player, Difficulty difficulty) {
        if (!votingActive) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Difficulty previousVote = votes.get(uuid);

        // Update vote
        votes.put(uuid, difficulty);

        // Update visual selection (enchant glint)
        DifficultyVoteItem.updateSelection(player, difficulty);

        // Notify player
        if (previousVote == null) {
            player.sendMessage(Component.text("You voted for ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(difficulty.getDisplayName())
                    .color(difficulty.getColor())
                    .decorate(TextDecoration.BOLD))
                .append(Component.text(" difficulty!")
                    .color(NamedTextColor.GRAY)));
        } else if (previousVote != difficulty) {
            player.sendMessage(Component.text("Changed vote to ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(difficulty.getDisplayName())
                    .color(difficulty.getColor())
                    .decorate(TextDecoration.BOLD))
                .append(Component.text("!")
                    .color(NamedTextColor.GRAY)));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /**
     * End the vote and determine the result.
     */
    private void endVote() {
        votingActive = false;

        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        // Remove boss bar
        plugin.getBossBarManager().removeDifficultyVoteBar();

        // Remove vote items from all players
        for (Player player : votingPlayers) {
            if (player.isOnline()) {
                DifficultyVoteItem.removeAll(player);
            }
        }

        // Calculate result
        selectedDifficulty = calculateResult();

        // Announce result
        MessageUtil.broadcast("");
        MessageUtil.broadcast("&e&lDifficulty Selected: " +
            (selectedDifficulty == Difficulty.EASY ? "&a" :
             selectedDifficulty == Difficulty.MEDIUM ? "&e" : "&c") +
            selectedDifficulty.getDisplayName().toUpperCase());

        int[] counts = getVoteCounts();
        MessageUtil.broadcast("&7Votes: &aEasy(" + counts[0] + ") &eMedium(" + counts[1] + ") &cHard(" + counts[2] + ")");
        MessageUtil.broadcast("");

        // Show title
        Title resultTitle = Title.title(
            Component.text(selectedDifficulty.getDisplayName().toUpperCase())
                .color(selectedDifficulty.getColor())
                .decorate(TextDecoration.BOLD),
            Component.text("Difficulty Selected!").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        );

        for (Player player : votingPlayers) {
            if (player.isOnline()) {
                player.showTitle(resultTitle);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        // Clear state
        votes.clear();

        // Trigger callback after a short delay
        if (onVoteComplete != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, onVoteComplete, 40L); // 2 seconds
        }
    }

    /**
     * Calculate the winning difficulty.
     * On tie, prefer lower difficulty (Easy > Medium > Hard).
     */
    private Difficulty calculateResult() {
        int[] counts = getVoteCounts();
        int easyVotes = counts[0];
        int mediumVotes = counts[1];
        int hardVotes = counts[2];

        // Find max votes
        int maxVotes = Math.max(easyVotes, Math.max(mediumVotes, hardVotes));

        // If no votes, use default
        if (maxVotes == 0) {
            return Difficulty.fromString(plugin.getConfigManager().getDefaultDifficulty());
        }

        // Check for ties - prefer easier difficulty
        if (easyVotes == maxVotes) {
            return Difficulty.EASY;
        } else if (mediumVotes == maxVotes) {
            return Difficulty.MEDIUM;
        } else {
            return Difficulty.HARD;
        }
    }

    /**
     * Get vote counts [easy, medium, hard].
     */
    private int[] getVoteCounts() {
        int easy = 0, medium = 0, hard = 0;
        for (Difficulty d : votes.values()) {
            switch (d) {
                case EASY -> easy++;
                case MEDIUM -> medium++;
                case HARD -> hard++;
            }
        }
        return new int[]{easy, medium, hard};
    }

    /**
     * Check if voting is currently active.
     */
    public boolean isVotingActive() {
        return votingActive;
    }

    /**
     * Get the selected difficulty (after vote completes).
     */
    public Difficulty getSelectedDifficulty() {
        return selectedDifficulty;
    }

    /**
     * Cancel the vote (for cleanup).
     */
    public void cancelVote() {
        if (votingActive) {
            votingActive = false;
            if (voteTask != null) {
                voteTask.cancel();
                voteTask = null;
            }
            plugin.getBossBarManager().removeDifficultyVoteBar();
            for (Player player : votingPlayers) {
                if (player.isOnline()) {
                    DifficultyVoteItem.removeAll(player);
                }
            }
            votes.clear();
            votingPlayers.clear();
            onVoteComplete = null;
        }
    }

    /**
     * Reset for a new game.
     */
    public void reset() {
        cancelVote();
        selectedDifficulty = Difficulty.MEDIUM;
    }
}
