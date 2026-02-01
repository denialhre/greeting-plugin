package com.example.swapbingo.world;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.RelocationItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * Manages barrier cages for player spawning in a circular pattern.
 */
public class SpawnCageManager {

    private final SwapBingoPlugin plugin;
    private final Set<Location> cageBlockLocations = new HashSet<>();
    private final Map<UUID, Boolean> gracePeriodActive = new HashMap<>();

    // Cage phase tracking
    private boolean inCagePhase = false;
    private List<Player> currentCagePlayers = new ArrayList<>();
    private BukkitRunnable currentCountdownTask;
    private Runnable pendingRelease;

    public SpawnCageManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculate cage positions in a circular pattern around center.
     * Returns positions for the specified number of players.
     */
    public List<Location> calculateCagePositions(World world, int playerCount) {
        List<Location> positions = new ArrayList<>();
        int distance = plugin.getConfigManager().getCageDistance();

        // Center is 0, 0
        double centerX = 0;
        double centerZ = 0;

        // Calculate angles for circular distribution
        for (int i = 0; i < playerCount; i++) {
            double angle = (2 * Math.PI * i) / playerCount;
            int x = (int) Math.round(centerX + distance * Math.cos(angle));
            int z = (int) Math.round(centerZ + distance * Math.sin(angle));
            positions.add(new Location(world, x, 0, z)); // Y will be determined later
        }

        return positions;
    }

    // Minimum Y level for cages
    private static final int MIN_CAGE_Y = 150;

    /**
     * Find optimal Y level for all cages (above terrain, same for all).
     * Minimum Y level is 150 to ensure good visibility.
     */
    public int findOptimalYLevel(World world, List<Location> positions) {
        int maxY = MIN_CAGE_Y; // Minimum starting Y is now 150

        for (Location pos : positions) {
            // Find highest solid block at this XZ
            int highestY = world.getHighestBlockYAt(pos.getBlockX(), pos.getBlockZ());
            maxY = Math.max(maxY, highestY + 10); // 10 blocks above highest terrain
        }

        // Cap at reasonable height
        return Math.min(maxY, 250);
    }

    /**
     * Spawn players in barrier cages and show loading screen.
     * Returns when all cages are built and players teleported.
     */
    public void spawnPlayersInCages(World world, List<Player> players, Runnable onComplete) {
        cageBlockLocations.clear();

        // Track cage phase
        inCagePhase = true;
        currentCagePlayers = new ArrayList<>(players);

        // Calculate positions
        List<Location> positions = calculateCagePositions(world, players.size());
        int yLevel = findOptimalYLevel(world, positions);

        // Update Y for all positions
        for (Location pos : positions) {
            pos.setY(yLevel);
        }

        // Show loading title to all players
        Title loadingTitle = Title.title(
            Component.text("Generating World...").color(NamedTextColor.GOLD),
            Component.text("Please wait").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(500))
        );

        for (Player player : players) {
            player.showTitle(loadingTitle);
        }

        // Build cages and teleport players
        // NOTE: Don't give relocation items or maps here - they will be given
        // after difficulty vote completes in startCountdownAndRelease()
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location cagePos = positions.get(i);

            // Build barrier cage
            buildCage(cagePos);

            // Center player in cage
            Location playerPos = cagePos.clone().add(0.5, 1, 0.5);
            playerPos.setYaw(0);
            playerPos.setPitch(0);

