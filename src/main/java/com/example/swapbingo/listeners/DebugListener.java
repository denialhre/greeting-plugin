package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple debug listener - tells player what biome they're in.
 * Structure checking is done via command to avoid lag.
 */
public class DebugListener implements Listener {

    private final SwapBingoPlugin plugin;
    private final Map<UUID, String> lastBiome = new HashMap<>();

    public DebugListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().isDebugEnabled()) {
            return;
        }

        // Only check biomes (lightweight) - structure checking is too expensive
        if (!plugin.getConfigManager().showBiomeDebug()) {
            return;
        }

        Player player = event.getPlayer();
        var to = event.getTo();
        if (to == null) return;

        // Only check on chunk boundary changes
        var from = event.getFrom();
        if (from.getChunk().equals(to.getChunk())) {
            return;
        }

        String currentBiome = to.getWorld().getBiome(to).name();
        String previousBiome = lastBiome.get(player.getUniqueId());

        if (!currentBiome.equals(previousBiome)) {
            lastBiome.put(player.getUniqueId(), currentBiome);
            player.sendMessage(MessageUtil.fromLegacy(
                "&8[DEBUG] &7Biome: &a" + currentBiome));
        }
    }

    /**
     * Remove cached data for a player who left.
     * Called from PlayerJoinLeaveListener to prevent memory leaks.
     */
    public void removePlayer(UUID playerId) {
        lastBiome.remove(playerId);
    }
}
