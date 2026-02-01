package com.example.swapbingo.bingo.pool;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.tasks.TaskDefinition;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads task definitions from JSON files.
 */
public class TaskPoolLoader {

    private final SwapBingoPlugin plugin;
    private final TaskPool taskPool;

    // Structure internal IDs mapping (name -> list of internal IDs)
    private final Map<String, List<String>> structureInternalIds = new HashMap<>();

    // Item ID to name mapping for recipes
    private final Map<Integer, String> itemIdToName = new HashMap<>();

    // Recipes mapping (item name -> list of recipes)
    private final Map<String, List<Recipe>> recipes = new HashMap<>();

    /**
     * Represents a crafting recipe.
     */
    public record Recipe(String[][] ingredients, String resultName, int resultCount) {}

    public TaskPoolLoader(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.taskPool = new TaskPool();
    }

    /**
     * Load all task data from JSON files.
     */
    public TaskPool load() {
        taskPool.clear();
        structureInternalIds.clear();
        itemIdToName.clear();
        recipes.clear();

        // First, load ALL item IDs from full-itens.json (needed for recipes)
        loadAllItemIds();

        // Load task pool items from JSON files
        loadItems();
        loadEntities();
        loadBiomes();
        loadStructures();

        // Load recipes (uses itemIdToName mapping from full-itens.json)
        loadRecipes();

        plugin.getLogger().info("Loaded task pool: " +
            taskPool.getTaskCount(TaskType.COLLECT_ITEM) + " items, " +
            taskPool.getTaskCount(TaskType.KILL_ENTITY) + " entities, " +
            taskPool.getTaskCount(TaskType.VISIT_BIOME) + " biomes, " +
            taskPool.getTaskCount(TaskType.DISCOVER_STRUCTURE) + " structures");

        return taskPool;
    }

    /**
     * Load ALL item IDs from full-itens.json for recipe lookups.
     * This includes items that are not in the task pool but are used as ingredients.
     */
    private void loadAllItemIds() {
        JsonArray array = loadJsonArray("data/full-itens.json");
        if (array == null) {
            plugin.getLogger().warning("Could not load full-itens.json, recipes may not work correctly");
            return;
        }

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            int id = obj.get("id").getAsInt();
            String name = obj.get("name").getAsString();
            itemIdToName.put(id, name);
        }

