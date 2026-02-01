package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.pool.TaskPoolLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying crafting recipes for bingo items.
 */
public class RecipeGUI {

    public static final String GUI_TITLE_PREFIX = "Recipe: ";

    private final SwapBingoPlugin plugin;

    public RecipeGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the recipe GUI for a specific item.
     * @param player The player to show the GUI to
     * @param itemName The item name (lowercase, e.g., "diamond")
     * @param displayName The display name of the item
     * @param recipes The list of recipes for this item
     * @param recipeIndex Which recipe to show (if multiple)
     */
    public void open(Player player, String itemName, String displayName,
                     List<TaskPoolLoader.Recipe> recipes, int recipeIndex) {
        if (recipes.isEmpty()) {
            return;
        }

        TaskPoolLoader.Recipe recipe = recipes.get(recipeIndex);

        // Create 6-row inventory (54 slots)
        String title = GUI_TITLE_PREFIX + displayName;
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(title).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // Fill background
        ItemStack background = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, background);
        }

        // === Title area (row 0) ===
        inv.setItem(4, createTitleItem(displayName, recipe.resultCount()));

        // === Crafting grid (3x3, centered in rows 1-3) ===
        // Grid slots: row 1 = 11,12,13; row 2 = 20,21,22; row 3 = 29,30,31
        int[] gridSlots = {11, 12, 13, 20, 21, 22, 29, 30, 31};
        String[][] ingredients = recipe.ingredients();

        // Place crafting grid border
        int[] borderSlots = {10, 14, 19, 23, 28, 32};
        ItemStack border = createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot : borderSlots) {
            inv.setItem(slot, border);
        }

        // Fill crafting grid
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slotIndex = r * 3 + c;
                int slot = gridSlots[slotIndex];

                String ingredient = null;
                if (r < ingredients.length && c < ingredients[r].length) {
                    ingredient = ingredients[r][c];
                }

                if (ingredient != null && !ingredient.isEmpty()) {
                    inv.setItem(slot, createIngredientItem(ingredient));
                } else {
                    // Empty slot - use lighter glass
                    inv.setItem(slot, createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
                }
            }
        }

        // === Arrow pointing to result (slot 24) ===
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        arrowMeta.displayName(Component.text("==>").color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(arrowMeta);
        inv.setItem(24, arrow);

        // === Result item (slot 25) ===
        inv.setItem(25, createResultItem(itemName, displayName, recipe.resultCount()));

        // === Navigation (row 5) ===
        // Back button (slot 45)
        inv.setItem(45, createBackButton());

        // Recipe navigation if multiple recipes
        if (recipes.size() > 1) {
            // Recipe counter (slot 49)
            inv.setItem(49, createRecipeCounter(recipeIndex + 1, recipes.size()));

            // Previous recipe (slot 48)
            if (recipeIndex > 0) {
                inv.setItem(48, createPrevButton());
            }

            // Next recipe (slot 50)
            if (recipeIndex < recipes.size() - 1) {
                inv.setItem(50, createNextButton());
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createTitleItem(String displayName, int count) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Crafting Recipe")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Item: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(displayName).color(NamedTextColor.YELLOW)));
        if (count > 1) {
            lore.add(Component.text("Yields: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(count + "x").color(NamedTextColor.GREEN)));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createIngredientItem(String ingredientName) {
        Material material;
        try {
            material = Material.valueOf(ingredientName.toUpperCase());
            if (!material.isItem()) {
                material = Material.BARRIER;
            }
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = formatItemName(ingredientName);
        meta.displayName(Component.text(displayName)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createResultItem(String itemName, String displayName, int count) {
        Material material;
        try {
            material = Material.valueOf(itemName.toUpperCase());
            if (!material.isItem()) {
                material = Material.BARRIER;
            }
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material, Math.min(count, 64));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(displayName)
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Result: " + count + "x")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("<< Back to Bingo Card")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrevButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("<< Previous Recipe")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Next Recipe >>")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRecipeCounter(int current, int total) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Recipe " + current + "/" + total)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Format an item name for display (e.g., "oak_planks" -> "Oak Planks").
     */
    private String formatItemName(String name) {
        if (name == null) return "Unknown";

        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) result.append(" ");
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
}
