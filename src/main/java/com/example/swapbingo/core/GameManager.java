package com.example.swapbingo.core;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central coordinator for game state.
 * Manages player sessions and game state.
 */
public class GameManager {

    private final SwapBingoPlugin plugin;
    private final Map<UUID, PlayerSession> playerSessions;

    private GameState bingoState = GameState.IDLE;

    public GameManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.playerSessions = new HashMap<>();
    }

    /**
     * Check if any game is currently running.
     */
    public boolean isAnyGameRunning() {
        return bingoState == GameState.RUNNING;
    }

    /**
     * Check if Bingo is running.
     */
    public boolean isBingoRunning() {
        return bingoState == GameState.RUNNING;
    }

    public GameState getBingoState() {
        return bingoState;
    }

    public void setBingoState(GameState state) {
        this.bingoState = state;
    }

    /**
     * Get or create a player session.
     */
    public PlayerSession getSession(Player player) {
        return playerSessions.computeIfAbsent(player.getUniqueId(),
            uuid -> new PlayerSession(player.getUniqueId()));
    }

    /**
     * Get an existing session without creating a new one.
     */
    public PlayerSession getSessionIfExists(UUID uuid) {
        return playerSessions.get(uuid);
    }

    /**
     * Remove a player session.
     */
    public void removeSession(UUID uuid) {
        playerSessions.remove(uuid);
    }

    /**
     * Clear all sessions.
     */
    public void clearAllSessions() {
        playerSessions.clear();
    }

    /**
     * Get all active sessions.
     */
    public Map<UUID, PlayerSession> getAllSessions() {
        return new HashMap<>(playerSessions);
    }
}
