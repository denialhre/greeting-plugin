package com.example.swapbingo.core;

import com.example.swapbingo.SwapBingoPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the points economy system and swap history for trap kill attribution.
 */
public class PointsManager {

    private final SwapBingoPlugin plugin;

    // Track who each player swapped with (whose location they went to)
    // Key: player who was teleported, Value: player whose location they went to + timestamp
    private final Map<UUID, SwapPartnerEntry> swapPartners = new HashMap<>();

    /**
     * Records who a player swapped with (whose location they were sent to).
     * If the player dies, the swap partner is credited with the trap kill.
     */
    public record SwapPartnerEntry(UUID trapSetterId, long timestamp) {}

    public PointsManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Get points for a player.
     */
    public int getPoints(UUID playerId) {
        PlayerSession session = plugin.getGameManager().getSessionIfExists(playerId);
        return session != null ? session.getPoints() : 0;
    }

    /**
     * Add points to a player.
     */
    public void addPoints(UUID playerId, int amount) {
        PlayerSession session = plugin.getGameManager().getSessionIfExists(playerId);
        if (session != null) {
            session.setPoints(session.getPoints() + amount);
        }
    }

    /**
     * Remove points from a player.
     * @return true if player had enough points and they were removed
     */
    public boolean removePoints(UUID playerId, int amount) {
        PlayerSession session = plugin.getGameManager().getSessionIfExists(playerId);
        if (session != null && session.getPoints() >= amount) {
            session.setPoints(session.getPoints() - amount);
            return true;
        }
        return false;
    }

    /**
     * Check if a player has enough points.
     */
    public boolean hasEnoughPoints(UUID playerId, int amount) {
        return getPoints(playerId) >= amount;
    }

    /**
     * Record a swap partner for a player.
     * Called when a player is teleported to another player's location.
     * @param playerId The player who was teleported
     * @param trapSetterId The player whose location they were sent to (the trap setter)
     */
    public void recordSwapPartner(UUID playerId, UUID trapSetterId) {
        swapPartners.put(playerId, new SwapPartnerEntry(trapSetterId, System.currentTimeMillis()));
    }

    /**
     * Get who set up the trap for a victim (whose location they were swapped to).
     * If the victim swapped recently (within the configured time window),
     * the player whose location they went to is credited with the trap kill.
     *
     * @param victimId The UUID of the player who died
     * @return The UUID of the player who set the trap, or null if swap was too long ago
     */
    public UUID getTrapSetter(UUID victimId) {
        SwapPartnerEntry entry = swapPartners.get(victimId);
        if (entry == null) {
            return null;
        }

        int windowSeconds = plugin.getConfigManager().getTrapKillWindowSeconds();
        long windowMillis = windowSeconds * 1000L;
        long now = System.currentTimeMillis();

        // Check if the swap was recent enough
        if (now - entry.timestamp() > windowMillis) {
            return null;
        }

        return entry.trapSetterId();
    }

    /**
     * Clear swap history (called when game ends).
     */
    public void clearSwapHistory() {
        swapPartners.clear();
    }

    /**
     * Clear all points data (called when game ends).
     */
    public void clearAll() {
        swapPartners.clear();
    }

    /**
     * Award points for task completion.
     */
    public void awardTaskCompletion(UUID playerId) {
        int points = plugin.getConfigManager().getPointsTaskCompletion();
        addPoints(playerId, points);
    }

    /**
     * Award points for a trap kill.
     */
    public void awardTrapKill(UUID playerId) {
        int points = plugin.getConfigManager().getPointsTrapKill();
        addPoints(playerId, points);
    }

    /**
     * Get the cost for location tracking.
     */
    public int getLocationTrackCost() {
        return plugin.getConfigManager().getPointsLocationTrack();
    }

    /**
     * Get the cost for task re-roll.
     */
    public int getTaskRerollCost() {
        return plugin.getConfigManager().getPointsTaskReroll();
    }

    /**
     * Get the cost for surprise swap.
     */
    public int getSurpriseSwapCost() {
        return plugin.getConfigManager().getPointsSurpriseSwap();
    }
}
