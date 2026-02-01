package com.example.swapbingo.config;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Manages plugin configuration access and live editing.
 */
public class ConfigManager {

    private final SwapBingoPlugin plugin;

    public ConfigManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public void save() {
        plugin.saveConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    // ==================== Debug settings ====================

    public boolean isDebugEnabled() {
        return config().getBoolean("debug.enabled", false);
    }

    public void setDebugEnabled(boolean enabled) {
        config().set("debug.enabled", enabled);
        save();
    }

    public boolean showBiomeDebug() {
        return isDebugEnabled() && config().getBoolean("debug.show-biome-info", true);
    }

    public boolean showStructureDebug() {
        return isDebugEnabled() && config().getBoolean("debug.show-structure-info", true);
    }

    // ==================== General settings ====================

    public String getPrefix() {
        return config().getString("general.prefix", "&6[SwapBingo]&r ");
    }

    public String getLobbyWorld() {
        return config().getString("general.lobby-world", "world");
    }

    // ==================== Swap settings ====================

    public boolean isSwapEnabled() {
        return config().getBoolean("swap.enabled", true);
    }

    public void setSwapEnabled(boolean enabled) {
        config().set("swap.enabled", enabled);
        save();
    }

    public int getSwapIntervalSeconds() {
        return config().getInt("swap.interval-seconds", 180);
    }

    public void setSwapIntervalSeconds(int seconds) {
        config().set("swap.interval-seconds", seconds);
        save();
    }

    public List<Integer> getCountdownWarnings() {
        return config().getIntegerList("swap.countdown-warnings");
    }

    public String getSwapMessage(String key) {
        return config().getString("swap.messages." + key, "");
    }

    // ==================== Bingo settings ====================

    public boolean isBingoEnabled() {
        return config().getBoolean("bingo.enabled", true);
    }

    public int getBingoMinPlayers() {
        return config().getInt("bingo.min-players", 1);
    }

    public void setBingoMinPlayers(int minPlayers) {
        config().set("bingo.min-players", minPlayers);
        save();
    }

    public int getBingoMaxPlayers() {
        return config().getInt("bingo.max-players", 16);
    }

    public int getCardSize() {
        return config().getInt("bingo.card-size", 5);
    }

    public int getTaskDistribution(String type) {
        return config().getInt("bingo.tasks-distribution." + type, 5);
    }

    public void setTaskDistribution(String type, int count) {
        config().set("bingo.tasks-distribution." + type, count);
        save();
    }

    public String getWinPattern() {
        return config().getString("bingo.win-pattern", "RANDOM");
    }

    public void setWinPattern(String pattern) {
        config().set("bingo.win-pattern", pattern);
        save();
    }

    public boolean useSharedBingoCard() {
        return config().getBoolean("bingo.shared-card", true);
    }

    public void setSharedBingoCard(boolean shared) {
        config().set("bingo.shared-card", shared);
        save();
    }

    public String getBingoWorldName() {
        return config().getString("bingo.world.name", "bingo_world");
    }

    public boolean shouldResetWorldOnWin() {
        return config().getBoolean("bingo.world.reset-on-win", true);
    }

    public void setResetWorldOnWin(boolean reset) {
        config().set("bingo.world.reset-on-win", reset);
        save();
    }

    public boolean useRandomSeed() {
        return config().getBoolean("bingo.world.random-seed", true);
    }

    public long getFixedSeed() {
        return config().getLong("bingo.world.fixed-seed", 0);
    }

    // ==================== Spawn cage settings ====================

    public int getCageDistance() {
        return config().getInt("bingo.spawn.cage-distance", 50);
    }

    public int getStartCountdown() {
        return config().getInt("bingo.spawn.start-countdown", 30);
    }

    public int getFallGracePeriod() {
        return config().getInt("bingo.spawn.fall-grace-period", 5);
    }

    // ==================== Game grace period settings ====================

    public boolean isGameGracePeriodEnabled() {
        return config().getBoolean("bingo.grace-period.enabled", true);
    }

    public void setGameGracePeriodEnabled(boolean enabled) {
        config().set("bingo.grace-period.enabled", enabled);
        save();
    }

    public int getGameGracePeriodMinutes() {
        return config().getInt("bingo.grace-period.duration-minutes", 10);
    }

    public void setGameGracePeriodMinutes(int minutes) {
        config().set("bingo.grace-period.duration-minutes", minutes);
        save();
    }

    // ==================== Location purchase settings ====================

    public boolean isLocationPurchaseEnabled() {
        return config().getBoolean("bingo.location-purchase.enabled", true);
    }

    public int getLocationPurchaseCost() {
        return config().getInt("bingo.location-purchase.diamond-cost", 1);
    }

    // ==================== Win settings ====================

    public int getNextRoundDelay() {
        return config().getInt("bingo.win.next-round-delay", 20);
    }

    public boolean isFireworksEnabled() {
        return config().getBoolean("bingo.win.fireworks-enabled", true);
    }

    public int getBiomeCheckIntervalChunks() {
        return config().getInt("bingo.tracking.biome-check-interval-chunks", 1);
    }

    public int getStructureDiscoveryRadius() {
        return config().getInt("bingo.tracking.structure-discovery-radius", 50);
    }

    public int getStructureCheckIntervalBlocks() {
        return config().getInt("bingo.tracking.structure-check-interval-blocks", 20);
    }

    public boolean isScoreboardEnabled() {
        return config().getBoolean("bingo.scoreboard.enabled", true);
    }

    public String getScoreboardTitle() {
        return config().getString("bingo.scoreboard.title", "&l&6Bingo Progress");
    }

    public String getBingoMessage(String key) {
        return config().getString("bingo.messages." + key, "");
    }

    // ==================== Storage settings ====================

    public String getWinsFile() {
        return config().getString("storage.wins-file", "wins.yml");
    }

    // ==================== Points settings ====================

    public int getPointsTaskCompletion() {
        return config().getInt("points.task-completion", 1);
    }

    public int getPointsTrapKill() {
        return config().getInt("points.trap-kill", 5);
    }

    public int getTrapKillWindowSeconds() {
        return config().getInt("points.trap-kill-window", 30);
    }

    public int getPointsLocationTrack() {
        return config().getInt("points.shop.location-track", 3);
    }

    public int getPointsTaskReroll() {
        return config().getInt("points.shop.task-reroll", 3);
    }

    public int getPointsSurpriseSwap() {
        return config().getInt("points.shop.surprise-swap", 5);
    }

    public int getSurpriseSwapWarningSeconds() {
        return config().getInt("points.surprise-swap.warning-seconds", 5);
    }

    public int getSurpriseSwapMinTimeBeforeSwap() {
        return config().getInt("points.surprise-swap.min-time-before-swap", 30);
    }

    // ==================== Difficulty voting settings ====================

    public boolean isDifficultyVoteEnabled() {
        return config().getBoolean("bingo.difficulty.vote-enabled", true);
    }

    public int getDifficultyVoteDuration() {
        return config().getInt("bingo.difficulty.vote-duration-seconds", 10);
    }

    public String getDefaultDifficulty() {
        return config().getString("bingo.difficulty.default", "MEDIUM");
    }

    // ==================== Boss bar settings ====================

    public boolean isBossBarGracePeriodEnabled() {
        return config().getBoolean("bingo.boss-bar.grace-period", true);
    }

    public boolean isBossBarSwapTimerEnabled() {
        return config().getBoolean("bingo.boss-bar.swap-timer", true);
    }

    // ==================== Remake settings ====================

    public boolean isRemakeEnabled() {
        return config().getBoolean("remake.enabled", true);
    }

    public int getRemakeVoteDuration() {
        return config().getInt("remake.vote-duration", 30);
    }

    public int getRemakeCooldownMinutes() {
        return config().getInt("remake.cooldown-minutes", 5);
    }

    // ==================== Deprecated DeathSwap methods (for compatibility) ====================

    @Deprecated
    public boolean isDeathSwapEnabled() {
        return isSwapEnabled();
    }

    @Deprecated
    public int getDeathSwapMinPlayers() {
        return getBingoMinPlayers();
    }

    @Deprecated
    public int getDeathSwapMaxPlayers() {
        return getBingoMaxPlayers();
    }

    @Deprecated
    public boolean useDeathSwapSeparateWorld() {
        return false;
    }

    @Deprecated
    public String getDeathSwapWorldName() {
        return getBingoWorldName();
    }

    @Deprecated
    public String getDeathSwapMessage(String key) {
        return getSwapMessage(key);
    }
}
