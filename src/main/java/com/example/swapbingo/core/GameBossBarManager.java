package com.example.swapbingo.core;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Manages boss bars for game timers (grace period, swap timer, difficulty vote).
 */
public class GameBossBarManager {

    private BossBar gracePeriodBar;
    private BossBar swapTimerBar;
    private BossBar difficultyVoteBar;

    public GameBossBarManager() {
        // No initialization needed
    }

    // ==================== GRACE PERIOD BAR ====================

    /**
     * Create the grace period boss bar.
     */
    public void createGracePeriodBar(int totalSeconds) {
        if (gracePeriodBar != null) {
            gracePeriodBar.removeAll();
        }

        gracePeriodBar = Bukkit.createBossBar(
            "Grace Period: " + formatTime(totalSeconds),
            BarColor.GREEN,
            BarStyle.SEGMENTED_10
        );
        gracePeriodBar.setProgress(1.0);
        gracePeriodBar.setVisible(true);
    }

    /**
     * Update the grace period boss bar.
     */
    public void updateGracePeriodBar(int remainingSeconds, int totalSeconds) {
        if (gracePeriodBar == null) return;

        double progress = (double) remainingSeconds / totalSeconds;
        gracePeriodBar.setProgress(Math.max(0, Math.min(1, progress)));
        gracePeriodBar.setTitle("Grace Period: " + formatTime(remainingSeconds));

        // Change color as time decreases
        if (remainingSeconds <= 30) {
            gracePeriodBar.setColor(BarColor.RED);
        } else if (remainingSeconds <= 60) {
            gracePeriodBar.setColor(BarColor.YELLOW);
        } else {
            gracePeriodBar.setColor(BarColor.GREEN);
        }
    }

    /**
     * Remove the grace period boss bar.
     */
    public void removeGracePeriodBar() {
        if (gracePeriodBar != null) {
            gracePeriodBar.removeAll();
            gracePeriodBar = null;
        }
    }

    // ==================== SWAP TIMER BAR ====================

    /**
     * Create the swap timer boss bar.
     */
    public void createSwapTimerBar(int intervalSeconds) {
        if (swapTimerBar != null) {
            swapTimerBar.removeAll();
        }

        swapTimerBar = Bukkit.createBossBar(
            "Next Swap: " + formatTime(intervalSeconds),
            BarColor.PURPLE,
            BarStyle.SOLID
        );
        swapTimerBar.setProgress(1.0);
        swapTimerBar.setVisible(true);
    }

    /**
     * Update the swap timer boss bar.
     */
    public void updateSwapTimerBar(int remainingSeconds, int totalSeconds) {
        if (swapTimerBar == null) return;

        double progress = (double) remainingSeconds / totalSeconds;
        swapTimerBar.setProgress(Math.max(0, Math.min(1, progress)));
        swapTimerBar.setTitle("Next Swap: " + formatTime(remainingSeconds));

        // Change color when swap is imminent
        if (remainingSeconds <= 10) {
            swapTimerBar.setColor(BarColor.RED);
        } else if (remainingSeconds <= 30) {
            swapTimerBar.setColor(BarColor.YELLOW);
        } else {
            swapTimerBar.setColor(BarColor.PURPLE);
        }
    }

    /**
     * Remove the swap timer boss bar.
     */
    public void removeSwapTimerBar() {
        if (swapTimerBar != null) {
            swapTimerBar.removeAll();
            swapTimerBar = null;
        }
    }

    // ==================== DIFFICULTY VOTE BAR ====================

    /**
     * Create the difficulty vote boss bar.
     */
    public void createDifficultyVoteBar(int totalSeconds) {
        if (difficultyVoteBar != null) {
            difficultyVoteBar.removeAll();
        }

        difficultyVoteBar = Bukkit.createBossBar(
            "Vote for Difficulty! " + totalSeconds + "s",
            BarColor.BLUE,
            BarStyle.SEGMENTED_10
        );
        difficultyVoteBar.setProgress(1.0);
        difficultyVoteBar.setVisible(true);
    }

    /**
     * Update the difficulty vote boss bar.
     */
    public void updateDifficultyVoteBar(int remainingSeconds, int totalSeconds,
                                         int easyVotes, int mediumVotes, int hardVotes) {
        if (difficultyVoteBar == null) return;

        double progress = (double) remainingSeconds / totalSeconds;
        difficultyVoteBar.setProgress(Math.max(0, Math.min(1, progress)));
        difficultyVoteBar.setTitle(String.format(
            "Vote: Easy(%d) Medium(%d) Hard(%d) - %ds",
            easyVotes, mediumVotes, hardVotes, remainingSeconds
        ));
    }

    /**
     * Remove the difficulty vote boss bar.
     */
    public void removeDifficultyVoteBar() {
        if (difficultyVoteBar != null) {
            difficultyVoteBar.removeAll();
            difficultyVoteBar = null;
        }
    }

    // ==================== PLAYER MANAGEMENT ====================

    /**
     * Add a player to all active boss bars.
     */
    public void addPlayer(Player player) {
        if (gracePeriodBar != null) {
            gracePeriodBar.addPlayer(player);
        }
        if (swapTimerBar != null) {
            swapTimerBar.addPlayer(player);
        }
        if (difficultyVoteBar != null) {
            difficultyVoteBar.addPlayer(player);
        }
    }

    /**
     * Remove a player from all boss bars.
     */
    public void removePlayer(Player player) {
        if (gracePeriodBar != null) {
            gracePeriodBar.removePlayer(player);
        }
        if (swapTimerBar != null) {
            swapTimerBar.removePlayer(player);
        }
        if (difficultyVoteBar != null) {
            difficultyVoteBar.removePlayer(player);
        }
    }

    /**
     * Add a player to the grace period bar only.
     */
    public void addPlayerToGracePeriodBar(Player player) {
        if (gracePeriodBar != null) {
            gracePeriodBar.addPlayer(player);
        }
    }

    /**
     * Add a player to the swap timer bar only.
     */
    public void addPlayerToSwapTimerBar(Player player) {
        if (swapTimerBar != null) {
            swapTimerBar.addPlayer(player);
        }
    }

    /**
     * Add a player to the difficulty vote bar only.
     */
    public void addPlayerToDifficultyVoteBar(Player player) {
        if (difficultyVoteBar != null) {
            difficultyVoteBar.addPlayer(player);
        }
    }

    /**
     * Clean up all boss bars.
     */
    public void cleanup() {
        removeGracePeriodBar();
        removeSwapTimerBar();
        removeDifficultyVoteBar();
    }

    // ==================== UTILITY ====================

    /**
     * Format seconds as "M:SS" or "Xs".
     */
    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int secs = seconds % 60;
            return String.format("%d:%02d", minutes, secs);
        }
        return seconds + "s";
    }
}
