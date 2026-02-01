package com.example.swapbingo.world;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.util.LocationUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Manages world creation, reset, and regeneration.
 */
public class WorldManager {

    private final SwapBingoPlugin plugin;
    private final Random random;

    public WorldManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Create or get the bingo world.
     */
    public World getOrCreateBingoWorld() {
        String worldName = plugin.getConfigManager().getBingoWorldName();
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            world = createWorld(worldName, getNextSeed());
        }

        return world;
    }

    /**
     * Reset the bingo world with a new seed and teleport players to it.
     * @param playersToTeleport Players to teleport to the new world
     * @param onComplete Callback when world is ready and players are teleported
     */
    public void resetBingoWorld(List<Player> playersToTeleport, Runnable onComplete) {
        String worldName = plugin.getConfigManager().getBingoWorldName();
        World lobbyWorld = Bukkit.getWorld(plugin.getConfigManager().getLobbyWorld());

        if (lobbyWorld == null) {
            lobbyWorld = Bukkit.getWorlds().get(0); // Fallback to main world
        }

        // Teleport all players to lobby first
        Location lobbySpawn = lobbyWorld.getSpawnLocation();
        for (Player player : playersToTeleport) {
            if (player.isOnline()) {
                player.teleport(lobbySpawn);
            }
        }

        // Unload the main bingo world and its dimensions
        World oldWorld = Bukkit.getWorld(worldName);
        if (oldWorld != null) {
            Bukkit.unloadWorld(oldWorld, false); // Don't save
        }

        // Unload nether
        World netherWorld = Bukkit.getWorld(worldName + "_nether");
        if (netherWorld != null) {
            Bukkit.unloadWorld(netherWorld, false);
        }

        // Unload end
        World endWorld = Bukkit.getWorld(worldName + "_the_end");
        if (endWorld != null) {
            Bukkit.unloadWorld(endWorld, false);
        }

        // Delete world folders asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            deleteWorldFolder(worldName);
            deleteWorldFolder(worldName + "_nether");
            deleteWorldFolder(worldName + "_the_end");

            // Create new world on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                World newWorld = createWorld(worldName, getNextSeed());
                if (newWorld != null) {
                    plugin.getLogger().info("Bingo world reset with new seed");

                    // Teleport players to the new world
                    teleportToSpawn(newWorld, playersToTeleport);

                    // Call completion callback
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
        });
    }

    /**
     * Legacy method for compatibility.
     */
    public void resetBingoWorld(List<Player> playersToTeleport) {
        resetBingoWorld(playersToTeleport, null);
    }

    /**
     * Create a new world with the given name and seed.
     */
    public World createWorld(String name, long seed) {
        WorldCreator creator = new WorldCreator(name);
        creator.seed(seed);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);

        World world = creator.createWorld();
        if (world != null) {
            plugin.getLogger().info("Created world '" + name + "' with seed " + seed);
        }
        return world;
    }

    /**
     * Get the next seed to use.
     */
    private long getNextSeed() {
        if (plugin.getConfigManager().useRandomSeed()) {
            return random.nextLong();
        }
        return plugin.getConfigManager().getFixedSeed();
    }

    /**
     * Delete a world folder.
     */
    private void deleteWorldFolder(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteRecursively(worldFolder);
            plugin.getLogger().info("Deleted world folder: " + worldName);
        }
    }

    /**
     * Recursively delete a directory.
     */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    /**
     * Teleport players to a world's spawn.
     */
    public void teleportToSpawn(World world, List<Player> players) {
        Location spawn = LocationUtil.findSafeSpawn(world);
        for (Player player : players) {
            if (player.isOnline()) {
                player.teleport(spawn);
            }
        }
    }

    /**
     * Get the lobby world.
     */
    public World getLobbyWorld() {
        World lobby = Bukkit.getWorld(plugin.getConfigManager().getLobbyWorld());
        if (lobby == null) {
            lobby = Bukkit.getWorlds().get(0);
        }
        return lobby;
    }
}
