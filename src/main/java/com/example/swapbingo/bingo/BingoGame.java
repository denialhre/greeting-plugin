package com.example.swapbingo.bingo;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.gui.BingoCardGUI;
import com.example.swapbingo.bingo.pool.TaskPool;
import com.example.swapbingo.bingo.pool.TaskPoolLoader;
import com.example.swapbingo.bingo.tasks.TaskDefinition;
import com.example.swapbingo.bingo.tasks.TaskType;
import net.kyori.adventure.text.format.TextDecoration;
import com.example.swapbingo.bingo.tracking.*;
import com.example.swapbingo.core.Difficulty;
import com.example.swapbingo.core.GameState;
import com.example.swapbingo.core.PlayerSession;
import com.example.swapbingo.util.MessageUtil;
import com.example.swapbingo.world.SpawnCageManager;
import com.example.swapbingo.world.WinCelebrationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

import java.util.*;

/**
 * Manages the Bingo game with integrated Death Swap mechanic.
 */
public class BingoGame {

    private final SwapBingoPlugin plugin;
    private final Set<UUID> participants;
    private final TaskPoolLoader poolLoader;
    private final BingoCardGUI bingoCardGUI;
    private final WinCelebrationManager winCelebrationManager;

    private TaskPool taskPool;
    private WinPattern currentPattern;

    // Event trackers
    private ItemCollectTracker itemTracker;
    private EntityKillTracker entityTracker;
    private BiomeVisitTracker biomeTracker;
    private StructureDiscoverTracker structureTracker;

    // Swap mechanic
    private SwapTimerTask swapTask;

    // Grace period state
    private boolean inGracePeriod = false;
    private BukkitRunnable gracePeriodTask;

    // Surprise swap countdown task (tracked to prevent orphaned tasks)
    private BukkitRunnable surpriseSwapTask;

    // Location tracking
    private final LocationTrackerManager locationTrackerManager;

    // Selected difficulty for current game
    private Difficulty selectedDifficulty = Difficulty.MEDIUM;

    public BingoGame(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.participants = new HashSet<>();
        this.poolLoader = new TaskPoolLoader(plugin);
        this.bingoCardGUI = new BingoCardGUI(plugin);
        this.winCelebrationManager = new WinCelebrationManager(plugin);
        this.locationTrackerManager = new LocationTrackerManager(plugin);

        // Load task pool
        this.taskPool = poolLoader.load();
    }

    /**
     * Get the spawn cage manager from the plugin.
     */
    private com.example.swapbingo.world.SpawnCageManager getSpawnCageManager() {
        return plugin.getSpawnCageManager();
    }

    /**
     * Start a new bingo game.
     */
    public boolean start(List<Player> players) {
        var configManager = plugin.getConfigManager();

        if (!configManager.isBingoEnabled()) {
            return false;
        }

        int minPlayers = configManager.getBingoMinPlayers();
        if (players.size() < minPlayers) {
            return false;
        }

        // Clean up any leftover boss bars from previous games
        plugin.getBossBarManager().cleanup();

        // Reload task pool
        taskPool = poolLoader.load();

        // Determine win pattern
        String patternConfig = configManager.getWinPattern();
        currentPattern = WinPattern.fromString(patternConfig);

        participants.clear();
        var gameManager = plugin.getGameManager();

        // Show loading title to all players
        Title loadingTitle = Title.title(
            Component.text("Generating World...").color(NamedTextColor.GOLD),
            Component.text("Please wait").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(30), Duration.ofMillis(500))
        );
        for (Player player : players) {
            player.showTitle(loadingTitle);
        }

        // Always regenerate the bingo world for a fresh start
        plugin.getWorldManager().resetBingoWorld(players, () -> {
            World bingoWorld = plugin.getWorldManager().getOrCreateBingoWorld();

            // Reset all players fully
            for (Player player : players) {
                resetPlayer(player);
            }

            // Spawn players in barrier cages
            getSpawnCageManager().spawnPlayersInCages(bingoWorld, players, () -> {
                // Add players as participants and store original locations
                for (Player player : players) {
                    PlayerSession session = gameManager.getSession(player);
                    session.setOriginalLocation(player.getLocation());
                    participants.add(player.getUniqueId());
                }

                // Check if difficulty voting is enabled
                if (configManager.isDifficultyVoteEnabled()) {
                    // Start difficulty vote phase
                    plugin.getDifficultyVoteManager().startVote(players, () -> {
                        // After difficulty vote completes, get selected difficulty and continue
                        selectedDifficulty = plugin.getDifficultyVoteManager().getSelectedDifficulty();
                        continueAfterDifficultyVote(players);
                    });
                } else {
                    // No difficulty vote, use default difficulty
                    selectedDifficulty = Difficulty.fromString(configManager.getDefaultDifficulty());
                    continueAfterDifficultyVote(players);
                }
            });
        });

