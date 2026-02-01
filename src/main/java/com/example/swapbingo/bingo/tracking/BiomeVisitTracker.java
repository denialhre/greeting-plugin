package com.example.swapbingo.bingo.tracking;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.example.swapbingo.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks biome visits for bingo tasks.
 * Optimized to only check on chunk boundary crossings.
 */
public class BiomeVisitTracker implements Listener {

    private final SwapBingoPlugin plugin;
    private final BingoGame game;
    private final Map<UUID, int[]> lastChunkCoords;

    public BiomeVisitTracker(SwapBingoPlugin plugin, BingoGame game) {
        this.plugin = plugin;
        this.game = game;
        this.lastChunkCoords = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!game.isRunning() || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // Only check when crossing chunk boundaries for performance
        if (!LocationUtil.isDifferentChunk(from, to)) {
            // Also skip if within same chunk as last check
            int[] lastCoords = lastChunkCoords.get(player.getUniqueId());
            int[] currentCoords = LocationUtil.getChunkCoords(to);
            if (lastCoords != null &&
                lastCoords[0] == currentCoords[0] &&
                lastCoords[1] == currentCoords[1]) {
                return;
            }
            lastChunkCoords.put(player.getUniqueId(), currentCoords);
        } else {
            lastChunkCoords.put(player.getUniqueId(), LocationUtil.getChunkCoords(to));
        }

        // Get current biome
        Biome biome = to.getWorld().getBiome(to);
        String biomeName = biome.name();

        // Debug mode - show biome info
        if (plugin.getConfigManager().showBiomeDebug()) {
            String debugMsg = "&8[DEBUG] &7Biome: &a" + biomeName +
                " &7at &f(" + to.getBlockX() + ", " + to.getBlockY() + ", " + to.getBlockZ() + ")";
            player.sendMessage(com.example.swapbingo.util.MessageUtil.fromLegacy(debugMsg));
        }

        game.handleTaskCompletion(player, TaskType.VISIT_BIOME, biomeName);
    }

    /**
     * Clear all cached data to prevent memory leaks.
     * Called when the tracker is unregistered.
     */
    public void cleanup() {
        lastChunkCoords.clear();
    }
}
