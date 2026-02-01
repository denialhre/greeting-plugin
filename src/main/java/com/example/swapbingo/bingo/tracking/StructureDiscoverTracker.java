package com.example.swapbingo.bingo.tracking;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.BingoTask;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.example.swapbingo.util.LocationUtil;
import com.example.swapbingo.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks structure discovery for bingo tasks.
 */
public class StructureDiscoverTracker implements Listener {

    private final SwapBingoPlugin plugin;
    private final BingoGame game;
    private final Map<UUID, Location> lastCheckLocation;
    private final Map<UUID, Long> lastCheckTime;
    private final int discoveryRadius;
    private final int checkIntervalBlocks;

    public StructureDiscoverTracker(SwapBingoPlugin plugin, BingoGame game) {
        this.plugin = plugin;
        this.game = game;
        this.lastCheckLocation = new HashMap<>();
        this.lastCheckTime = new HashMap<>();
        this.discoveryRadius = plugin.getConfigManager().getStructureDiscoveryRadius();
        this.checkIntervalBlocks = plugin.getConfigManager().getStructureCheckIntervalBlocks();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!game.isRunning() || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Throttle checks based on distance moved
        Location lastLoc = lastCheckLocation.get(player.getUniqueId());
        if (lastLoc != null) {
            double distance = LocationUtil.distance2D(lastLoc, to);
            if (distance < checkIntervalBlocks) {
                return;
            }
        }
        lastCheckLocation.put(player.getUniqueId(), to.clone());

        // Throttle by time (at least 2 seconds between checks)
        Long lastTime = lastCheckTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (lastTime != null && now - lastTime < 2000) {
            return;
        }
        lastCheckTime.put(player.getUniqueId(), now);

        boolean debugMode = plugin.getConfigManager().showStructureDebug();

        // Debug mode: scan ALL structures to tell player what's nearby
        if (debugMode) {
            scanAllStructures(player, to);
        }

        // Get player's incomplete structure tasks
        BingoCard card = game.getCard(player);
        if (card == null) {
            return;
        }

        List<BingoTask> structureTasks = card.getIncompleteTasks(TaskType.DISCOVER_STRUCTURE);
        if (structureTasks.isEmpty()) {
            return;
        }

        // Check structures for bingo tasks (synchronously - required by Paper)
        checkBingoStructures(player, to, structureTasks);
    }

    /**
     * Scan all structures and tell the player what's nearby (debug mode).
     */
    private void scanAllStructures(Player player, Location loc) {
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);

        StringBuilder found = new StringBuilder();
        int foundCount = 0;

        for (Structure structure : registry) {
            try {
                StructureSearchResult result = loc.getWorld().locateNearestStructure(
                    loc, structure, discoveryRadius, false);

                if (result != null) {
                    Location structureLoc = result.getLocation();
                    double distance = LocationUtil.distance2D(loc, structureLoc);

                    if (distance <= discoveryRadius) {
                        NamespacedKey key = registry.getKey(structure);
                        String name = key != null ? key.getKey() : "unknown";

                        if (foundCount > 0) found.append(", ");
                        found.append("&a").append(name).append(" &7(").append((int)distance).append("m)");
                        foundCount++;
                    }
                }
            } catch (Exception e) {
                // Ignore errors for individual structures
            }
        }

        if (foundCount > 0) {
            player.sendMessage(MessageUtil.fromLegacy(
                "&8[DEBUG] &eStructures nearby: " + found.toString()));
        } else {
            player.sendMessage(MessageUtil.fromLegacy(
                "&8[DEBUG] &7No structures within " + discoveryRadius + " blocks"));
        }
    }

    /**
     * Check structures for bingo task completion.
     * Uses internalIds from TaskPoolLoader - e.g., "village" checks all village variants.
     */
    private void checkBingoStructures(Player player, Location loc, List<BingoTask> structureTasks) {
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        var poolLoader = game.getPoolLoader();

        for (BingoTask task : structureTasks) {
            String structureName = task.getTarget().toLowerCase();

            // Get all internal IDs for this structure (e.g., "village" -> ["village_plains", "village_desert", ...])
            List<String> internalIds = poolLoader.getStructureInternalIds(structureName);

            // Check each internal ID - complete task if ANY match
            for (String internalId : internalIds) {
                try {
                    NamespacedKey key = NamespacedKey.minecraft(internalId);
                    Structure structure = registry.get(key);

                    if (structure == null) {
                        continue;
                    }

                    StructureSearchResult result = loc.getWorld().locateNearestStructure(
                        loc, structure, discoveryRadius, false);

                    if (result != null) {
                        Location structureLoc = result.getLocation();
                        double distance = LocationUtil.distance2D(loc, structureLoc);

                        if (distance <= discoveryRadius) {
                            game.handleTaskCompletion(player, TaskType.DISCOVER_STRUCTURE, task.getTarget());
                            break; // Found one, no need to check other variants
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("Structure search failed for " + internalId + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Clear all cached data to prevent memory leaks.
     * Called when the tracker is unregistered.
     */
    public void cleanup() {
        lastCheckLocation.clear();
        lastCheckTime.clear();
    }
}
