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
 * Points shop GUI for purchasing abilities.
 */
public class ShopGUI {

    public static final String GUI_TITLE = "Points Shop";

    // Slot positions
    public static final int SLOT_TITLE = 4;
    public static final int SLOT_BALANCE = 13;
    public static final int SLOT_REROLL = 29;
    public static final int SLOT_SURPRISE_SWAP = 33;
    public static final int SLOT_BACK = 45;

    private final SwapBingoPlugin plugin;

    public ShopGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(GUI_TITLE)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        // Fill background
        ItemStack background = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, background);
        }

        int playerPoints = plugin.getPointsManager().getPoints(player.getUniqueId());

        // Title item (slot 4)
        inv.setItem(SLOT_TITLE, createTitleItem());

        // Points balance (slot 13)
        inv.setItem(SLOT_BALANCE, createBalanceItem(playerPoints));

        // Re-roll Task (slot 29)
        int rerollCost = plugin.getConfigManager().getPointsTaskReroll();
        inv.setItem(SLOT_REROLL, createRerollItem(rerollCost, playerPoints));

        // Surprise Swap (slot 33)
        int swapCost = plugin.getConfigManager().getPointsSurpriseSwap();
        inv.setItem(SLOT_SURPRISE_SWAP, createSurpriseSwapItem(swapCost, playerPoints));

        // Back button (slot 45)
        inv.setItem(SLOT_BACK, createBackButton());

        player.openInventory(inv);
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Points Shop")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Spend your points on")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("powerful abilities!")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBalanceItem(int points) {
        // Use stack size to visually represent points (min 1, max 64)
        int stackSize = Math.max(1, Math.min(64, points));
        ItemStack item = new ItemStack(Material.EMERALD, stackSize);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Your Balance")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(points + " Points")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Earn points by:")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  + Completing tasks")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  + Trap kills")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRerollItem(int cost, int playerPoints) {
        boolean canAfford = playerPoints >= cost;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor titleColor = canAfford ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
        meta.displayName(Component.text("Re-roll Task")
            .color(titleColor)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Replace a task with a")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("new random task.")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Changes for ALL players!")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Cost: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(cost + " points")
                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)));

        if (!canAfford) {
            lore.add(Component.empty());
            lore.add(Component.text("Not enough points!")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click to select task")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSurpriseSwapItem(int cost, int playerPoints) {
        boolean canAfford = playerPoints >= cost;

        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor titleColor = canAfford ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY;
        meta.displayName(Component.text("Surprise Swap")
            .color(titleColor)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Trigger an immediate swap!")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("5-second warning given.")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Catch enemies off guard!")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Cost: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(cost + " points")
                .color(canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)));

        if (!canAfford) {
            lore.add(Component.empty());
            lore.add(Component.text("Not enough points!")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click to confirm")
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

        meta.displayName(Component.text("Back to Bingo Card")
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

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
}