        plugin.getLogger().info("Loaded " + itemIdToName.size() + " item IDs for recipe lookup");
    }

    /**
     * Load items from itens.json (task pool items only).
     * Item ID mappings are already loaded from full-itens.json.
     */
    private void loadItems() {
        JsonArray array = loadJsonArray("data/itens.json");
        if (array == null) return;

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();

            String name = obj.get("name").getAsString();
            String displayName = obj.get("displayName").getAsString();
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 50;

            // Target is uppercase for Material enum
            String target = name.toUpperCase();

            TaskDefinition task = new TaskDefinition(
                TaskType.COLLECT_ITEM, name, displayName, target, weight);
            taskPool.addTask(task);
        }
    }

    /**
     * Load entities from entities.json
     */
    private void loadEntities() {
        JsonArray array = loadJsonArray("data/entities.json");
        if (array == null) return;

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();

            String name = obj.get("name").getAsString();
            String displayName = obj.get("displayName").getAsString();
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 50;

            // Target is uppercase for EntityType enum
            String target = name.toUpperCase();

            TaskDefinition task = new TaskDefinition(
                TaskType.KILL_ENTITY, name, displayName, target, weight);
            taskPool.addTask(task);
        }
    }

    /**
     * Load biomes from biomes.json
     */
    private void loadBiomes() {
        JsonArray array = loadJsonArray("data/biomes.json");
        if (array == null) return;

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();

            String name = obj.get("name").getAsString();
            String displayName = obj.get("displayName").getAsString();
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 50;

            // Target is uppercase for Biome enum
            String target = name.toUpperCase();

            TaskDefinition task = new TaskDefinition(
                TaskType.VISIT_BIOME, name, displayName, target, weight);
            taskPool.addTask(task);
        }
    }

    /**
     * Load structures from structures.json
     */
    private void loadStructures() {
        JsonArray array = loadJsonArray("data/structures.json");
        if (array == null) return;

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();

            String name = obj.get("name").getAsString();
            String displayName = obj.get("displayName").getAsString();
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 50;

            // Load internal IDs (the actual structure registry names)
            List<String> internalIds = new ArrayList<>();
            if (obj.has("internalIds")) {
                JsonArray ids = obj.getAsJsonArray("internalIds");
                for (JsonElement id : ids) {
                    internalIds.add(id.getAsString().toLowerCase());
                }
            } else {
                // Fallback to name if no internal IDs
                internalIds.add(name.toLowerCase());
            }

            // Store the internal IDs mapping
            structureInternalIds.put(name.toLowerCase(), internalIds);

            // Target is the structure name (we'll look up internal IDs separately)
            TaskDefinition task = new TaskDefinition(
                TaskType.DISCOVER_STRUCTURE, name, displayName, name.toLowerCase(), weight);
            taskPool.addTask(task);
        }
    }

    /**
     * Load a JSON array from a resource file.
     */
    private JsonArray loadJsonArray(String resourcePath) {
        try {
            // First check if file exists in plugin data folder
            File dataFile = new File(plugin.getDataFolder(), resourcePath);

            InputStream inputStream;
            if (dataFile.exists()) {
                inputStream = new FileInputStream(dataFile);
            } else {
                // Try to load from resources
                inputStream = plugin.getResource(resourcePath);
                if (inputStream == null) {
                    plugin.getLogger().warning("Could not find " + resourcePath);
                    return null;
                }
            }

            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonArray()) {
                    return element.getAsJsonArray();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the internal IDs for a structure name.
     * For example, "village" returns ["village_plains", "village_desert", ...]
     */
    public List<String> getStructureInternalIds(String structureName) {
        return structureInternalIds.getOrDefault(structureName.toLowerCase(), List.of(structureName.toLowerCase()));
    }

    /**
     * Get item name by ID (for recipe lookup).
     */
    public String getItemNameById(int id) {
        return itemIdToName.get(id);
    }

    /**
     * Get item ID to name map.
     */
    public Map<Integer, String> getItemIdToNameMap() {
        return Collections.unmodifiableMap(itemIdToName);
    }

    public TaskPool getTaskPool() {
        return taskPool;
    }

    /**
     * Load recipes from recipes.json
     */
    private void loadRecipes() {
        JsonObject recipesObj = loadJsonObject("data/recipes.json");
        if (recipesObj == null) return;

        int count = 0;
        for (String resultIdStr : recipesObj.keySet()) {
            try {
                int resultId = Integer.parseInt(resultIdStr);
                String resultName = itemIdToName.get(resultId);
                if (resultName == null) {
                    continue;
                }

                JsonArray recipeArray = recipesObj.getAsJsonArray(resultIdStr);
                for (JsonElement recipeEl : recipeArray) {
                    JsonObject recipeObj = recipeEl.getAsJsonObject();

                    String[][] ingredients;

                    // Check for shaped recipe (inShape) or shapeless recipe (ingredients)
                    if (recipeObj.has("inShape")) {
                        // Shaped recipe - parse the ingredient shape (2D array)
                        JsonArray inShape = recipeObj.getAsJsonArray("inShape");
                        int rows = inShape.size();
                        int cols = 0;
                        for (JsonElement row : inShape) {
                            cols = Math.max(cols, row.getAsJsonArray().size());
                        }

                        ingredients = new String[rows][cols];
                        for (int r = 0; r < rows; r++) {
                            JsonArray row = inShape.get(r).getAsJsonArray();
                            for (int c = 0; c < row.size(); c++) {
                                JsonElement cell = row.get(c);
                                if (!cell.isJsonNull()) {
                                    int ingredientId = cell.getAsInt();
                                    String ingredientName = itemIdToName.get(ingredientId);
                                    ingredients[r][c] = ingredientName != null ? ingredientName : "unknown";
                                }
                            }
                        }
                    } else if (recipeObj.has("ingredients")) {
                        // Shapeless recipe - flat list of ingredients
                        JsonArray ingredientList = recipeObj.getAsJsonArray("ingredients");
                        int size = ingredientList.size();

                        // Convert to a single row for display
                        ingredients = new String[1][size];
                        for (int i = 0; i < size; i++) {
                            JsonElement ingredientEl = ingredientList.get(i);
                            if (!ingredientEl.isJsonNull()) {
                                int ingredientId = ingredientEl.getAsInt();
                                String ingredientName = itemIdToName.get(ingredientId);
                                ingredients[0][i] = ingredientName != null ? ingredientName : "unknown";
                            }
                        }
                    } else {
                        // Unknown format, skip
                        continue;
                    }

                    // Get result count
                    JsonObject resultObj = recipeObj.getAsJsonObject("result");
                    int resultCount = resultObj.has("count") ? resultObj.get("count").getAsInt() : 1;

                    Recipe recipe = new Recipe(ingredients, resultName, resultCount);

                    recipes.computeIfAbsent(resultName.toLowerCase(), k -> new ArrayList<>()).add(recipe);
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Failed to parse recipe for ID " + resultIdStr + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + count + " recipes");
    }

    /**
     * Load a JSON object from a resource file.
     */
    private JsonObject loadJsonObject(String resourcePath) {
        try {
            File dataFile = new File(plugin.getDataFolder(), resourcePath);

            InputStream inputStream;
            if (dataFile.exists()) {
                inputStream = new FileInputStream(dataFile);
            } else {
                inputStream = plugin.getResource(resourcePath);
                if (inputStream == null) {
                    plugin.getLogger().warning("Could not find " + resourcePath);
                    return null;
                }
            }

            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get recipes for an item by name.
     */
    public List<Recipe> getRecipes(String itemName) {
        return recipes.getOrDefault(itemName.toLowerCase(), List.of());
    }
}
