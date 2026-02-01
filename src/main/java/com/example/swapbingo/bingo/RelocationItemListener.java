package com.example.swapbingo.bingo;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listens for right-clicks on the relocation item during cage phase.
 */
public class RelocationItemListener implements Listener {

    private final SwapBingoPlugin plugin;

    public RelocationItemListener(SwapBingoPlugin plugin) {
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

        // Check if holding relocation item
        if (!RelocationItem.isRelocationItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Vote to relocate - the manager handles all the logic
        plugin.getRelocationVoteManager().voteToRelocate(player);
    }
}
