package com.example.swapbingo.core;

/**
 * Represents the current state of a game.
 */
public enum GameState {
    /** No game is running */
    IDLE,
    /** Game is in countdown phase before starting */
    COUNTDOWN,
    /** Game is actively running */
    RUNNING,
    /** Game has ended, cleanup in progress */
    ENDED
}
