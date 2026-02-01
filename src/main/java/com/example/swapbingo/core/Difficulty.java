package com.example.swapbingo.core;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Difficulty levels for bingo card generation.
 * Affects task weight selection - higher weights = easier tasks.
 */
public enum Difficulty {
    EASY("Easy", NamedTextColor.GREEN, Material.LIME_TERRACOTTA),
    MEDIUM("Medium", NamedTextColor.YELLOW, Material.YELLOW_TERRACOTTA),
    HARD("Hard", NamedTextColor.RED, Material.RED_TERRACOTTA);

    private final String displayName;
    private final NamedTextColor color;
    private final Material voteMaterial;

    Difficulty(String displayName, NamedTextColor color, Material voteMaterial) {
        this.displayName = displayName;
        this.color = color;
        this.voteMaterial = voteMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Material getVoteMaterial() {
        return voteMaterial;
    }

    /**
     * Adjust task weight based on difficulty.
     * - EASY: Higher weighted (easier) tasks are MORE likely
     * - MEDIUM: No adjustment
     * - HARD: Higher weighted (easier) tasks are LESS likely (inverts weights)
     *
     * @param originalWeight The original weight (typically 1-100)
     * @return The adjusted weight
     */
    public int adjustWeight(int originalWeight) {
        return switch (this) {
            case EASY -> originalWeight * 2; // Double high weights, making easy tasks more likely
            case MEDIUM -> originalWeight; // No change
            case HARD -> Math.max(1, 101 - originalWeight); // Invert: low weights become high
        };
    }

    /**
     * Get difficulty from string (case-insensitive).
     */
    public static Difficulty fromString(String name) {
        if (name == null) return MEDIUM;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }

    /**
     * Get difficulty from material.
     */
    public static Difficulty fromMaterial(Material material) {
        for (Difficulty d : values()) {
            if (d.voteMaterial == material) {
                return d;
            }
        }
        return null;
    }
}
