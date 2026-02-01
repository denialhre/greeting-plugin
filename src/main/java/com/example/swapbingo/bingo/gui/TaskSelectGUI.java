package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.BingoTask;
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
 * GUI for selecting a task to re-roll.
 */
public class TaskSelectGUI {

    public static final String GUI_TITLE = "Select Task to Re-roll";

    private final SwapBingoPlugin plugin;
    private final BingoCardGUI bingoCardGUI;

    public TaskSelectGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.bingoCardGUI = new BingoCardGUI(plugin);
    }

    public void open(Player player, BingoCard card) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(GUI_TITLE)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        // Fill background
        ItemStack background = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, background);
        }

        // Instructions (slot 4)
        inv.setItem(4, createInstructionItem());

        // Bingo grid (same layout as BingoCardGUI)
        int[] rowStarts = {11, 20, 29, 38, 47};

        for (int row = 0; row < card.getSize(); row++) {
            for (int col = 0; col < card.getSize(); col++) {
                BingoTask task = card.getTask(row, col);
                if (task != null) {
                    int slot = rowStarts[row] + col;
                    ItemStack item = createSelectableTaskItem(task);
                    inv.setItem(slot, item);
                }
            }
        }

        // Back button (slot 45)
        inv.setItem(45, createBackButton());

        // Cost reminder (slot 0)
        int cost = plugin.getConfigManager().getPointsTaskReroll();
        inv.setItem(0, createCostReminder(cost));

        player.openInventory(inv);
    }

    private ItemStack createInstructionItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Select Task to Re-roll")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Click on any task to")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("replace it with a new one.")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("The change affects ALL players!")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectableTaskItem(BingoTask task) {
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
            name = Component.text("COMPLETED")
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

        // Position
        lore.add(Component.text("Row " + (task.getRow() + 1) + ", Column " + (task.getCol() + 1))
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (task.isCompleted()) {
            lore.add(Component.text("Cannot re-roll completed tasks")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to re-roll this task")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Back to Shop")
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

    private ItemStack createCostReminder(int cost) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Cost: " + cost + " Points")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Points will be deducted")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("after you select a task.")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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
            case KILL_ENTITY -> Material.IRON_SWORD;
            case VISIT_BIOME -> Material.FILLED_MAP;
            case DISCOVER_STRUCTURE -> Material.MAP;
        };
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
