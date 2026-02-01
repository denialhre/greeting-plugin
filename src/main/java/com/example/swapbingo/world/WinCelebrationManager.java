package com.example.swapbingo.world;

import com.example.swapbingo.SwapBingoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * Manages win celebration with podium and fireworks.
 */
public class WinCelebrationManager {

    private final SwapBingoPlugin plugin;
    private final Set<Location> podiumBlocks = new HashSet<>();
    private BukkitRunnable fireworkTask;

    public WinCelebrationManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Celebrate a win - build podium, teleport players, spawn fireworks.
     * @param winner The winning player
     * @param allPlayers All players in the game
     * @param onCountdownComplete Called when countdown finishes
     */
    public void celebrate(Player winner, List<Player> allPlayers, Runnable onCountdownComplete) {
        World world = winner.getWorld();

        // Find a good location for podium (near 0, 0)
        Location podiumCenter = findPodiumLocation(world);

        // Build podium
        buildPodium(podiumCenter);

        // Teleport winner to top of podium
        Location winnerPos = podiumCenter.clone().add(0.5, 4, 0.5);
        winner.teleport(winnerPos);
        winner.setGameMode(GameMode.ADVENTURE);

        // Teleport other players around the podium
        List<Player> others = new ArrayList<>(allPlayers);
        others.remove(winner);
        teleportSpectatorsAroundPodium(podiumCenter, others);

        // Show win title to everyone
        Title winTitle = Title.title(
            Component.text("BINGO!").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            Component.text(winner.getName() + " wins!").color(NamedTextColor.GREEN),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
        );

        for (Player player : allPlayers) {
            player.showTitle(winTitle);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Start fireworks if enabled
        if (plugin.getConfigManager().isFireworksEnabled()) {
            startFireworks(podiumCenter, allPlayers);
        }

        // Start countdown to next round
        int delay = plugin.getConfigManager().getNextRoundDelay();
        startNextRoundCountdown(allPlayers, delay, onCountdownComplete);
    }

    /**
     * Find a good location for the podium.
     */
    private Location findPodiumLocation(World world) {
        int x = 0;
        int z = 0;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x, y, z);
    }

