package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles portal travel to ensure bingo world players go to bingo nether/end.
 */
public class PortalListener implements Listener {

    private final SwapBingoPlugin plugin;

    public PortalListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();

        if (fromWorld == null) return;

        String bingoWorldName = plugin.getConfigManager().getBingoWorldName();
        String fromWorldName = fromWorld.getName();

        // Check if player is in the bingo world system
        if (!isBingoWorld(fromWorldName, bingoWorldName)) {
            return; // Not a bingo world, let vanilla handle it
        }

        // Only handle if player is in bingo game
        if (!plugin.getBingoGame().isRunning() ||
            !plugin.getBingoGame().isParticipant(player.getUniqueId())) {
            return;
        }

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            handleNetherPortal(event, fromWorld, bingoWorldName);
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            handleEndPortal(event, fromWorld, bingoWorldName);
        }
    }

    /**
     * Handle nether portal travel.
     */
    private void handleNetherPortal(PlayerPortalEvent event, World fromWorld, String bingoWorldName) {
        World.Environment fromEnv = fromWorld.getEnvironment();

        if (fromEnv == World.Environment.NORMAL) {
            // Going to nether
            World netherWorld = getOrCreateBingoNether(bingoWorldName);
            if (netherWorld != null) {
                Location to = calculateNetherDestination(event.getFrom(), netherWorld);
                event.setTo(to);
            }
        } else if (fromEnv == World.Environment.NETHER) {
            // Going to overworld
            World overworldWorld = plugin.getServer().getWorld(bingoWorldName);
            if (overworldWorld != null) {
                Location to = calculateOverworldDestination(event.getFrom(), overworldWorld);
                event.setTo(to);
            }
        }
    }

    /**
     * Handle end portal travel.
     */
    private void handleEndPortal(PlayerPortalEvent event, World fromWorld, String bingoWorldName) {
        World.Environment fromEnv = fromWorld.getEnvironment();

        if (fromEnv == World.Environment.NORMAL) {
            // Going to the end
            World endWorld = getOrCreateBingoEnd(bingoWorldName);
            if (endWorld != null) {
                // End portal always spawns at obsidian platform
                Location to = new Location(endWorld, 100.5, 49, 0.5);
                event.setTo(to);
            }
        } else if (fromEnv == World.Environment.THE_END) {
            // Leaving the end (through end gateway or portal)
            World overworldWorld = plugin.getServer().getWorld(bingoWorldName);
            if (overworldWorld != null) {
                Location spawnLoc = overworldWorld.getSpawnLocation();
                event.setTo(spawnLoc);
            }
        }
    }

    /**
     * Check if a world is part of the bingo world system.
     */
    private boolean isBingoWorld(String worldName, String bingoWorldName) {
        return worldName.equals(bingoWorldName) ||
               worldName.equals(bingoWorldName + "_nether") ||
               worldName.equals(bingoWorldName + "_the_end");
    }

    /**
     * Get or create the bingo nether world.
     */
    private World getOrCreateBingoNether(String bingoWorldName) {
        String netherName = bingoWorldName + "_nether";
        World netherWorld = plugin.getServer().getWorld(netherName);

        if (netherWorld == null) {
            plugin.getLogger().info("Creating bingo nether world: " + netherName);

            // Get seed from overworld
            World overworld = plugin.getServer().getWorld(bingoWorldName);
            long seed = overworld != null ? overworld.getSeed() : System.currentTimeMillis();

            WorldCreator creator = new WorldCreator(netherName);
            creator.environment(World.Environment.NETHER);
            creator.seed(seed);

            netherWorld = creator.createWorld();

            if (netherWorld != null) {
                plugin.getLogger().info("Created bingo nether world with seed: " + seed);
            }
        }

        return netherWorld;
    }

    /**
     * Get or create the bingo end world.
     */
    private World getOrCreateBingoEnd(String bingoWorldName) {
        String endName = bingoWorldName + "_the_end";
        World endWorld = plugin.getServer().getWorld(endName);

        if (endWorld == null) {
            plugin.getLogger().info("Creating bingo end world: " + endName);

            // Get seed from overworld
            World overworld = plugin.getServer().getWorld(bingoWorldName);
            long seed = overworld != null ? overworld.getSeed() : System.currentTimeMillis();

            WorldCreator creator = new WorldCreator(endName);
            creator.environment(World.Environment.THE_END);
            creator.seed(seed);

            endWorld = creator.createWorld();

            if (endWorld != null) {
                plugin.getLogger().info("Created bingo end world with seed: " + seed);
            }
        }

        return endWorld;
    }

    /**
     * Calculate destination in the nether (overworld coords / 8).
     */
    private Location calculateNetherDestination(Location from, World nether) {
        double x = from.getX() / 8.0;
        double z = from.getZ() / 8.0;

        // Find safe Y
        int y = findSafeY(nether, (int) x, (int) z);

        return new Location(nether, x, y, z, from.getYaw(), from.getPitch());
    }

    /**
     * Calculate destination in the overworld (nether coords * 8).
     */
    private Location calculateOverworldDestination(Location from, World overworld) {
        double x = from.getX() * 8.0;
        double z = from.getZ() * 8.0;

        // Find safe Y
        int y = overworld.getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(overworld, x, y, z, from.getYaw(), from.getPitch());
    }

    /**
     * Find a safe Y level in a world.
     */
    private int findSafeY(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            // In nether, search from bottom up for 2-block air space
            for (int y = 32; y < 120; y++) {
                if (world.getBlockAt(x, y, z).isEmpty() &&
                    world.getBlockAt(x, y + 1, z).isEmpty() &&
                    !world.getBlockAt(x, y - 1, z).isEmpty()) {
                    return y;
                }
            }
            return 64; // Default
        } else {
            return world.getHighestBlockYAt(x, z) + 1;
        }
    }
}
