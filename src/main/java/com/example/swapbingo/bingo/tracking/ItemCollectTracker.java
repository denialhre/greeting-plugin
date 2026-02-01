package com.example.swapbingo.bingo.tracking;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.tasks.TaskType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks item collection for bingo tasks.
 */
public class ItemCollectTracker implements Listener {

    private final SwapBingoPlugin plugin;
    private final BingoGame game;

    public ItemCollectTracker(SwapBingoPlugin plugin, BingoGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!game.isRunning() || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        String materialName = item.getType().name();

        game.handleTaskCompletion(player, TaskType.COLLECT_ITEM, materialName);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!game.isRunning() || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        String materialName = result.getType().name();

        game.handleTaskCompletion(player, TaskType.COLLECT_ITEM, materialName);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!game.isRunning() || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        // Check cursor item (item being picked up)
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            String materialName = cursor.getType().name();
            game.handleTaskCompletion(player, TaskType.COLLECT_ITEM, materialName);
        }

        // Check current item in slot
        ItemStack current = event.getCurrentItem();
        if (current != null && !current.getType().isAir()) {
            String materialName = current.getType().name();
            game.handleTaskCompletion(player, TaskType.COLLECT_ITEM, materialName);
        }
    }
}
