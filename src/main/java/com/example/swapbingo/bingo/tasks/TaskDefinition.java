package com.example.swapbingo.bingo.tasks;

import org.bukkit.Material;

/**
 * Represents a task definition loaded from the pool.
 */
public class TaskDefinition {

    private final TaskType type;
    private final String id;
    private final String displayName;
    private final String target; // Material name, EntityType name, Biome name, or Structure name
    private final int weight;

    public TaskDefinition(TaskType type, String id, String displayName, String target, int weight) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
        this.target = target;
        this.weight = weight;
    }

    public TaskType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTarget() {
        return target;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Get the Material for item tasks.
     */
    public Material getMaterial() {
        if (type != TaskType.COLLECT_ITEM) return null;
        try {
            return Material.valueOf(target.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the description text for this task.
     */
    public String getDescription() {
        return switch (type) {
            case COLLECT_ITEM -> "Collect: " + displayName;
            case KILL_ENTITY -> "Kill: " + displayName;
            case VISIT_BIOME -> "Visit: " + displayName;
            case DISCOVER_STRUCTURE -> "Discover: " + displayName;
        };
    }

    @Override
    public String toString() {
        return "TaskDefinition{" +
            "type=" + type +
            ", id='" + id + '\'' +
            ", displayName='" + displayName + '\'' +
            ", target='" + target + '\'' +
            '}';
    }
}
