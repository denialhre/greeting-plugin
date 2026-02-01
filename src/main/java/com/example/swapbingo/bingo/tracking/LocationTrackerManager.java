package com.example.swapbingo.bingo.tracking;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.tasks.TaskType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages location tracking compasses for biomes and structures.
 */
public class LocationTrackerManager {

    private final SwapBingoPlugin plugin;
    private final NamespacedKey trackerKey;
    private final NamespacedKey targetTypeKey;
    private final NamespacedKey targetNameKey;
    private final NamespacedKey targetWorldKey;

    // Track active compasses for each player
    private final Map<UUID, List<TrackerInfo>> playerTrackers = new HashMap<>();

    public LocationTrackerManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.trackerKey = new NamespacedKey(plugin, "bingo_tracker");
        this.targetTypeKey = new NamespacedKey(plugin, "tracker_type");
        this.targetNameKey = new NamespacedKey(plugin, "tracker_target");
        this.targetWorldKey = new NamespacedKey(plugin, "tracker_world");
    }

    /**
     * Info about a tracked location.
     */
    public record TrackerInfo(TaskType type, String targetName, Location location, World.Environment dimension) {}

    /**
     * Attempt to purchase a location tracker for a biome or structure.
     * @return true if purchase was successful
     */
    public boolean purchaseTracker(Player player, TaskType type, String targetName, String displayName) {
        if (!plugin.getConfigManager().isLocationPurchaseEnabled()) {
            player.sendMessage(Component.text("Location tracking is disabled.").color(NamedTextColor.RED));
            return false;
        }

        int cost = plugin.getPointsManager().getLocationTrackCost();

        // Check if player has enough points
        if (!plugin.getPointsManager().hasEnoughPoints(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("You need " + cost + " point" + (cost > 1 ? "s" : "") + " to track this location!")
                .color(NamedTextColor.RED));
            return false;
        }

        // Find the nearest location
        Location targetLocation = findNearestLocation(player, type, targetName);

        if (targetLocation == null) {
            player.sendMessage(Component.text("Could not find a nearby " + displayName + "!")
                .color(NamedTextColor.RED));
            return false;
        }

        // Deduct points
        plugin.getPointsManager().removePoints(player.getUniqueId(), cost);

        // Create and give tracking compass
        ItemStack compass = createTrackingCompass(type, targetName, displayName, targetLocation);
        player.getInventory().addItem(compass);

        // Track this compass
        TrackerInfo info = new TrackerInfo(type, targetName, targetLocation, targetLocation.getWorld().getEnvironment());
        playerTrackers.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(info);

        // Notify player
        String dimName = getDimensionName(targetLocation.getWorld().getEnvironment());
        int distance = (int) player.getLocation().distance(targetLocation);

        player.sendMessage(Component.text("Tracking compass created for: ").color(NamedTextColor.GREEN)
            .append(Component.text(displayName).color(NamedTextColor.YELLOW))
            .append(Component.text(" (" + dimName + ", ~" + distance + " blocks away)").color(NamedTextColor.GRAY)));

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        return true;
    }

    /**
     * Find the nearest location for a biome or structure.
     */
    private Location findNearestLocation(Player player, TaskType type, String targetName) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        // Determine which world to search based on target
        World searchWorld = determineSearchWorld(world, type, targetName);
        if (searchWorld == null) return null;

        Location searchLoc = searchWorld == world ? playerLoc : getEquivalentLocation(playerLoc, searchWorld);

        if (type == TaskType.VISIT_BIOME) {
            return findNearestBiome(searchWorld, searchLoc, targetName);
        } else if (type == TaskType.DISCOVER_STRUCTURE) {
            return findNearestStructure(searchWorld, searchLoc, targetName);
        }

        return null;
    }

    /**
     * Determine which world to search in based on the target.
     */
    private World determineSearchWorld(World currentWorld, TaskType type, String targetName) {
        String name = targetName.toLowerCase();

        // Check for nether-specific targets
        if (isNetherTarget(name)) {
            return getLinkedWorld(currentWorld, World.Environment.NETHER);
        }

        // Check for end-specific targets
        if (isEndTarget(name)) {
            return getLinkedWorld(currentWorld, World.Environment.THE_END);
        }

        // Default to current world's overworld equivalent for biomes/structures
        if (type == TaskType.VISIT_BIOME || type == TaskType.DISCOVER_STRUCTURE) {
            return getLinkedWorld(currentWorld, World.Environment.NORMAL);
        }

        return currentWorld;
    }

    /**
     * Check if target is nether-specific.
     */
    private boolean isNetherTarget(String name) {
        return name.contains("nether") || name.contains("basalt") || name.contains("soul_sand") ||
               name.contains("warped") || name.contains("crimson") || name.equals("bastion_remnant") ||
               name.equals("nether_fortress") || name.equals("fortress");
    }

    /**
     * Check if target is end-specific.
     */
    private boolean isEndTarget(String name) {
        return name.contains("end_") || name.equals("end_city") || name.equals("end_highlands") ||
               name.equals("end_midlands") || name.equals("end_barrens") || name.equals("small_end_islands");
    }

    /**
     * Get the linked world for a specific environment.
     */
    private World getLinkedWorld(World currentWorld, World.Environment targetEnv) {
        // Get the bingo world base name
        String bingoWorldName = plugin.getConfigManager().getBingoWorldName();

        String worldName = switch (targetEnv) {
            case NORMAL -> bingoWorldName;
            case NETHER -> bingoWorldName + "_nether";
            case THE_END -> bingoWorldName + "_the_end";
            default -> bingoWorldName;
        };

        World world = plugin.getServer().getWorld(worldName);

        // If world doesn't exist, try to create it
        if (world == null && targetEnv != World.Environment.NORMAL) {
            // The world might need to be created when entering portals
            plugin.getLogger().info("World " + worldName + " not found for tracking");
        }

        return world;
    }

    /**
     * Get equivalent location in another world (for nether/overworld conversion).
     */
    private Location getEquivalentLocation(Location loc, World targetWorld) {
        double x = loc.getX();
        double z = loc.getZ();

        World.Environment fromEnv = loc.getWorld().getEnvironment();
        World.Environment toEnv = targetWorld.getEnvironment();

        // Nether<->Overworld conversion (1:8 ratio)
        if (fromEnv == World.Environment.NETHER && toEnv == World.Environment.NORMAL) {
            x *= 8;
            z *= 8;
        } else if (fromEnv == World.Environment.NORMAL && toEnv == World.Environment.NETHER) {
            x /= 8;
            z /= 8;
        }

        return new Location(targetWorld, x, 64, z);
    }

    /**
     * Find the nearest biome.
     */
    private Location findNearestBiome(World world, Location searchLoc, String biomeName) {
        try {
            Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(biomeName.toLowerCase()));
            if (biome == null) {
                plugin.getLogger().warning("Unknown biome: " + biomeName);
                return null;
            }

            // Use Paper's locateBiome
            Location result = world.locateNearestBiome(searchLoc, biome, 6400, 32);
            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to locate biome " + biomeName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Find the nearest structure.
     */
    private Location findNearestStructure(World world, Location searchLoc, String structureName) {
        try {
            // Map common names to structure types
            String mappedName = mapStructureName(structureName);

            // Use the new Registry-based API for structures
            org.bukkit.generator.structure.Structure structure =
                Registry.STRUCTURE.get(NamespacedKey.minecraft(mappedName));

            if (structure == null) {
                plugin.getLogger().warning("Unknown structure type: " + structureName + " (mapped: " + mappedName + ")");
                return null;
            }

            // Use the new locateNearestStructure method with Structure
            org.bukkit.util.StructureSearchResult result =
                world.locateNearestStructure(searchLoc, structure, 10000, false);

            return result != null ? result.getLocation() : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to locate structure " + structureName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Map structure names to Bukkit structure types.
     */
    private String mapStructureName(String name) {
        return switch (name.toLowerCase()) {
            case "pillager_outpost" -> "pillager_outpost";
            case "mineshaft" -> "mineshaft";
            case "mineshaft_mesa" -> "mineshaft_mesa";
            case "woodland_mansion", "mansion" -> "mansion";
            case "jungle_pyramid", "jungle_temple" -> "jungle_pyramid";
            case "desert_pyramid", "desert_temple" -> "desert_pyramid";
            case "igloo" -> "igloo";
            case "shipwreck" -> "shipwreck";
            case "swamp_hut", "witch_hut" -> "swamp_hut";
            case "stronghold" -> "stronghold";
            case "monument", "ocean_monument" -> "monument";
            case "ocean_ruin", "ocean_ruins" -> "ocean_ruin";
            case "fortress", "nether_fortress" -> "fortress";
            case "endcity", "end_city" -> "endcity";
            case "buried_treasure" -> "buried_treasure";
            case "village" -> "village";
            case "nether_fossil" -> "nether_fossil";
            case "bastion_remnant", "bastion" -> "bastion_remnant";
            case "ruined_portal" -> "ruined_portal";
            case "ancient_city" -> "ancient_city";
            case "trail_ruins" -> "trail_ruins";
            case "trial_chambers" -> "trial_chambers";
            default -> name.toLowerCase();
        };
    }

    /**
     * Create a tracking compass item.
     */
    private ItemStack createTrackingCompass(TaskType type, String targetName, String displayName, Location targetLocation) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();

        // Set display name
        String typeStr = type == TaskType.VISIT_BIOME ? "Biome" : "Structure";
        meta.displayName(Component.text(typeStr + " Tracker: ").color(NamedTextColor.GOLD)
            .append(Component.text(displayName).color(NamedTextColor.YELLOW))
            .decoration(TextDecoration.ITALIC, false));

        // Set lore
        String dimName = getDimensionName(targetLocation.getWorld().getEnvironment());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tracking: ").color(NamedTextColor.GRAY)
            .append(Component.text(displayName).color(NamedTextColor.WHITE)));
        lore.add(Component.text("Dimension: ").color(NamedTextColor.GRAY)
            .append(Component.text(dimName).color(NamedTextColor.AQUA)));
        lore.add(Component.text("X: " + targetLocation.getBlockX() + " Z: " + targetLocation.getBlockZ())
            .color(NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Hold to track direction").color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);

        // Set the compass to point to the location
        meta.setLodestone(targetLocation);
        meta.setLodestoneTracked(false); // Don't require actual lodestone block

        // Store metadata for later identification
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(trackerKey, PersistentDataType.BOOLEAN, true);
        pdc.set(targetTypeKey, PersistentDataType.STRING, type.name());
        pdc.set(targetNameKey, PersistentDataType.STRING, targetName);
        pdc.set(targetWorldKey, PersistentDataType.STRING, targetLocation.getWorld().getName());

        compass.setItemMeta(meta);
        return compass;
    }

    /**
     * Check if player has enough diamonds.
     */
    private boolean hasEnoughDiamonds(Player player, int amount) {
        return player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), amount);
    }

    /**
     * Remove diamonds from player's inventory.
     */
    private void removeDiamonds(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    /**
     * Get human-readable dimension name.
     */
    private String getDimensionName(World.Environment env) {
        return switch (env) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Unknown";
        };
    }

    /**
     * Update all tracking compasses after a swap.
     * Re-locates the nearest target from the new position.
     */
    public void updateTrackersAfterSwap(Player player) {
        if (!plugin.getConfigManager().isLocationPurchaseEnabled()) return;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.COMPASS) continue;
            if (!(item.getItemMeta() instanceof CompassMeta meta)) continue;

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.has(trackerKey, PersistentDataType.BOOLEAN)) continue;

            // This is a tracking compass - update it
            String typeStr = pdc.get(targetTypeKey, PersistentDataType.STRING);
            String targetName = pdc.get(targetNameKey, PersistentDataType.STRING);

            if (typeStr == null || targetName == null) continue;

            TaskType type = TaskType.valueOf(typeStr);

            // Find new nearest location
            Location newLocation = findNearestLocation(player, type, targetName);

            if (newLocation != null) {
                meta.setLodestone(newLocation);
                meta.setLodestoneTracked(false);

                // Update lore with new coordinates
                String dimName = getDimensionName(newLocation.getWorld().getEnvironment());
                List<Component> lore = meta.lore();
                if (lore != null && lore.size() >= 3) {
                    lore.set(1, Component.text("Dimension: ").color(NamedTextColor.GRAY)
                        .append(Component.text(dimName).color(NamedTextColor.AQUA)));
                    lore.set(2, Component.text("X: " + newLocation.getBlockX() + " Z: " + newLocation.getBlockZ())
                        .color(NamedTextColor.DARK_GRAY));
                    meta.lore(lore);
                }

                pdc.set(targetWorldKey, PersistentDataType.STRING, newLocation.getWorld().getName());
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * Check if an item is a bingo tracking compass.
     */
    public boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!(item.getItemMeta() instanceof CompassMeta meta)) return false;
        return meta.getPersistentDataContainer().has(trackerKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Clear all tracker data for a player.
     */
    public void clearPlayerTrackers(UUID playerId) {
        playerTrackers.remove(playerId);
    }

    /**
     * Clear all tracker data.
     */
    public void clearAll() {
        playerTrackers.clear();
    }
}
