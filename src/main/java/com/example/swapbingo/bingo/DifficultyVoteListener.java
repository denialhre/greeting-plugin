package com.example.swapbingo.bingo;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.core.Difficulty;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for interactions with difficulty vote items.
 */
public class DifficultyVoteListener implements Listener {

    private final SwapBingoPlugin plugin;

    public DifficultyVoteListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Check if it's a right-click
        if (!event.getAction().isRightClick()) {
            return;
        }

        ItemStack item = event.getItem();
        if (!DifficultyVoteItem.isDifficultyVoteItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Check if voting is active
        if (!plugin.getDifficultyVoteManager().isVotingActive()) {
            return;
        }

        Player player = event.getPlayer();
        Difficulty difficulty = DifficultyVoteItem.getDifficulty(item);

        if (difficulty != null) {
            plugin.getDifficultyVoteManager().castVote(player, difficulty);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Prevent moving difficulty vote items during voting
        if (!plugin.getDifficultyVoteManager().isVotingActive()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (DifficultyVoteItem.isDifficultyVoteItem(clicked) ||
            DifficultyVoteItem.isDifficultyVoteItem(cursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Prevent dropping difficulty vote items during voting
        if (!plugin.getDifficultyVoteManager().isVotingActive()) {
            return;
        }

        if (DifficultyVoteItem.isDifficultyVoteItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
