package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCardItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join and leave events during games.
 */
public class PlayerJoinLeaveListener implements Listener {

    private final SwapBingoPlugin plugin;

    public PlayerJoinLeaveListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // If Bingo is running and player was a participant
        var bingo = plugin.getBingoGame();
        if (bingo.isRunning() && bingo.isParticipant(player.getUniqueId())) {
            // Re-give the bingo card item if they don't have it
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !hasBingoCardItem(player)) {
                    BingoCardItem.give(player);
                }
            }, 20L); // 1 second delay
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Bingo doesn't eliminate players on quit - they can rejoin
        // Their progress is saved in their session

        // Clean up cached debug data to prevent memory leaks
        plugin.getDebugListener().removePlayer(event.getPlayer().getUniqueId());

        // Clean up location tracker data for this player
        plugin.getBingoGame().getLocationTrackerManager().clearPlayerTrackers(event.getPlayer().getUniqueId());
    }

    private boolean hasBingoCardItem(Player player) {
        for (var item : player.getInventory().getContents()) {
            if (BingoCardItem.isBingoCardItem(item)) {
                return true;
            }
        }
        return false;
    }
}
