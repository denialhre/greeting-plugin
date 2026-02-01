package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.util.LocationUtil;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player respawn to ensure they stay in the bingo world during a game.
 */
public class PlayerRespawnListener implements Listener {

    private final SwapBingoPlugin plugin;

    public PlayerRespawnListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Check if player is in a bingo game
        if (!plugin.getBingoGame().isRunning() ||
            !plugin.getBingoGame().isParticipant(player.getUniqueId())) {
            return;
        }

        // Get the bingo world
        String bingoWorldName = plugin.getConfigManager().getBingoWorldName();
        World bingoWorld = plugin.getServer().getWorld(bingoWorldName);

        if (bingoWorld == null) {
            return;
        }

        // Set respawn location to bingo world spawn
        event.setRespawnLocation(LocationUtil.findSafeSpawn(bingoWorld));
    }
}
