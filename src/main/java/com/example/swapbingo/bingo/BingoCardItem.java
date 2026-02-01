package com.example.swapbingo.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Manages the bingo card item that players use to open their card GUI.
 */
public class BingoCardItem {

    public static final NamespacedKey BINGO_CARD_KEY = new NamespacedKey("swapbingo", "bingo_card");
    public static final int INVENTORY_SLOT = 8; // Last hotbar slot

    /**
     * Create the bingo card item.
     */
    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        // Set display name
        meta.displayName(Component.text("Bingo Card")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        // Set lore
        meta.lore(List.of(
            Component.empty(),
            Component.text("Right-click to view your")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("bingo card and progress!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Complete a row or column")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("to win the game!")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));

        // Add persistent data to identify this item
        meta.getPersistentDataContainer().set(BINGO_CARD_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an item is the bingo card item.
     */
    public static boolean isBingoCardItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(BINGO_CARD_KEY, PersistentDataType.BYTE);
    }

    /**
     * Give the bingo card item to a player.
     */
    public static void give(Player player) {
        // Remove any existing bingo card items first
        remove(player);

        // Add to slot 8 (last hotbar slot)
        player.getInventory().setItem(INVENTORY_SLOT, create());
    }

    /**
     * Remove the bingo card item from a player.
     */
    public static void remove(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isBingoCardItem(item)) {
                inventory.setItem(i, null);
            }
        }
    }
}
