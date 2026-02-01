package com.example.swapbingo.bingo;

import java.util.Random;

/**
 * Represents the win pattern for bingo.
 */
public enum WinPattern {
    /** Complete any row to win */
    ROW,
    /** Complete any column to win */
    COLUMN;

    private static final Random RANDOM = new Random();

    /**
     * Get a random win pattern.
     */
    public static WinPattern random() {
        return RANDOM.nextBoolean() ? ROW : COLUMN;
    }

    /**
     * Parse a win pattern from config string.
     */
    public static WinPattern fromString(String str) {
        if (str == null) return random();

        return switch (str.toUpperCase()) {
            case "ROW", "ROW_ONLY" -> ROW;
            case "COLUMN", "COLUMN_ONLY" -> COLUMN;
            default -> random();
        };
    }
}