            player.teleport(playerPos);
        }

        // Schedule completion
        plugin.getServer().getScheduler().runTaskLater(plugin, onComplete, 20L); // 1 second delay
    }

    /**
     * Give a map of the surroundings to a player.
     * Pre-loads chunks around the center to help with map rendering.
     */
    private void giveMap(Player player, Location center) {
        World world = player.getWorld();

        // Remove any existing area maps first
        removeOldMaps(player);

        // Pre-load chunks around the map center to help with rendering
        preloadChunksForMap(world, center.getBlockX(), center.getBlockZ());

        // Create a new map
        org.bukkit.map.MapView mapView = plugin.getServer().createMap(world);
        mapView.setCenterX(center.getBlockX());
        mapView.setCenterZ(center.getBlockZ());
        mapView.setScale(org.bukkit.map.MapView.Scale.NORMAL);
        mapView.setTrackingPosition(true);

        org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);
        meta.displayName(net.kyori.adventure.text.Component.text("Area Map")
            .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        // Add identifier to remove old maps later
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "area_map"),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1
        );

        mapItem.setItemMeta(meta);
        player.getInventory().addItem(mapItem);

        // Send map update to player
        player.sendMap(mapView);
    }

    /**
     * Pre-load chunks around the map center to help with map rendering.
     */
    private void preloadChunksForMap(World world, int centerX, int centerZ) {
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;

        // Load a 9x9 chunk area (144 blocks radius covers NORMAL scale map)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
            }
        }
    }

    /**
     * Remove old area maps from player inventory.
     */
    private void removeOldMaps(Player player) {
        org.bukkit.NamespacedKey mapKey = new org.bukkit.NamespacedKey(plugin, "area_map");
        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(mapKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                    inventory.setItem(i, null);
                }
            }
        }
    }

    /**
     * Build a 5x5x4 barrier cage at the given location.
     * The location is the floor center. Interior is 3x3x2.
     */
    private void buildCage(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Build a 5x5 floor and walls, 4 high with roof
        // Interior is 3x3x2 (player has room to move)
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    // Skip interior (3x3x2 space where player stands)
                    boolean isInteriorX = x >= cx - 1 && x <= cx + 1;
                    boolean isInteriorZ = z >= cz - 1 && z <= cz + 1;
                    boolean isInteriorY = y >= cy + 1 && y <= cy + 2;

                    if (isInteriorX && isInteriorZ && isInteriorY) {
                        // Clear interior space (in case there's terrain)
                        Location blockLoc = new Location(world, x, y, z);
                        world.getBlockAt(blockLoc).setType(Material.AIR);
                        continue;
                    }

                    // Build barrier on edges and floor/roof
                    boolean isEdge = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2;
                    boolean isFloorOrRoof = y == cy || y == cy + 3;

                    if (isEdge || isFloorOrRoof) {
                        Location blockLoc = new Location(world, x, y, z);
                        world.getBlockAt(blockLoc).setType(Material.BARRIER);
                        cageBlockLocations.add(blockLoc);
                    }
                }
            }
        }
    }

    /**
     * Start countdown and remove cages when done.
     * Shows 10-second warning in chat, then 5-second title countdown.
     */
    public void startCountdownAndRelease(List<Player> players, Runnable onRelease) {
        // Store for potential restart
        this.pendingRelease = onRelease;
        this.currentCagePlayers = new ArrayList<>(players);

        // Cancel any existing countdown
        if (currentCountdownTask != null) {
            currentCountdownTask.cancel();
        }

        int countdown = plugin.getConfigManager().getStartCountdown();

        // Give relocation items and maps now (after difficulty vote is complete)
        for (Player player : players) {
            if (player.isOnline()) {
                RelocationItem.give(player);
                giveMap(player, player.getLocation());
            }
        }

        // Enable relocation voting - pass callback to restart countdown on successful relocation
        plugin.getRelocationVoteManager().enableVoting(players, this::restartCountdown);

        // Show initial countdown message
        for (Player player : players) {
            player.sendMessage(Component.text("Game starting in ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(countdown + " seconds")
                    .color(NamedTextColor.GREEN))
                .append(Component.text("! Check your bingo card!")
                    .color(NamedTextColor.YELLOW)));
        }

        currentCountdownTask = new BukkitRunnable() {
            int remaining = countdown;

            @Override
            public void run() {
                if (remaining <= 0) {
                    // End cage phase
                    inCagePhase = false;

                    // Remove all cages
                    removeCages();

                    // Relocation items and maps were already removed at 5-second mark by endVoting()

                    // Apply fall damage grace period
                    applyGracePeriod(players);

                    // Show GO title
                    Title goTitle = Title.title(
                        Component.text("GO!").color(NamedTextColor.GREEN),
                        Component.text("Good luck!").color(NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
                    );

                    for (Player player : players) {
                        player.showTitle(goTitle);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }

                    currentCountdownTask = null;
                    onRelease.run();
                    cancel();
                    return;
                }

                // 10-second warning in chat
                if (remaining == 10) {
                    for (Player player : players) {
                        player.sendMessage(Component.text("⚠ ")
                            .color(NamedTextColor.RED)
                            .append(Component.text("10 seconds remaining!")
                                .color(NamedTextColor.YELLOW)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    }
                }

                // Final 5-second title countdown
                if (remaining <= 5) {
                    // At exactly 5 seconds: end relocation voting
                    if (remaining == 5) {
                        boolean anyoneVoted = plugin.getRelocationVoteManager().endVoting();
                        if (anyoneVoted) {
                            // Show failure message - not enough votes
                            for (Player player : players) {
                                player.sendMessage(Component.text("Relocation failed! ")
                                    .color(NamedTextColor.RED)
                                    .append(Component.text("Not enough votes to relocate.")
                                        .color(NamedTextColor.GRAY)));
                            }
                        }
                    }

                    NamedTextColor color = switch (remaining) {
                        case 5 -> NamedTextColor.GREEN;
                        case 4 -> NamedTextColor.YELLOW;
                        case 3 -> NamedTextColor.YELLOW;
                        case 2 -> NamedTextColor.GOLD;
                        case 1 -> NamedTextColor.RED;
                        default -> NamedTextColor.WHITE;
                    };

                    Title countdownTitle = Title.title(
                        Component.text(String.valueOf(remaining)).color(color),
                        Component.text("Get ready!").color(NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                    );

                    for (Player player : players) {
                        player.showTitle(countdownTitle);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                            remaining <= 3 ? 0.8f : 1.0f);
                    }
                }

                remaining--;
            }
        };

        currentCountdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Restart the countdown (called after relocation).
     */
    public void restartCountdown() {
        if (pendingRelease != null && currentCagePlayers != null && !currentCagePlayers.isEmpty()) {
            // Cancel current countdown
            if (currentCountdownTask != null) {
                currentCountdownTask.cancel();
                currentCountdownTask = null;
            }

            // Restart with fresh countdown
            startCountdownAndRelease(currentCagePlayers, pendingRelease);
        }
    }

    /**
     * Check if currently in cage phase (before countdown finishes).
     */
    public boolean isInCagePhase() {
        return inCagePhase;
    }

    /**
     * Get the current players in the cage phase.
     */
    public List<Player> getCurrentCagePlayers() {
        return new ArrayList<>(currentCagePlayers);
    }

    /**
     * Remove all cage blocks (both initial cages and relocation cages).
     * Spreads removal over multiple ticks to prevent lag spikes.
     */
    private void removeCages() {
        // Collect all blocks to remove
        List<Location> allBlocks = new ArrayList<>(cageBlockLocations);
        allBlocks.addAll(plugin.getRelocationVoteManager().getRelocationCageBlocks());

        cageBlockLocations.clear();

        // Remove blocks in batches to prevent lag spike
        final int BLOCKS_PER_TICK = 50;
        if (allBlocks.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int removed = 0;
                while (index < allBlocks.size() && removed < BLOCKS_PER_TICK) {
                    Location loc = allBlocks.get(index);
                    if (loc.getWorld() != null) {
                        loc.getWorld().getBlockAt(loc).setType(Material.AIR, false);
                    }
                    index++;
                    removed++;
                }

                if (index >= allBlocks.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Clear relocation cages tracking
        plugin.getRelocationVoteManager().getRelocationCageBlocks().clear();
    }

    /**
     * Apply fall damage grace period to players.
     */
    private void applyGracePeriod(List<Player> players) {
        int gracePeriod = plugin.getConfigManager().getFallGracePeriod();

        for (Player player : players) {
            gracePeriodActive.put(player.getUniqueId(), true);
            player.setAllowFlight(true); // Temporary flight to prevent fall damage
        }

        // Remove grace period after delay
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : players) {
                gracePeriodActive.remove(player.getUniqueId());
                if (player.isOnline() && player.getGameMode() == GameMode.SURVIVAL) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        }, gracePeriod * 20L);
    }

    /**
     * Check if player is in grace period.
     */
    public boolean isInGracePeriod(UUID playerId) {
        return gracePeriodActive.getOrDefault(playerId, false);
    }

    /**
     * Clear all cage data (cleanup).
     */
    public void cleanup() {
        removeCages();
        gracePeriodActive.clear();
        inCagePhase = false;
        currentCagePlayers.clear();
        pendingRelease = null;
        if (currentCountdownTask != null) {
            currentCountdownTask.cancel();
            currentCountdownTask = null;
        }
    }
}
