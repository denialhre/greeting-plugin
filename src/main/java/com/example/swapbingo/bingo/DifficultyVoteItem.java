package com.example.swapbingo.bingo;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.core.Difficulty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles difficulty vote terracotta items.
 */
public class DifficultyVoteItem {

    private static NamespacedKey DIFFICULTY_KEY;

    public static void init(SwapBingoPlugin plugin) {
        DIFFICULTY_KEY = new NamespacedKey(plugin, "difficulty_vote");
    }

    /**
     * Create a difficulty vote item for the given difficulty.
     */
    public static ItemStack createItem(Difficulty difficulty, boolean selected) {
        ItemStack item = new ItemStack(difficulty.getVoteMaterial());
        ItemMeta meta = item.getItemMeta();

        // Set display name
        meta.displayName(Component.text(difficulty.getDisplayName() + " Mode")
            .color(difficulty.getColor())
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));

        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to vote for this difficulty")
            .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        switch (difficulty) {
            case EASY -> {
                lore.add(Component.text("Easier tasks are more common")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            }
            case MEDIUM -> {
                lore.add(Component.text("Balanced task distribution")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }
            case HARD -> {
                lore.add(Component.text("Harder tasks are more common")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (selected) {
            lore.add(Component.empty());
            lore.add(Component.text(">>> YOUR VOTE <<<")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        }

        meta.lore(lore);

        // Store difficulty in persistent data
        meta.getPersistentDataContainer().set(
            DIFFICULTY_KEY,
            PersistentDataType.STRING,
            difficulty.name()
        );

        // Add enchant glint if selected
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an item is a difficulty vote item.
     */
    public static boolean isDifficultyVoteItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(DIFFICULTY_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the difficulty from a vote item.
     */
    public static Difficulty getDifficulty(ItemStack item) {
        if (!isDifficultyVoteItem(item)) {
            return null;
        }
        String diffName = item.getItemMeta().getPersistentDataContainer()
            .get(DIFFICULTY_KEY, PersistentDataType.STRING);
        return Difficulty.fromString(diffName);
    }

    /**
     * Give all difficulty vote items to a player.
     * Places them in hotbar slots 2, 4, 6 (easy, medium, hard).
     */
    public static void giveAll(Player player, Difficulty currentVote) {
        player.getInventory().setItem(2, createItem(Difficulty.EASY, currentVote == Difficulty.EASY));
        player.getInventory().setItem(4, createItem(Difficulty.MEDIUM, currentVote == Difficulty.MEDIUM));
        player.getInventory().setItem(6, createItem(Difficulty.HARD, currentVote == Difficulty.HARD));
    }

    /**
     * Remove all difficulty vote items from a player.
     */
    public static void removeAll(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isDifficultyVoteItem(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Update the enchant glint on vote items to show selected difficulty.
     */
    public static void updateSelection(Player player, Difficulty selected) {
        giveAll(player, selected);
    }
}