        return true;
    }

    /**
     * Continue game setup after difficulty vote completes.
     * Generates bingo cards and starts countdown phase.
     */
    private void continueAfterDifficultyVote(List<Player> players) {
        var configManager = plugin.getConfigManager();
        var gameManager = plugin.getGameManager();

        // Generate shared card with selected difficulty
        BingoCard sharedCard = null;
        if (configManager.useSharedBingoCard()) {
            sharedCard = generateCard(selectedDifficulty);
        }

        // Set up bingo cards for all players
        for (Player player : players) {
            if (!player.isOnline()) continue;

            PlayerSession session = gameManager.getSessionIfExists(player.getUniqueId());
            if (session == null) continue;

            // Use shared card (cloned) or generate individual card
            BingoCard card;
            if (sharedCard != null) {
                card = sharedCard.clone();
            } else {
                card = generateCard(selectedDifficulty);
            }
            session.setBingoCard(card);

            // Give the bingo card item so players can view during countdown
            BingoCardItem.give(player);
        }

        // Notify players they can view their card during countdown
        String patternName = currentPattern == WinPattern.ROW ? "row" : "column";
        for (Player player : players) {
            if (player.isOnline()) {
                MessageUtil.send(player, "&7You can view your bingo card now!");
                MessageUtil.send(player, "&7Win by completing a &e" + patternName + "&7 of tasks.");
            }
        }

        // Start countdown and release from cages
        getSpawnCageManager().startCountdownAndRelease(players, () -> {
            // Game officially starts here
            gameManager.setBingoState(GameState.RUNNING);

            // Register trackers
            registerTrackers();

            // Announce game start
            String pattern = currentPattern == WinPattern.ROW ? "row" : "column";
            MessageUtil.broadcast("&aBingo game started! Complete a &e" + pattern + "&a to win!");
            MessageUtil.broadcast("&7Difficulty: " +
                (selectedDifficulty == Difficulty.EASY ? "&aEasy" :
                 selectedDifficulty == Difficulty.MEDIUM ? "&eMedium" : "&cHard"));

            // Notify players about bingo card
            String msg = configManager.getBingoMessage("card-given");
            for (Player player : players) {
                if (player.isOnline()) {
                    MessageUtil.send(player, msg);
                }
            }

            // Handle grace period or start swaps immediately
            if (configManager.isGameGracePeriodEnabled() && configManager.isSwapEnabled()) {
                startGracePeriod(players);
            } else if (configManager.isSwapEnabled()) {
                // No grace period, start swaps immediately
                int interval = configManager.getSwapIntervalSeconds();
                MessageUtil.broadcast("&7Death Swap active! Players swap every &e" + interval + "&7 seconds.");
                startSwapTimer();
            }

            plugin.getLogger().info("Bingo started with " + players.size() + " players, pattern: " + currentPattern + ", difficulty: " + selectedDifficulty);
        });
    }

    /**
     * Reset a player to fresh state (health, hunger, inventory).
     */
    private void resetPlayer(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getEnderChest().clear();

        // Reset health
        var maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getBaseValue());
        } else {
            player.setHealth(20.0);
        }

        // Reset hunger
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);

        // Reset experience
        player.setExp(0);
        player.setLevel(0);

        // Clear potion effects
        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Set game mode
        player.setGameMode(GameMode.SURVIVAL);

        // Reset fire and fall distance
        player.setFireTicks(0);
        player.setFallDistance(0);
    }

    /**
     * Stop the bingo game.
     */
    public void stop() {
        // Cancel any active votes
        plugin.getRemakeVoteManager().cancelVote();
        plugin.getDifficultyVoteManager().cancelVote();

        // Cleanup boss bars
        plugin.getBossBarManager().cleanup();

        // Stop swap timer
        stopSwapTimer();

        // Cancel surprise swap countdown if running
        cancelSurpriseSwapCountdown();

        // Stop grace period timer
        if (gracePeriodTask != null) {
            gracePeriodTask.cancel();
            gracePeriodTask = null;
        }
        inGracePeriod = false;

        // Unregister trackers (this also clears their internal caches)
        unregisterTrackers();

        // Clear location tracker data to prevent memory leaks
        locationTrackerManager.clearAll();

        // Clear swap history and points data
        plugin.getPointsManager().clearAll();

        // Restore players and remove bingo card items
        var gameManager = plugin.getGameManager();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Remove bingo card item
                BingoCardItem.remove(player);

                PlayerSession session = gameManager.getSessionIfExists(uuid);
                if (session != null && session.getOriginalLocation() != null) {
                    player.teleport(session.getOriginalLocation());
                }
            }
            gameManager.removeSession(uuid);
        }

        participants.clear();
        gameManager.setBingoState(GameState.IDLE);

        plugin.getLogger().info("Bingo stopped");
    }

    /**
     * Start the grace period (no PvP, no swaps).
     * When grace period ends, first swap happens immediately, then timer continues.
     */
    private void startGracePeriod(List<Player> players) {
        int durationMinutes = plugin.getConfigManager().getGameGracePeriodMinutes();
        int durationSeconds = durationMinutes * 60;

        inGracePeriod = true;

        // Create and show grace period boss bar
        var bossBarManager = plugin.getBossBarManager();
        bossBarManager.createGracePeriodBar(durationSeconds);
        for (Player player : players) {
            bossBarManager.addPlayerToGracePeriodBar(player);
        }

        MessageUtil.broadcast("&e⚔ Grace Period Started! &7PvP disabled, no swaps for &e" + durationMinutes + " minutes&7.");

        // Show title to all players
        Title graceTitle = Title.title(
            Component.text("Grace Period").color(NamedTextColor.GREEN),
            Component.text(durationMinutes + " minutes - No PvP, No Swaps").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
        );
        for (Player player : players) {
            player.showTitle(graceTitle);
        }

        final int totalDuration = durationSeconds;
        gracePeriodTask = new BukkitRunnable() {
            int remaining = durationSeconds;

            @Override
            public void run() {
                if (!isRunning()) {
                    cancel();
                    return;
                }

                // Update boss bar every second
                plugin.getBossBarManager().updateGracePeriodBar(remaining, totalDuration);

                if (remaining <= 0) {
                    plugin.getBossBarManager().removeGracePeriodBar();
                    endGracePeriod();
                    cancel();
                    return;
                }

                // Show warnings at specific intervals
                if (remaining == 60 || remaining == 30 || remaining == 10 || remaining <= 5) {
                    String timeStr = remaining >= 60 ? (remaining / 60) + " minute" + (remaining >= 120 ? "s" : "") : remaining + " seconds";
                    if (remaining == 60) timeStr = "1 minute";

                    MessageUtil.broadcast("&e⚔ Grace period ends in &c" + timeStr + "&e!");

                    if (remaining <= 5) {
                        // Show title countdown
                        NamedTextColor color = switch (remaining) {
                            case 5, 4 -> NamedTextColor.YELLOW;
                            case 3, 2 -> NamedTextColor.GOLD;
                            case 1 -> NamedTextColor.RED;
                            default -> NamedTextColor.WHITE;
                        };

                        Title countdownTitle = Title.title(
                            Component.text(String.valueOf(remaining)).color(color),
                            Component.text("Grace period ending!").color(NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                        );

                        for (UUID uuid : participants) {
                            Player player = plugin.getServer().getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                player.showTitle(countdownTitle);
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                            }
                        }
                    }
                }

                remaining--;
            }
        };

        gracePeriodTask.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

    /**
     * End the grace period - enable PvP, do first swap, start timer.
     */
    private void endGracePeriod() {
        inGracePeriod = false;

        MessageUtil.broadcast("&c⚔ Grace Period Over! &7PvP enabled, swaps active!");

        // Show title
        Title endTitle = Title.title(
            Component.text("GRACE PERIOD OVER!").color(NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
            Component.text("PvP Enabled - First Swap NOW!").color(NamedTextColor.GOLD),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        );

        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(endTitle);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
            }
        }

        // Perform the first swap immediately
        performSwap();

        // Then start the normal swap timer
        int interval = plugin.getConfigManager().getSwapIntervalSeconds();
        MessageUtil.broadcast("&7Death Swap active! Next swap in &e" + interval + "&7 seconds.");
        startSwapTimer();
    }

    /**
     * Check if currently in grace period.
     */
    public boolean isInGracePeriod() {
        return inGracePeriod;
    }

    /**
     * Start the swap timer.
     */
    private void startSwapTimer() {
        swapTask = new SwapTimerTask();
        swapTask.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

    /**
     * Stop the swap timer and remove its boss bar.
     */
    private void stopSwapTimer() {
        if (swapTask != null) {
            swapTask.cancel();
            swapTask = null;
        }
        // Always remove the swap timer boss bar when stopping
        plugin.getBossBarManager().removeSwapTimerBar();
    }

    /**
     * Perform a rotation swap where everyone moves to someone else's location.
     * Example with 3 players: 1->2, 2->3, 3->1 (everyone moves, no one stays)
     */
    public void performSwap() {
        List<Player> activePlayers = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                activePlayers.add(player);
            }
        }

        if (activePlayers.size() < 2) {
            return;
        }

        // Shuffle players for random ordering
        Collections.shuffle(activePlayers);

        // Store all locations before teleporting
        List<org.bukkit.Location> locations = new ArrayList<>();
        for (Player player : activePlayers) {
            locations.add(player.getLocation().clone());
        }

        // Rotation swap: each player goes to the next player's location
        // Player 0 -> Location 1, Player 1 -> Location 2, ..., Last Player -> Location 0
        List<String> swapMessages = new ArrayList<>();

        for (int i = 0; i < activePlayers.size(); i++) {
            Player player = activePlayers.get(i);
            int targetIndex = (i + 1) % activePlayers.size(); // Next player's location (wraps around)
            org.bukkit.Location targetLoc = locations.get(targetIndex);
            Player targetPlayer = activePlayers.get(targetIndex);

            // Preserve player's yaw and pitch
            targetLoc.setYaw(player.getLocation().getYaw());
            targetLoc.setPitch(player.getLocation().getPitch());

            player.teleport(targetLoc);

            // Record swap partner for trap kill attribution:
            // The player who was at this location (targetPlayer) is the "trap setter"
            // If the teleported player dies within the time window, targetPlayer gets credited
            plugin.getPointsManager().recordSwapPartner(player.getUniqueId(), targetPlayer.getUniqueId());

            // Build swap message (who they're going to)
            swapMessages.add("&e" + player.getName() + " &7-> &e" + targetPlayer.getName() + "'s location");
        }

        // Broadcast swap announcement header
        MessageUtil.broadcast("");
        MessageUtil.broadcast("&c&l⚠ SWAP! &7Everyone has been teleported!");
        MessageUtil.broadcast("&8&m                                        ");

        // Broadcast individual swap details
        for (String msg : swapMessages) {
            MessageUtil.broadcast(msg);
        }

        MessageUtil.broadcast("&8&m                                        ");
        MessageUtil.broadcast("");

        // Update all tracking compasses after swap
        for (Player player : activePlayers) {
            locationTrackerManager.updateTrackersAfterSwap(player);
        }
    }

    /**
     * Generate a bingo card with random tasks using default difficulty.
     */
    private BingoCard generateCard() {
        return generateCard(Difficulty.MEDIUM);
    }

    /**
     * Generate a bingo card with random tasks using specified difficulty.
     */
    private BingoCard generateCard(Difficulty difficulty) {
        var configManager = plugin.getConfigManager();
        int size = configManager.getCardSize();

        BingoCard card = new BingoCard(size, currentPattern);

        // Get task counts from config
        int itemCount = configManager.getTaskDistribution("items");
        int entityCount = configManager.getTaskDistribution("entities");
        int biomeCount = configManager.getTaskDistribution("biomes");
        int structureCount = configManager.getTaskDistribution("structures");

        // Collect all tasks with difficulty-adjusted weights
        List<TaskDefinition> allTasks = new ArrayList<>();
        allTasks.addAll(taskPool.getRandomTasks(TaskType.COLLECT_ITEM, itemCount, difficulty));
        allTasks.addAll(taskPool.getRandomTasks(TaskType.KILL_ENTITY, entityCount, difficulty));
        allTasks.addAll(taskPool.getRandomTasks(TaskType.VISIT_BIOME, biomeCount, difficulty));
        allTasks.addAll(taskPool.getRandomTasks(TaskType.DISCOVER_STRUCTURE, structureCount, difficulty));

        // Shuffle and assign to grid
        Collections.shuffle(allTasks);

        int index = 0;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (index < allTasks.size()) {
                    card.setTask(row, col, allTasks.get(index++));
                }
            }
        }

        return card;
    }

    /**
     * Register event trackers.
     */
    private void registerTrackers() {
        var pm = plugin.getServer().getPluginManager();

        itemTracker = new ItemCollectTracker(plugin, this);
        entityTracker = new EntityKillTracker(plugin, this);
        biomeTracker = new BiomeVisitTracker(plugin, this);
        structureTracker = new StructureDiscoverTracker(plugin, this);

        pm.registerEvents(itemTracker, plugin);
        pm.registerEvents(entityTracker, plugin);
        pm.registerEvents(biomeTracker, plugin);
        pm.registerEvents(structureTracker, plugin);
    }

    /**
     * Unregister event trackers and clean up their cached data.
     */
    private void unregisterTrackers() {
        if (itemTracker != null) {
            org.bukkit.event.HandlerList.unregisterAll(itemTracker);
            itemTracker = null;
        }
        if (entityTracker != null) {
            org.bukkit.event.HandlerList.unregisterAll(entityTracker);
            entityTracker = null;
        }
        if (biomeTracker != null) {
            org.bukkit.event.HandlerList.unregisterAll(biomeTracker);
            biomeTracker.cleanup(); // Clear cached chunk coordinates
            biomeTracker = null;
        }
        if (structureTracker != null) {
            org.bukkit.event.HandlerList.unregisterAll(structureTracker);
            structureTracker.cleanup(); // Clear cached locations and timestamps
            structureTracker = null;
        }
    }

    /**
     * Handle task completion for a player.
     */
    public void handleTaskCompletion(Player player, TaskType type, String target) {
        if (!isRunning() || !isParticipant(player.getUniqueId())) {
            return;
        }

        var gameManager = plugin.getGameManager();
        PlayerSession session = gameManager.getSessionIfExists(player.getUniqueId());
        if (session == null || session.getBingoCard() == null) {
            return;
        }

        BingoCard card = session.getBingoCard();
        BingoTask completedTask = card.tryComplete(type, target);

        if (completedTask != null) {
            // Award points for task completion
            plugin.getPointsManager().awardTaskCompletion(player.getUniqueId());
            int points = plugin.getConfigManager().getPointsTaskCompletion();

            // Send personal completion message with points
            String msg = plugin.getConfigManager().getBingoMessage("task-complete");
            msg = MessageUtil.replacePlaceholders(msg, Map.of(
                "task", completedTask.getDescription()
            ));
            MessageUtil.send(player, msg + " &7(+" + points + " point" + (points > 1 ? "s" : "") + ")");

            // Calculate tasks to win for threat warning
            int tasksToWin = calculateTasksToWin(card);

            // Universal broadcast with threat warning
            broadcastTaskCompletion(player, completedTask.getDescription(), tasksToWin);

            // Check win condition
            if (card.checkWin()) {
                declareWinner(player);
            }
        }
    }

    /**
     * Broadcast task completion to all players with threat warnings.
     */
    private void broadcastTaskCompletion(Player completingPlayer, String taskDescription, int tasksToWin) {
        Component message = Component.text(completingPlayer.getName())
            .color(NamedTextColor.YELLOW)
            .append(Component.text(" completed: ").color(NamedTextColor.GRAY))
            .append(Component.text(taskDescription).color(NamedTextColor.WHITE));

        // Add threat warning if close to winning
        if (tasksToWin <= 2 && tasksToWin > 0) {
            NamedTextColor warningColor = tasksToWin == 1 ? NamedTextColor.RED : NamedTextColor.GOLD;
            net.kyori.adventure.text.format.TextDecoration bold = net.kyori.adventure.text.format.TextDecoration.BOLD;

            message = message.append(Component.text(" [" + tasksToWin + " TO WIN!]")
                .color(warningColor)
                .decorate(bold));

            // Play warning sound to other players
            for (UUID uuid : participants) {
                if (!uuid.equals(completingPlayer.getUniqueId())) {
                    Player other = plugin.getServer().getPlayer(uuid);
                    if (other != null && other.isOnline()) {
                        Sound warningSound = tasksToWin == 1 ? Sound.ENTITY_WITHER_SPAWN : Sound.BLOCK_NOTE_BLOCK_BELL;
                        other.playSound(other.getLocation(), warningSound, 1.0f, tasksToWin == 1 ? 0.5f : 1.0f);
                    }
                }
            }
        }

        // Broadcast to all players
        for (UUID uuid : participants) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    /**
     * Calculate tasks remaining to win for a card.
     */
    public int calculateTasksToWin(BingoCard card) {
        int size = card.getSize();
        int minRemaining = size;

        if (card.getRequiredPattern() == WinPattern.ROW) {
            for (int row = 0; row < size; row++) {
                int incomplete = 0;
                for (int col = 0; col < size; col++) {
                    BingoTask task = card.getTask(row, col);
                    if (task != null && !task.isCompleted()) {
                        incomplete++;
                    }
                }
                minRemaining = Math.min(minRemaining, incomplete);
            }
        } else {
            for (int col = 0; col < size; col++) {
                int incomplete = 0;
                for (int row = 0; row < size; row++) {
                    BingoTask task = card.getTask(row, col);
                    if (task != null && !task.isCompleted()) {
                        incomplete++;
                    }
                }
                minRemaining = Math.min(minRemaining, incomplete);
            }
        }

        return minRemaining;
    }

    /**
     * Declare the winner and end the game.
     */
    private void declareWinner(Player winner) {
        // Cancel any active remake vote
        plugin.getRemakeVoteManager().cancelVote();

        // Stop swap timer immediately
        stopSwapTimer();

        // Track win
        plugin.getWinTracker().addWin(winner.getUniqueId(), "bingo");

        // Unregister trackers
        unregisterTrackers();

        // Remove bingo card items from all players
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                BingoCardItem.remove(player);
            }
        }

        // Get all current players
        List<Player> allPlayers = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                allPlayers.add(player);
            }
        }

        // Start win celebration with podium and fireworks
        if (plugin.getConfigManager().shouldResetWorldOnWin()) {
            winCelebrationManager.celebrate(winner, allPlayers, () -> {
                // After celebration countdown, start new round
                resetAndStartNewRound();
            });
        } else {
            // Just show basic win message and stop
            String patternName = currentPattern == WinPattern.ROW ? "row" : "column";
            String msg = plugin.getConfigManager().getBingoMessage("bingo");
            msg = MessageUtil.replacePlaceholders(msg, Map.of(
                "player", winner.getName(),
                "pattern", patternName
            ));
            MessageUtil.broadcast(msg);
            stop();
        }
    }

    /**
     * Reset the world and start a new round.
     */
    private void resetAndStartNewRound() {
        var gameManager = plugin.getGameManager();
        var worldManager = plugin.getWorldManager();

        // Get current players before clearing
        List<Player> currentPlayers = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                currentPlayers.add(player);
            }
            gameManager.removeSession(uuid);
        }

        participants.clear();
        gameManager.setBingoState(GameState.IDLE);

        if (currentPlayers.isEmpty()) {
            plugin.getLogger().info("No players remaining for new round");
            return;
        }

        // Reset the world and start new round when ready
        worldManager.resetBingoWorld(currentPlayers, () -> {
            // Check if players are still online
            List<Player> readyPlayers = currentPlayers.stream()
                .filter(Player::isOnline)
                .toList();

            if (readyPlayers.size() >= plugin.getConfigManager().getBingoMinPlayers()) {
                // Delay a bit for world to fully initialize
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    MessageUtil.broadcast("&aNew round starting!");
                    start(new ArrayList<>(readyPlayers));
                }, 40L); // 2 seconds after teleport
            } else {
                MessageUtil.broadcast("&cNot enough players for a new round.");
            }
        });
    }

    /**
     * Get a player's bingo card.
     */
    public BingoCard getCard(Player player) {
        var session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
        return session != null ? session.getBingoCard() : null;
    }

    /**
     * Check if the game is running.
     */
    public boolean isRunning() {
        return plugin.getGameManager().getBingoState() == GameState.RUNNING;
    }

    /**
     * Check if a player is participating.
     */
    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public WinPattern getCurrentPattern() {
        return currentPattern;
    }

    public TaskPool getTaskPool() {
        return taskPool;
    }

    public TaskPoolLoader getPoolLoader() {
        return poolLoader;
    }

    public BingoCardGUI getBingoCardGUI() {
        return bingoCardGUI;
    }

    public LocationTrackerManager getLocationTrackerManager() {
        return locationTrackerManager;
    }

    /**
     * Get the seconds until the next scheduled swap.
     * Returns -1 if no swap timer is running.
     */
    public int getSecondsUntilNextSwap() {
        if (swapTask == null) {
            return -1;
        }
        return swapTask.getTimeRemaining();
    }

    /**
     * Re-roll a task at the specified position.
     * This changes the task for ALL players with shared cards.
     */
    public void rerollTask(Player initiator, int row, int col) {
        var gameManager = plugin.getGameManager();

        // Get the old task type to generate a replacement of the same type
        PlayerSession initiatorSession = gameManager.getSessionIfExists(initiator.getUniqueId());
        if (initiatorSession == null || initiatorSession.getBingoCard() == null) {
            return;
        }

        BingoTask oldTask = initiatorSession.getBingoCard().getTask(row, col);
        if (oldTask == null) {
            return;
        }

        // Generate a new random task of the same type
        TaskType taskType = oldTask.getType();
        List<TaskDefinition> availableTasks = taskPool.getRandomTasks(taskType, 10);

        // Find a task that doesn't already exist on the card
        TaskDefinition newTask = null;
        for (TaskDefinition task : availableTasks) {
            if (!isTaskOnAnyCard(task)) {
                newTask = task;
                break;
            }
        }

        if (newTask == null && !availableTasks.isEmpty()) {
            newTask = availableTasks.get(0); // Fallback to first available
        }

        if (newTask == null) {
            initiator.sendMessage(Component.text("Could not find a replacement task!")
                .color(NamedTextColor.RED));
            return;
        }

        // Update all players' cards
        String oldTaskName = oldTask.getDisplayName();
        String newTaskName = newTask.getDisplayName();

        for (UUID uuid : participants) {
            PlayerSession session = gameManager.getSessionIfExists(uuid);
            if (session != null && session.getBingoCard() != null) {
                session.getBingoCard().resetTask(row, col, newTask);
            }
        }

        // Broadcast the re-roll
        MessageUtil.broadcast("&e" + initiator.getName() + " &7re-rolled a task!");
        MessageUtil.broadcast("&c" + oldTaskName + " &7-> &a" + newTaskName);

        // Play sound to all players
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Check if a task definition already exists on any player's card.
     */
    private boolean isTaskOnAnyCard(TaskDefinition task) {
        var gameManager = plugin.getGameManager();
        for (UUID uuid : participants) {
            PlayerSession session = gameManager.getSessionIfExists(uuid);
            if (session != null && session.getBingoCard() != null) {
                for (BingoTask existingTask : session.getBingoCard().getAllTasks()) {
                    if (existingTask.getTarget().equals(task.getTarget()) &&
                        existingTask.getType() == task.getType()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Cancel any running surprise swap countdown.
     */
    private void cancelSurpriseSwapCountdown() {
        if (surpriseSwapTask != null) {
            surpriseSwapTask.cancel();
            surpriseSwapTask = null;
        }
    }

    /**
     * Trigger a surprise swap initiated by a player.
     */
    public void triggerSurpriseSwap(Player initiator) {
        if (!isRunning()) {
            return;
        }

        // Cancel any existing surprise swap countdown
        cancelSurpriseSwapCountdown();

        int warningSeconds = plugin.getConfigManager().getSurpriseSwapWarningSeconds();

        // Broadcast warning
        MessageUtil.broadcast("&c&l⚠ SURPRISE SWAP! &e" + initiator.getName() +
            " &7triggered an early swap!");
        MessageUtil.broadcast("&7Swapping in &e" + warningSeconds + " seconds&7!");

        // Show title and play sound to all
        Title warningTitle = Title.title(
            Component.text("SURPRISE SWAP!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
            Component.text(initiator.getName() + " triggered a swap!").color(NamedTextColor.GOLD),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
        );

        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(warningTitle);
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
            }
        }

        // Schedule the swap after warning countdown (store reference to prevent orphaned tasks)
        surpriseSwapTask = new BukkitRunnable() {
            int countdown = warningSeconds;

            @Override
            public void run() {
                if (!isRunning()) {
                    surpriseSwapTask = null;
                    cancel();
                    return;
                }

                if (countdown <= 0) {
                    // Perform the swap
                    showSwapTitle();
                    performSwap();

                    // Reset the normal swap timer
                    if (swapTask != null) {
                        swapTask.resetTimer();
                    }

                    surpriseSwapTask = null;
                    cancel();
                    return;
                }

                // Show countdown
                if (countdown <= 5) {
                    NamedTextColor color = switch (countdown) {
                        case 5, 4 -> NamedTextColor.YELLOW;
                        case 3, 2 -> NamedTextColor.GOLD;
                        case 1 -> NamedTextColor.RED;
                        default -> NamedTextColor.WHITE;
                    };

                    Title countdownTitle = Title.title(
                        Component.text(String.valueOf(countdown)).color(color),
                        Component.text("Surprise Swap!").color(NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                    );

                    for (UUID uuid : participants) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.showTitle(countdownTitle);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                        }
                    }
                }

                countdown--;
            }
        };
        surpriseSwapTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Show the swap title to all participants.
     */
    private void showSwapTitle() {
        Title title = Title.title(
            Component.text("SWAP!").color(NamedTextColor.RED),
            Component.text("Locations exchanged!").color(NamedTextColor.YELLOW),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
        );

        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Inner class for swap timer task.
     */
    private class SwapTimerTask extends BukkitRunnable {
        private final List<Integer> warningTimes;
        private final int swapInterval;
        private int timeRemaining;

        public SwapTimerTask() {
            this.warningTimes = plugin.getConfigManager().getCountdownWarnings();
            this.swapInterval = plugin.getConfigManager().getSwapIntervalSeconds();
            this.timeRemaining = swapInterval;

            // Create swap timer boss bar
            var bossBarManager = plugin.getBossBarManager();
            bossBarManager.createSwapTimerBar(swapInterval);
            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    bossBarManager.addPlayerToSwapTimerBar(player);
                }
            }
        }

        @Override
        public void run() {
            if (!isRunning()) {
                cancel();
                return;
            }

            // Update boss bar every second
            plugin.getBossBarManager().updateSwapTimerBar(timeRemaining, swapInterval);

            // Show title countdown for last 5 seconds
            if (timeRemaining <= 5 && timeRemaining > 0) {
                showSwapCountdownTitle(timeRemaining);
            }

            // Check if we need to broadcast a warning (for chat, larger numbers)
            if (warningTimes.contains(timeRemaining) && timeRemaining > 5) {
                String msg = plugin.getConfigManager().getSwapMessage("swap-warning");
                if (msg.isEmpty()) {
                    msg = "&c⚠ Swap in &e" + timeRemaining + "&c seconds!";
                } else {
                    msg = MessageUtil.replacePlaceholders(msg, Map.of(
                        "time", String.valueOf(timeRemaining)
                    ));
                }
                MessageUtil.broadcast(msg);
            }

            // Time to swap?
            if (timeRemaining <= 0) {
                // Show SWAP title
                showSwapTitle();
                performSwap();
                // Reset timer and boss bar
                timeRemaining = swapInterval;
                plugin.getBossBarManager().updateSwapTimerBar(timeRemaining, swapInterval);
            } else {
                timeRemaining--;
            }
        }

        private void showSwapCountdownTitle(int seconds) {
            NamedTextColor color = switch (seconds) {
                case 5, 4 -> NamedTextColor.YELLOW;
                case 3, 2 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.RED;
                default -> NamedTextColor.WHITE;
            };

            Title title = Title.title(
                Component.text(String.valueOf(seconds)).color(color),
                Component.text("Swap incoming!").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
            );

            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.showTitle(title);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                }
            }
        }

        private void showSwapTitle() {
            Title title = Title.title(
                Component.text("SWAP!").color(NamedTextColor.RED),
                Component.text("Locations exchanged!").color(NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
            );

            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.showTitle(title);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
            }
        }

        public int getTimeRemaining() {
            return timeRemaining;
        }

        public void resetTimer() {
            this.timeRemaining = swapInterval;
        }
    }
}
