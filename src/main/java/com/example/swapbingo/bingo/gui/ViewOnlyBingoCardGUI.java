package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.BingoTask;
import com.example.swapbingo.bingo.WinPattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for viewing another player's bingo card (view-only, no interactions).
 */
public class ViewOnlyBingoCardGUI {

    public static final String GUI_TITLE_PREFIX = "Viewing: ";

    // Special entity display materials
    private static final Map<String, Material> SPECIAL_ENTITY_ITEMS = Map.of(
        "ENDER_DRAGON", Material.DRAGON_HEAD,
        "WITHER", Material.WITHER_SKELETON_SKULL,
        "IRON_GOLEM", Material.IRON_BLOCK,
        "SNOW_GOLEM", Material.CARVED_PUMPKIN,
        "GIANT", Material.ZOMBIE_HEAD
    );

    private final SwapBingoPlugin plugin;

    public ViewOnlyBingoCardGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open a view-only bingo card GUI.
     * @param viewer The player viewing the card
     * @param targetPlayer The player whose card is being viewed
     * @param card The bingo card to display
     */
    public void open(Player viewer, Player targetPlayer, BingoCard card) {
        open(viewer, targetPlayer.getName(), card);
    }

    /**
     * Open a view-only bingo card GUI.
     * @param viewer The player viewing the card
     * @param targetName The name of the player whose card is being viewed
     * @param card The bingo card to display
     */
    public void open(Player viewer, String targetName, BingoCard card) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(GUI_TITLE_PREFIX + targetName)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD));

        // Fill background
        ItemStack background = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, background);
        }

        // Info item (slot 0)
        inv.setItem(0, createInfoItem(targetName, card));

        // Pattern info (slot 4)
        inv.setItem(4, createPatternInfoItem(card));

        // Bingo grid
        int[] rowStarts = {11, 20, 29, 38, 47};

        for (int row = 0; row < card.getSize(); row++) {
            for (int col = 0; col < card.getSize(); col++) {
                BingoTask task = card.getTask(row, col);
                if (task != null) {
                    int slot = rowStarts[row] + col;
                    ItemStack item = createTaskItem(task);
                    inv.setItem(slot, item);
                }
            }
        }

        // Back button (slot 45)
        inv.setItem(45, createBackButton());

        viewer.openInventory(inv);
    }

    private ItemStack createInfoItem(String targetName, BingoCard card) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Try to set the skull owner
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetName));

        meta.displayName(Component.text(targetName + "'s Card")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        int completed = card.getCompletedCount();
        int total = card.getTotalTasks();
        int tasksToWin = calculateTasksToWin(card);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Tasks Completed: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(completed + "/" + total)
                .color(NamedTextColor.GREEN)));
        lore.add(Component.text("Tasks to Win: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(String.valueOf(tasksToWin))
                .color(tasksToWin <= 2 ? NamedTextColor.GOLD : NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text("(View Only)")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPatternInfoItem(BingoCard card) {
        WinPattern pattern = card.getRequiredPattern();
        Material material = pattern == WinPattern.ROW ? Material.ARROW : Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String patternName = pattern == WinPattern.ROW ? "Complete a ROW" : "Complete a COLUMN";
        meta.displayName(Component.text("Win Condition")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(patternName)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTaskItem(BingoTask task) {
        Material material;

        if (task.isCompleted()) {
            material = Material.LIME_CONCRETE;
        } else {
            material = getTaskMaterial(task);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Display name
        Component name;
        if (task.isCompleted()) {
            name = Component.text("COMPLETED " + task.getDisplayName())
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        } else {
            name = Component.text(task.getDisplayName())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
        }
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();

        // Task type
        String typeStr = switch (task.getType()) {
            case COLLECT_ITEM -> "Collect Item";
            case KILL_ENTITY -> "Kill Entity";
            case VISIT_BIOME -> "Visit Biome";
            case DISCOVER_STRUCTURE -> "Discover Structure";
        };

        lore.add(Component.text(typeStr)
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // Status
        if (task.isCompleted()) {
            lore.add(Component.text("Status: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("COMPLETED")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)));
        } else {
            lore.add(Component.text("Status: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Incomplete")
                    .color(NamedTextColor.RED)));
        }

        // Position
        lore.add(Component.empty());
        lore.add(Component.text("Row " + (task.getRow() + 1) + ", Column " + (task.getCol() + 1))
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Back to Your Card")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
            Component.empty(),
            Component.text("Click to return")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private int calculateTasksToWin(BingoCard card) {
        int size = card.getSize();
        int minRemaining = size;

        if (card.getRequiredPattern() == WinPattern.ROW) {
            for (int row = 0; row < size; row++) {
                int incomplete = 0;
                for (int col = 0; col < size; col++) {
                    BingoTask task = card.getTask(row, col);
                    if (task != null && !task.isCompleted()) {
                        incomplete++;
                    }
                }
                minRemaining = Math.min(minRemaining, incomplete);
            }
        } else {
            for (int col = 0; col < size; col++) {
                int incomplete = 0;
                for (int row = 0; row < size; row++) {
                    BingoTask task = card.getTask(row, col);
                    if (task != null && !task.isCompleted()) {
                        incomplete++;
                    }
                }
                minRemaining = Math.min(minRemaining, incomplete);
            }
        }

        return minRemaining;
    }

    private Material getTaskMaterial(BingoTask task) {
        return switch (task.getType()) {
            case COLLECT_ITEM -> {
                try {
                    Material mat = Material.valueOf(task.getTarget());
                    yield mat.isItem() ? mat : Material.BARRIER;
                } catch (IllegalArgumentException e) {
                    yield Material.BARRIER;
                }
            }
            case KILL_ENTITY -> getSpawnEggForEntity(task.getTarget());
            case VISIT_BIOME -> Material.FILLED_MAP;
            case DISCOVER_STRUCTURE -> Material.MAP;
        };
    }

    private Material getSpawnEggForEntity(String entityName) {
        String upperName = entityName.toUpperCase();

        Material special = SPECIAL_ENTITY_ITEMS.get(upperName);
        if (special != null) {
            return special;
        }

        String spawnEggName = upperName + "_SPAWN_EGG";
        try {
            Material spawnEgg = Material.valueOf(spawnEggName);
            if (spawnEgg.isItem()) {
                return spawnEgg;
            }
        } catch (IllegalArgumentException ignored) {}

        if (upperName.equals("MUSHROOM_COW") || upperName.equals("MOOSHROOM")) {
            return Material.MOOSHROOM_SPAWN_EGG;
        }

        return Material.ZOMBIE_SPAWN_EGG;
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
