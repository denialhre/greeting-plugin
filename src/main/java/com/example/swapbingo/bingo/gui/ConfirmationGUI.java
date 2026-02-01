package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
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
 * Generic confirmation GUI with confirm/cancel buttons.
 */
public class ConfirmationGUI {

    public static final String GUI_TITLE_PREFIX = "Confirm: ";

    private final SwapBingoPlugin plugin;

    public ConfirmationGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open a confirmation GUI.
     * @param player The player to show the GUI to
     * @param action The action being confirmed (e.g., "Re-roll Task", "Surprise Swap")
     * @param description Description of what will happen
     * @param cost The cost in points
     */
    public void open(Player player, String action, String description, int cost) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text(GUI_TITLE_PREFIX + action)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        // Fill background
        ItemStack background = createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, background);
        }

        // Description item (slot 4)
        inv.setItem(4, createDescriptionItem(action, description));

        // Confirm button (slot 11 - green concrete)
        inv.setItem(11, createConfirmButton(cost));

        // Cancel button (slot 15 - red concrete)
        inv.setItem(15, createCancelButton());

        // Cost display (slot 22)
        inv.setItem(22, createCostItem(cost, player));

        player.openInventory(inv);
    }

    private ItemStack createDescriptionItem(String action, String description) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(action)
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Split description into lines
        String[] lines = description.split("\n");
        for (String line : lines) {
            lore.add(Component.text(line)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmButton(int cost) {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("CONFIRM")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Click to confirm purchase")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Cost: " + cost + " points")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("CANCEL")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Click to cancel")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCostItem(int cost, Player player) {
        int playerPoints = plugin.getPointsManager().getPoints(player.getUniqueId());
        boolean canAfford = playerPoints >= cost;

        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Cost")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Price: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(cost + " points")
                .color(NamedTextColor.GOLD)));
        lore.add(Component.text("Your balance: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(playerPoints + " points")
                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)));

        if (!canAfford) {
            lore.add(Component.empty());
            lore.add(Component.text("Not enough points!")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
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
}
