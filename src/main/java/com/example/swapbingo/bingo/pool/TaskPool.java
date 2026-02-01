package com.example.swapbingo.bingo.pool;

import com.example.swapbingo.bingo.tasks.TaskDefinition;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.example.swapbingo.core.Difficulty;

import java.util.*;

/**
 * Contains all available task definitions for card generation.
 */
public class TaskPool {

    private final Map<TaskType, List<TaskDefinition>> tasksByType;
    private final Random random;

    public TaskPool() {
        this.tasksByType = new EnumMap<>(TaskType.class);
        this.random = new Random();

        // Initialize empty lists for each type
        for (TaskType type : TaskType.values()) {
            tasksByType.put(type, new ArrayList<>());
        }
    }

    /**
     * Add a task definition to the pool.
     */
    public void addTask(TaskDefinition task) {
        tasksByType.get(task.getType()).add(task);
    }

    /**
     * Get all tasks of a specific type.
     */
    public List<TaskDefinition> getTasks(TaskType type) {
        return Collections.unmodifiableList(tasksByType.get(type));
    }

    /**
     * Get a random selection of tasks using weighted random selection.
     */
    public List<TaskDefinition> getRandomTasks(TaskType type, int count) {
        return getRandomTasks(type, count, Difficulty.MEDIUM);
    }

    /**
     * Get a random selection of tasks using weighted random selection with difficulty adjustment.
     */
    public List<TaskDefinition> getRandomTasks(TaskType type, int count, Difficulty difficulty) {
        List<TaskDefinition> available = new ArrayList<>(tasksByType.get(type));

        if (available.isEmpty()) {
            return Collections.emptyList();
        }

        // If we want more than available, just return all shuffled
        if (count >= available.size()) {
            Collections.shuffle(available, random);
            return available;
        }

        List<TaskDefinition> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        while (selected.size() < count && !available.isEmpty()) {
            TaskDefinition task = selectWeightedRandom(available, difficulty);
            if (!selectedIds.contains(task.getId())) {
                selected.add(task);
                selectedIds.add(task.getId());
                available.remove(task);
            }
        }

        return selected;
    }

    /**
     * Select a task using weighted random selection.
     */
    private TaskDefinition selectWeightedRandom(List<TaskDefinition> tasks) {
        return selectWeightedRandom(tasks, Difficulty.MEDIUM);
    }

    /**
     * Select a task using weighted random selection with difficulty adjustment.
     */
    private TaskDefinition selectWeightedRandom(List<TaskDefinition> tasks, Difficulty difficulty) {
        // Calculate adjusted weights based on difficulty
        int totalWeight = 0;
        int[] adjustedWeights = new int[tasks.size()];

        for (int i = 0; i < tasks.size(); i++) {
            adjustedWeights[i] = difficulty.adjustWeight(tasks.get(i).getWeight());
            totalWeight += adjustedWeights[i];
        }

        if (totalWeight <= 0) {
            return tasks.get(random.nextInt(tasks.size()));
        }

        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (int i = 0; i < tasks.size(); i++) {
            currentWeight += adjustedWeights[i];
            if (randomValue < currentWeight) {
                return tasks.get(i);
            }
        }

        return tasks.get(tasks.size() - 1);
    }

    /**
     * Get the count of tasks for a specific type.
     */
    public int getTaskCount(TaskType type) {
        return tasksByType.get(type).size();
    }

    /**
     * Get total count of all tasks.
     */
    public int getTotalCount() {
        return tasksByType.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Check if the pool has enough tasks.
     */
    public boolean hasEnoughTasks(int itemCount, int entityCount, int biomeCount, int structureCount) {
        return getTaskCount(TaskType.COLLECT_ITEM) >= itemCount
            && getTaskCount(TaskType.KILL_ENTITY) >= entityCount
            && getTaskCount(TaskType.VISIT_BIOME) >= biomeCount
            && getTaskCount(TaskType.DISCOVER_STRUCTURE) >= structureCount;
    }

    /**
     * Clear all tasks.
     */
    public void clear() {
        for (List<TaskDefinition> tasks : tasksByType.values()) {
            tasks.clear();
        }
    }
}