    /**
     * Build a winner's podium.
     */
    private void buildPodium(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int cageRadius = 8;
        int cageHeight = 25;

        // Clear the ENTIRE interior area first (trees, terrain, everything)
        for (int x = cx - cageRadius; x <= cx + cageRadius; x++) {
            for (int z = cz - cageRadius; z <= cz + cageRadius; z++) {
                for (int y = cy; y <= cy + cageHeight; y++) {
                    Location loc = new Location(world, x, y, z);
                    world.getBlockAt(loc).setType(Material.AIR);
                }
            }
        }

        // Build 3-tier podium (center is highest)
        // First tier (ground level) - gold block base 3x3
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                Location loc = new Location(world, x, cy, z);
                world.getBlockAt(loc).setType(Material.GOLD_BLOCK);
                podiumBlocks.add(loc);
            }
        }

        // Second tier - center 1x1, 2 high
        for (int y = cy + 1; y <= cy + 2; y++) {
            Location loc = new Location(world, cx, y, cz);
            world.getBlockAt(loc).setType(Material.GOLD_BLOCK);
            podiumBlocks.add(loc);
        }

        // Top platform - diamond block
        Location topLoc = new Location(world, cx, cy + 3, cz);
        world.getBlockAt(topLoc).setType(Material.DIAMOND_BLOCK);
        podiumBlocks.add(topLoc);

        // Add beacon effect (sea lanterns around base for glow)
        int[][] glowPositions = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] pos : glowPositions) {
            Location loc = new Location(world, cx + pos[0], cy, cz + pos[1]);
            world.getBlockAt(loc).setType(Material.SEA_LANTERN);
            podiumBlocks.add(loc);
        }

        // Add barrier cage around the celebration area (tall for fireworks)
        buildBarrierCage(center, cageRadius, cageHeight);

        // Add small cage around winner position (on top of podium)
        buildWinnerCage(center);
    }

    /**
     * Build a small barrier cage around the winner on top of the podium.
     */
    private void buildWinnerCage(Location podiumCenter) {
        World world = podiumCenter.getWorld();
        int cx = podiumCenter.getBlockX();
        int cy = podiumCenter.getBlockY() + 4; // Winner stands at y+4
        int cz = podiumCenter.getBlockZ();

        // Build a 3x3 barrier cage around the winner (walls and ceiling, no floor to not block diamond block)
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                for (int y = cy; y <= cy + 2; y++) {
                    // Only build walls (edges) and ceiling
                    boolean isEdge = (x == cx - 1 || x == cx + 1 || z == cz - 1 || z == cz + 1);
                    boolean isCeiling = (y == cy + 2);

                    // Skip the interior (where winner stands)
                    boolean isInterior = (x == cx && z == cz);

                    if ((isEdge || isCeiling) && !isInterior) {
                        Location loc = new Location(world, x, y, z);
                        world.getBlockAt(loc).setType(Material.BARRIER);
                        podiumBlocks.add(loc);
                    }
                }
            }
        }
    }

    /**
     * Build invisible barrier cage around an area.
     * @param center Center location
     * @param radius Radius of the cage
     * @param height Height of the cage
     */
    private void buildBarrierCage(Location center, int radius, int height) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Build floor, walls, and ceiling
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                boolean isEdge = (x == cx - radius || x == cx + radius || z == cz - radius || z == cz + radius);

                // Floor (full floor, below player feet)
                Location floorLoc = new Location(world, x, cy - 1, z);
                world.getBlockAt(floorLoc).setType(Material.BARRIER);
                podiumBlocks.add(floorLoc);

                // Walls (only on edges)
                if (isEdge) {
                    for (int y = cy; y <= cy + height; y++) {
                        Location loc = new Location(world, x, y, z);
                        world.getBlockAt(loc).setType(Material.BARRIER);
                        podiumBlocks.add(loc);
                    }
                }

                // Ceiling
                Location ceilingLoc = new Location(world, x, cy + height, z);
                world.getBlockAt(ceilingLoc).setType(Material.BARRIER);
                podiumBlocks.add(ceilingLoc);
            }
        }
    }

    /**
     * Teleport spectators around the podium in a circle.
     */
    private void teleportSpectatorsAroundPodium(Location center, List<Player> spectators) {
        int distance = 5;

        for (int i = 0; i < spectators.size(); i++) {
            double angle = (2 * Math.PI * i) / Math.max(spectators.size(), 1);
            int x = center.getBlockX() + (int) Math.round(distance * Math.cos(angle));
            int z = center.getBlockZ() + (int) Math.round(distance * Math.sin(angle));

            Location spectatorPos = new Location(center.getWorld(), x + 0.5, center.getY(), z + 0.5);

            // Look at podium center
            double dx = center.getX() - spectatorPos.getX();
            double dz = center.getZ() - spectatorPos.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            spectatorPos.setYaw(yaw);
            spectatorPos.setPitch(10); // Look slightly up

            Player spectator = spectators.get(i);
            spectator.teleport(spectatorPos);
            spectator.setGameMode(GameMode.ADVENTURE);
        }
    }

    /**
     * Start spawning fireworks around the podium.
     */
    private void startFireworks(Location center, List<Player> players) {
        if (fireworkTask != null) {
            fireworkTask.cancel();
        }

        Random random = new Random();

        fireworkTask = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 20) { // 20 fireworks total
                    cancel();
                    return;
                }

                // Random position around podium
                double angle = random.nextDouble() * 2 * Math.PI;
                int distance = 3 + random.nextInt(5);
                int x = center.getBlockX() + (int) (distance * Math.cos(angle));
                int z = center.getBlockZ() + (int) (distance * Math.sin(angle));
                Location fireworkLoc = new Location(center.getWorld(), x + 0.5, center.getY() + 1, z + 0.5);

                spawnFirework(fireworkLoc);
                count++;
            }
        };

        fireworkTask.runTaskTimer(plugin, 0L, 10L); // Every half second
    }

    /**
     * Spawn a single firework.
     */
    private void spawnFirework(Location location) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();

        Random random = new Random();
        Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE, Color.WHITE};
        Color color1 = colors[random.nextInt(colors.length)];
        Color color2 = colors[random.nextInt(colors.length)];

        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        FireworkEffect.Type type = types[random.nextInt(types.length)];

        FireworkEffect effect = FireworkEffect.builder()
            .with(type)
            .withColor(color1)
            .withFade(color2)
            .flicker(random.nextBoolean())
            .trail(random.nextBoolean())
            .build();

        meta.addEffect(effect);
        meta.setPower(1 + random.nextInt(2));
        firework.setFireworkMeta(meta);
    }

    /**
     * Start countdown to next round.
     */
    private void startNextRoundCountdown(List<Player> players, int totalSeconds, Runnable onComplete) {
        new BukkitRunnable() {
            int remaining = totalSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    // Show "Starting new round" title
                    Title startTitle = Title.title(
                        Component.text("New Round Starting!").color(NamedTextColor.GREEN),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
                    );

                    for (Player player : players) {
                        if (player.isOnline()) {
                            player.showTitle(startTitle);
                        }
                    }

                    // Cleanup podium
                    cleanup();

                    onComplete.run();
                    cancel();
                    return;
                }

                // Show countdown in action bar every 5 seconds or last 5 seconds
                if (remaining % 5 == 0 || remaining <= 5) {
                    Component actionBar = Component.text("Next round in: ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(remaining + "s").color(NamedTextColor.YELLOW));

                    for (Player player : players) {
                        if (player.isOnline()) {
                            player.sendActionBar(actionBar);
                        }
                    }
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Cleanup podium blocks.
     */
    public void cleanup() {
        if (fireworkTask != null) {
            fireworkTask.cancel();
            fireworkTask = null;
        }

        for (Location loc : podiumBlocks) {
            if (loc.getWorld() != null) {
                loc.getWorld().getBlockAt(loc).setType(Material.AIR);
            }
        }
        podiumBlocks.clear();
    }
}
