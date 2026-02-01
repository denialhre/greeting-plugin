package com.example.swapbingo.core;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.RelocationItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.time.Duration;
import java.util.*;

/**
 * Manages spawn relocation voting during the cage phase.
 * Players click compass to vote for relocation. When majority is reached, instant teleport.
 * Voting ends when countdown reaches 5 seconds.
 */
public class RelocationVoteManager {

    private final SwapBingoPlugin plugin;

    // Vote tracking - just tracks who wants to relocate
    private final Set<UUID> wantsToRelocate = new HashSet<>();
    private boolean votingEnabled = false;

    // Cooldown tracking to prevent multiple vote registrations
    private final Map<UUID, Long> lastVoteTime = new HashMap<>();
    private static final long VOTE_COOLDOWN_MS = 2000; // 2 second cooldown per player

    // Flag to prevent votes during active relocation
    private boolean relocationInProgress = false;

    // Track used relocation offsets to avoid repeats
    private final List<int[]> usedOffsets = new ArrayList<>();

    // Track cage blocks for cleanup
    private final Set<Location> relocationCageBlocks = new HashSet<>();

    // Reference to current players and callback
    private List<Player> currentPlayers = new ArrayList<>();
    private Runnable pendingCountdownRestart;

    // Relocation settings
    private static final int RELOCATION_DISTANCE = 1000;
    private static final int MIN_CAGE_Y = 150;

    public RelocationVoteManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable voting for the current cage phase.
     */
    public void enableVoting(List<Player> players, Runnable countdownRestart) {
        this.currentPlayers = new ArrayList<>(players);
        this.pendingCountdownRestart = countdownRestart;
        this.wantsToRelocate.clear();
        this.votingEnabled = true;
    }

