package com.example.swapbingo.bingo;

import com.example.swapbingo.bingo.tasks.TaskDefinition;
import com.example.swapbingo.bingo.tasks.TaskType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player's bingo card (5x5 grid).
 */
public class BingoCard {

    private final int size;
    private final BingoTask[][] grid;
    private final WinPattern requiredPattern;
    private int completedCount;

    public BingoCard(int size, WinPattern pattern) {
        this.size = size;
        this.grid = new BingoTask[size][size];
        this.requiredPattern = pattern;
        this.completedCount = 0;
    }

    /**
     * Set a task at a specific position.
     */
    public void setTask(int row, int col, TaskDefinition definition) {
        grid[row][col] = new BingoTask(definition, row, col);
    }

    /**
     * Get a task at a specific position.
     */
    public BingoTask getTask(int row, int col) {
        return grid[row][col];
    }

    /**
     * Mark a task as completed by position.
     */
    public boolean markComplete(int row, int col) {
        BingoTask task = grid[row][col];
        if (task != null && !task.isCompleted()) {
            task.setCompleted(true);
            completedCount++;
            return true;
        }
        return false;
    }

    /**
     * Try to mark a task complete by type and target.
     * Returns the task if it was newly completed, null otherwise.
     */
    public BingoTask tryComplete(TaskType type, String target) {
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                BingoTask task = grid[row][col];
                if (task != null && !task.isCompleted() && task.matches(type, target)) {
                    task.setCompleted(true);
                    completedCount++;
                    return task;
                }
            }
        }
        return null;
    }

    /**
     * Check if the card has a winning pattern.
     */
    public boolean checkWin() {
        return switch (requiredPattern) {
            case ROW -> checkRowWin();
            case COLUMN -> checkColumnWin();
        };
    }

    /**
     * Check if any row is complete.
     */
    private boolean checkRowWin() {
        for (int row = 0; row < size; row++) {
            boolean rowComplete = true;
            for (int col = 0; col < size; col++) {
                if (grid[row][col] == null || !grid[row][col].isCompleted()) {
                    rowComplete = false;
                    break;
                }
            }
            if (rowComplete) return true;
        }
        return false;
    }

    /**
     * Check if any column is complete.
     */
    private boolean checkColumnWin() {
        for (int col = 0; col < size; col++) {
            boolean colComplete = true;
            for (int row = 0; row < size; row++) {
                if (grid[row][col] == null || !grid[row][col].isCompleted()) {
                    colComplete = false;
                    break;
                }
            }
            if (colComplete) return true;
        }
        return false;
    }

    /**
     * Get all tasks as a flat list.
     */
    public List<BingoTask> getAllTasks() {
        List<BingoTask> tasks = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (grid[row][col] != null) {
                    tasks.add(grid[row][col]);
                }
            }
        }
        return tasks;
    }

    /**
     * Get all completed tasks.
     */
    public List<BingoTask> getCompletedTasks() {
        List<BingoTask> completed = new ArrayList<>();
        for (BingoTask task : getAllTasks()) {
            if (task.isCompleted()) {
                completed.add(task);
            }
        }
        return completed;
    }

    /**
     * Get all incomplete tasks of a specific type.
     */
    public List<BingoTask> getIncompleteTasks(TaskType type) {
        List<BingoTask> incomplete = new ArrayList<>();
        for (BingoTask task : getAllTasks()) {
            if (!task.isCompleted() && task.getType() == type) {
                incomplete.add(task);
            }
        }
        return incomplete;
    }

    /**
     * Check if the card has an incomplete task matching the type and target.
     */
    public boolean hasIncompleteTask(TaskType type, String target) {
        for (BingoTask task : getAllTasks()) {
            if (!task.isCompleted() && task.matches(type, target)) {
                return true;
            }
        }
        return false;
    }

    public int getSize() {
        return size;
    }

    public WinPattern getRequiredPattern() {
        return requiredPattern;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getTotalTasks() {
        return size * size;
    }

    /**
     * Clone this card with the same tasks but reset progress.
     * Used for shared bingo cards where everyone has the same tasks.
     */
    @Override
    public BingoCard clone() {
        BingoCard cloned = new BingoCard(size, requiredPattern);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (grid[row][col] != null) {
                    // Create new task with same definition but fresh completion state
                    cloned.grid[row][col] = new BingoTask(grid[row][col].getDefinition(), row, col);
                }
            }
        }
        return cloned;
    }

    /**
     * Reset a task at a specific position with a new definition.
     * Used for task re-rolling.
     */
    public void resetTask(int row, int col, TaskDefinition newDefinition) {
        BingoTask oldTask = grid[row][col];
        if (oldTask != null && oldTask.isCompleted()) {
            completedCount--;
        }
        grid[row][col] = new BingoTask(newDefinition, row, col);
    }

    /**
     * Get a task's definition for comparison during re-roll.
     */
    public TaskDefinition getTaskDefinition(int row, int col) {
        BingoTask task = grid[row][col];
        return task != null ? task.getDefinition() : null;
    }
}
