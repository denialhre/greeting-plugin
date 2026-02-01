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
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Temporary item given during cage phase for initiating relocation votes.
 * This item is removed when the game starts (cages drop).
 */
public class RelocationItem {

    private static NamespacedKey key;

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "relocation_item");
    }

    /**
     * Create the relocation vote item.
     */
    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Vote to Relocate")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
            Component.empty(),
            Component.text("Don't like this spawn location?")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Start a vote to move 1000 blocks!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Right-click to start vote")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));

        // Mark as relocation item
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an item is the relocation item.
     */
    public static boolean isRelocationItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * Give the relocation item to a player.
     */
    public static void give(Player player) {
        player.getInventory().setItem(7, create()); // Slot 7 (second to last hotbar slot)
    }

    /**
     * Remove the relocation item from a player.
     */
    public static void remove(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isRelocationItem(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
