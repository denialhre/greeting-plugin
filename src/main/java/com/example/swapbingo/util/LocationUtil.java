package com.example.swapbingo.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Utility class for location manipulation.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /**
     * Swap locations between two players, preserving yaw and pitch.
     */
    public static void swapLocations(Player player1, Player player2) {
        Location loc1 = player1.getLocation().clone();
        Location loc2 = player2.getLocation().clone();

        // Teleport player1 to player2's location
        player1.teleport(loc2);
        // Teleport player2 to player1's location
        player2.teleport(loc1);
    }

    /**
     * Find a safe spawn location in a world.
     * Searches for a location with solid ground and air above.
     */
    public static Location findSafeSpawn(World world) {
        Location spawn = world.getSpawnLocation();

        // Try to find safe ground starting from spawn
        int startY = world.getHighestBlockYAt(spawn);
        spawn.setY(startY + 1);

        // Ensure the location is safe
        if (isSafe(spawn)) {
            return spawn;
        }

        // Search in a spiral pattern
        for (int radius = 1; radius <= 16; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        Location test = spawn.clone().add(x, 0, z);
                        test.setY(world.getHighestBlockYAt(test) + 1);
                        if (isSafe(test)) {
                            return test;
                        }
                    }
                }
            }
        }

        return spawn;
    }

    /**
     * Check if a location is safe to spawn at.
     */
    public static boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return ground.getType().isSolid()
            && !feet.getType().isSolid()
            && !head.getType().isSolid()
            && !feet.isLiquid()
            && !head.isLiquid();
    }

    /**
     * Get the chunk coordinates for a location.
     */
    public static int[] getChunkCoords(Location location) {
        return new int[] {
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4
        };
    }

    /**
     * Check if two locations are in different chunks.
     */
    public static boolean isDifferentChunk(Location from, Location to) {
        if (from == null || to == null) return true;
        return (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
            || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4);
    }

    /**
     * Calculate distance between two locations (ignoring Y).
     */
    public static double distance2D(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