    /**
     * Player clicks compass to vote for relocation.
     */
    public void voteToRelocate(Player player) {
        // Block votes if relocation is currently in progress
        if (relocationInProgress) {
            return; // Silently ignore - relocation happening
        }

        if (!votingEnabled) {
            player.sendMessage(Component.text("Relocation voting is no longer available.")
                .color(NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check cooldown to prevent rapid clicks from registering multiple times
        long now = System.currentTimeMillis();
        Long lastVote = lastVoteTime.get(uuid);
        if (lastVote != null && (now - lastVote) < VOTE_COOLDOWN_MS) {
            return; // Silently ignore rapid clicks
        }
        lastVoteTime.put(uuid, now);

        // Check if already voted
        if (wantsToRelocate.contains(uuid)) {
            player.sendMessage(Component.text("You have already voted to relocate!")
                .color(NamedTextColor.YELLOW));
            return;
        }

        // Add vote
        wantsToRelocate.add(uuid);

        // Broadcast vote
        int votes = wantsToRelocate.size();
        int total = currentPlayers.size();
        int needed = (total / 2) + 1;

        for (Player p : currentPlayers) {
            p.sendMessage(Component.text(player.getName())
                .color(NamedTextColor.AQUA)
                .append(Component.text(" wants to relocate! ")
                    .color(NamedTextColor.GRAY))
                .append(Component.text("(" + votes + "/" + needed + " needed)")
                    .color(votes >= needed ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }

        // Check if majority reached
        if (votes >= needed) {
            performRelocation();
        }
    }

    /**
     * Called when countdown reaches 5 seconds - end voting.
     * @return true if anyone voted (to show failure message)
     */
    public boolean endVoting() {
        if (!votingEnabled) {
            return false;
        }

        votingEnabled = false;
        int votedCount = wantsToRelocate.size();

        // Remove compass and map from all players
        for (Player player : currentPlayers) {
            RelocationItem.remove(player);
            removeOldMaps(player);
        }

        // Clear votes and cooldown tracking
        wantsToRelocate.clear();
        lastVoteTime.clear();

        return votedCount > 0;
    }

    /**
     * Check if voting is currently enabled.
     */
    public boolean isVotingEnabled() {
        return votingEnabled;
    }

    /**
     * Get current vote count.
     */
    public int getVoteCount() {
        return wantsToRelocate.size();
    }

    /**
     * Perform the relocation - teleport all players to new location.
     */
    private void performRelocation() {
        if (currentPlayers == null || currentPlayers.isEmpty()) return;

        // Block all votes during relocation process
        relocationInProgress = true;
        votingEnabled = false; // Disable further voting

        // Remove old relocation cages before building new ones
        removeRelocationCages();

        World world = currentPlayers.get(0).getWorld();

        // Show relocation title
        Title relocatingTitle = Title.title(
            Component.text("RELOCATING!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            Component.text("Moving to new location...").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        );

        for (Player player : currentPlayers) {
            player.showTitle(relocatingTitle);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }

        // Calculate random offset (avoid previously used offsets)
        int[] offset = calculateNewOffset();
        usedOffsets.add(offset);

        int offsetX = offset[0];
        int offsetZ = offset[1];

        // Move all players
        for (Player player : currentPlayers) {
            Location currentLoc = player.getLocation();

            // Calculate new XZ position
            int newX = currentLoc.getBlockX() + offsetX;
            int newZ = currentLoc.getBlockZ() + offsetZ;

            // Calculate Y level: at least MIN_CAGE_Y, or 10 blocks above highest terrain
            int terrainY = world.getHighestBlockYAt(newX, newZ);
            int newY = Math.max(MIN_CAGE_Y, terrainY + 10);

            // Cage floor position (player will be inside)
            Location cageFloor = new Location(world, newX, newY, newZ);

            // Rebuild cage at new location (5x5x4 cage)
            rebuildCageAt(cageFloor);

            // Teleport player to exact center of cage interior
            // Cage floor is at newY, interior is at newY+1 and newY+2
            Location playerPos = new Location(world,
                newX + 0.5, // Center of block
                newY + 1,   // One block above floor
                newZ + 0.5, // Center of block
                currentLoc.getYaw(),
                currentLoc.getPitch());

            player.teleport(playerPos);

            // Give player a new map of the surroundings
            giveMap(player, cageFloor);
        }

        // Notify players
        for (Player player : currentPlayers) {
            player.sendMessage(Component.text("You have been relocated " + RELOCATION_DISTANCE + " blocks!")
                .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Hold the map to fill it with terrain data.")
                .color(NamedTextColor.GRAY));
        }

        // Clear votes for potential next relocation
        wantsToRelocate.clear();
        lastVoteTime.clear();
        // Don't re-enable voting immediately - schedule it after a delay to prevent instant re-votes

        // Restart the 30-second countdown
        if (pendingCountdownRestart != null) {
            pendingCountdownRestart.run();
        }

        // Re-enable voting after a 3-second delay to prevent duplicate vote registrations
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            relocationInProgress = false; // Allow votes again
            if (plugin.getSpawnCageManager().isInCagePhase()) {
                votingEnabled = true;
            }
        }, 60L); // 3 seconds (60 ticks)
    }

    /**
     * Remove all relocation cage blocks.
     */
    private void removeRelocationCages() {
        for (Location loc : relocationCageBlocks) {
            if (loc.getWorld() != null) {
                loc.getWorld().getBlockAt(loc).setType(Material.AIR);
            }
        }
        relocationCageBlocks.clear();
    }

    /**
     * Get the set of relocation cage block locations for external cleanup.
     */
    public Set<Location> getRelocationCageBlocks() {
        return relocationCageBlocks;
    }

    /**
     * Calculate a new random offset that hasn't been used.
     */
    private int[] calculateNewOffset() {
        Random random = new Random();
        int maxAttempts = 100;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random angle
            double angle = random.nextDouble() * 2 * Math.PI;
            int offsetX = (int) Math.round(RELOCATION_DISTANCE * Math.cos(angle));
            int offsetZ = (int) Math.round(RELOCATION_DISTANCE * Math.sin(angle));

            // Check if this offset is too close to any used offset
            boolean tooClose = false;
            for (int[] used : usedOffsets) {
                double distance = Math.sqrt(Math.pow(offsetX - used[0], 2) + Math.pow(offsetZ - used[1], 2));
                if (distance < RELOCATION_DISTANCE / 2) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                return new int[]{offsetX, offsetZ};
            }
        }

        // Fallback: return any random offset
        double angle = random.nextDouble() * 2 * Math.PI;
        return new int[]{
            (int) Math.round(RELOCATION_DISTANCE * Math.cos(angle)),
            (int) Math.round(RELOCATION_DISTANCE * Math.sin(angle))
        };
    }

    /**
     * Rebuild a barrier cage at the new location.
     * Builds a 5x5x4 cage with 3x3x2 interior space.
     */
    private void rebuildCageAt(Location center) {
        World world = center.getWorld();
        if (world == null) return;

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
                        relocationCageBlocks.add(blockLoc);
                    }
                }
            }
        }
    }

    /**
     * Give a map of the surroundings to a player.
     * Pre-loads chunks around the center to help the map render.
     */
    private void giveMap(Player player, Location center) {
        World world = player.getWorld();

        // Remove any existing area maps first
        removeOldMaps(player);

        // Pre-load chunks around the map center to help with rendering
        // A NORMAL scale map covers about 128 blocks, so we load a 9x9 chunk area
        preloadChunksForMap(world, center.getBlockX(), center.getBlockZ());

        // Create a new map
        MapView mapView = plugin.getServer().createMap(world);
        mapView.setCenterX(center.getBlockX());
        mapView.setCenterZ(center.getBlockZ());
        mapView.setScale(MapView.Scale.NORMAL);
        mapView.setTrackingPosition(true);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);
        meta.displayName(Component.text("Area Map")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

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
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(mapKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                    inventory.setItem(i, null);
                }
            }
        }
    }

    /**
     * Reset for a new game.
     */
    public void reset() {
        votingEnabled = false;
        relocationInProgress = false;
        wantsToRelocate.clear();
        lastVoteTime.clear();
        usedOffsets.clear();
        removeRelocationCages();
        currentPlayers.clear();
        pendingCountdownRestart = null;
    }

    /**
     * Clean up all relocation cages (called when game starts/ends).
     */
    public void cleanupCages() {
        removeRelocationCages();
    }
}
