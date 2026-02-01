package com.example.swapbingo.bingo;

import com.example.swapbingo.bingo.tasks.TaskDefinition;
import com.example.swapbingo.bingo.tasks.TaskType;

/**
 * Represents a single cell on the bingo card.
 */
public class BingoTask {

    private final TaskDefinition definition;
    private final int row;
    private final int col;
    private boolean completed;

    public BingoTask(TaskDefinition definition, int row, int col) {
        this.definition = definition;
        this.row = row;
        this.col = col;
        this.completed = false;
    }

    public TaskDefinition getDefinition() {
        return definition;
    }

    public TaskType getType() {
        return definition.getType();
    }

    public String getTarget() {
        return definition.getTarget();
    }

    public String getDisplayName() {
        return definition.getDisplayName();
    }

    public String getDescription() {
        return definition.getDescription();
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * Check if this task matches the given target for the given type.
     */
    public boolean matches(TaskType type, String target) {
        if (this.definition.getType() != type) return false;
        return this.definition.getTarget().equalsIgnoreCase(target);
    }
}
