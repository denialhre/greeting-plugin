package com.example.swapbingo.bingo;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.gui.BingoCardGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions with the bingo card item.
 */
public class BingoItemListener implements Listener {

    private final SwapBingoPlugin plugin;
    private final BingoCardGUI cardGUI;

    public BingoItemListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.cardGUI = new BingoCardGUI(plugin);
    }

    /**
     * Handle right-click to open GUI.
     * Works during cage phase (before game is officially running) as long as player has a card.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!BingoCardItem.isBingoCardItem(item)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        BingoGame game = plugin.getBingoGame();

        // Allow opening card if player is a participant (works during cage phase too)
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }

        BingoCard card = game.getCard(player);
        if (card != null) {
            cardGUI.open(player, card, game);
        }
    }

    /**
     * Prevent dropping the bingo card item.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (BingoCardItem.isBingoCardItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent moving the bingo card item in inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (BingoCardItem.isBingoCardItem(current) || BingoCardItem.isBingoCardItem(cursor)) {
            // Allow clicking in the bingo GUI, but not moving the item
            if (event.getClickedInventory() == event.getWhoClicked().getInventory()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent swapping the bingo card to off-hand.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (BingoCardItem.isBingoCardItem(event.getMainHandItem()) ||
            BingoCardItem.isBingoCardItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Re-give the item on respawn if game is running.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BingoGame game = plugin.getBingoGame();

        if (game.isRunning() && game.isParticipant(player.getUniqueId())) {
            // Delay by 1 tick to ensure inventory is ready
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                BingoCardItem.give(player);
            }, 1L);
        }
    }

    /**
     * Handle death - item will be removed, we'll re-give on respawn.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Remove bingo card from drops
        event.getDrops().removeIf(BingoCardItem::isBingoCardItem);
    }
}
