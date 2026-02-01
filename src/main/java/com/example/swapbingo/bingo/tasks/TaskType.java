package com.example.swapbingo.bingo.tasks;

/**
 * Types of tasks that can appear on a bingo card.
 */
public enum TaskType {
    /** Collect a specific item */
    COLLECT_ITEM("items"),
    /** Kill a specific entity */
    KILL_ENTITY("entities"),
    /** Visit a specific biome */
    VISIT_BIOME("biomes"),
    /** Discover a specific structure */
    DISCOVER_STRUCTURE("structures");

    private final String configKey;

    TaskType(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Get the config key for task distribution.
     */
    public String getConfigKey() {
        return configKey;
    }
}
