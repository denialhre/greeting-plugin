package com.example.swapbingo.core;

import com.example.swapbingo.bingo.BingoCard;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Stores per-player game data.
 */
public class PlayerSession {

    private final UUID playerId;

    // Original location for restoration after game
    private Location originalLocation;

    // Death Swap specific
    private boolean eliminated;

    // Bingo specific
    private BingoCard bingoCard;

    // Points system
    private int points;

    public PlayerSession(UUID playerId) {
        this.playerId = playerId;
        this.eliminated = false;
        this.points = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getOriginalLocation() {
        return originalLocation;
    }

    public void setOriginalLocation(Location location) {
        this.originalLocation = location != null ? location.clone() : null;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public BingoCard getBingoCard() {
        return bingoCard;
    }

    public void setBingoCard(BingoCard bingoCard) {
        this.bingoCard = bingoCard;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    /**
     * Reset the session for a new game.
     */
    public void reset() {
        this.originalLocation = null;
        this.eliminated = false;
        this.bingoCard = null;
        this.points = 0;
    }
}
