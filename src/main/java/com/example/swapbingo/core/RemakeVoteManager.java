package com.example.swapbingo.core;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the remake voting system for restarting games democratically.
 */
public class RemakeVoteManager {

    private final SwapBingoPlugin plugin;

    // Vote tracking
    private boolean voteInProgress = false;
    private final Set<UUID> yesVotes = new HashSet<>();
    private final Set<UUID> noVotes = new HashSet<>();
    private long lastVoteEndTime = 0;
    private BukkitRunnable voteTask;
    private UUID voteInitiator;

    public RemakeVoteManager(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a remake vote.
     * @param initiator The player starting the vote
     * @return true if vote started successfully
     */
    public boolean startVote(Player initiator) {
        if (!plugin.getConfigManager().isRemakeEnabled()) {
            initiator.sendMessage(Component.text("Remake voting is disabled.")
                .color(NamedTextColor.RED));
            return false;
        }

        // Allow remake during running game OR during cage phase (after difficulty vote, before cages drop)
        boolean gameActive = plugin.getBingoGame().isRunning() ||
                             plugin.getSpawnCageManager().isInCagePhase();
        if (!gameActive) {
            initiator.sendMessage(Component.text("No game is currently active.")
                .color(NamedTextColor.RED));
            return false;
        }

        if (voteInProgress) {
            initiator.sendMessage(Component.text("A vote is already in progress!")
                .color(NamedTextColor.RED));
            return false;
        }

        // Check cooldown
        long cooldownMillis = plugin.getConfigManager().getRemakeCooldownMinutes() * 60 * 1000L;
        long timeSinceLastVote = System.currentTimeMillis() - lastVoteEndTime;

        if (timeSinceLastVote < cooldownMillis) {
            long remainingSeconds = (cooldownMillis - timeSinceLastVote) / 1000;
            initiator.sendMessage(Component.text("Vote on cooldown! Wait " +
                MessageUtil.formatTime((int) remainingSeconds) + " before starting another vote.")
                .color(NamedTextColor.RED));
            return false;
        }

        // Start the vote
        voteInProgress = true;
        voteInitiator = initiator.getUniqueId();
        yesVotes.clear();
        noVotes.clear();

        // Initiator automatically votes yes
        yesVotes.add(initiator.getUniqueId());

        int duration = plugin.getConfigManager().getRemakeVoteDuration();

        // Broadcast vote started
        broadcastVoteStarted(initiator, duration);

        // Start countdown
        voteTask = new BukkitRunnable() {
            int remaining = duration;

            @Override
            public void run() {
                if (!voteInProgress) {
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    endVote();
                    cancel();
                    return;
                }

                // Show warnings at specific times
                if (remaining == 10 || remaining == 5) {
                    MessageUtil.broadcast("&eRemake vote ends in &c" + remaining + " seconds&e!");
                }

                remaining--;
            }
        };

        voteTask.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    /**
     * Register a player's vote.
     */
    public void castVote(Player player, boolean voteYes) {
        if (!voteInProgress) {
            player.sendMessage(Component.text("No vote is currently in progress.")
                .color(NamedTextColor.RED));
            return;
        }

        Set<UUID> participants = getCurrentParticipants();
        if (!participants.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("Only game participants can vote.")
                .color(NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();

        // Check if already voted
        if (yesVotes.contains(uuid) || noVotes.contains(uuid)) {
            player.sendMessage(Component.text("You have already voted!")
                .color(NamedTextColor.YELLOW));
            return;
        }

        if (voteYes) {
            yesVotes.add(uuid);
            MessageUtil.broadcast("&a" + player.getName() + " &7voted &aYES&7 for remake. " +
                getCurrentVoteStatus());
        } else {
            noVotes.add(uuid);
            MessageUtil.broadcast("&c" + player.getName() + " &7voted &cNO&7 for remake. " +
                getCurrentVoteStatus());
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        // Check for early majority
        checkEarlyResult();
    }

    /**
     * Get current vote status string.
     */
    private String getCurrentVoteStatus() {
        int total = getCurrentParticipants().size();
        return "(&a" + yesVotes.size() + "&7/&c" + noVotes.size() + "&7 of " + total + ")";
    }

    /**
     * Check if vote can end early due to majority.
     */
    private void checkEarlyResult() {
        int totalParticipants = getCurrentParticipants().size();
        int majority = (totalParticipants / 2) + 1;

        // Check if YES has majority
        if (yesVotes.size() >= majority) {
            endVote();
            return;
        }

        // Check if NO has majority
        if (noVotes.size() >= majority) {
            endVote();
        }
    }

    /**
     * End the current vote and process result.
     */
    private void endVote() {
        if (!voteInProgress) {
            return;
        }

        voteInProgress = false;

        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }

        int yesCount = yesVotes.size();
        int noCount = noVotes.size();
        int totalVotes = yesCount + noCount;
        Set<UUID> participants = getCurrentParticipants();
        int totalParticipants = participants.size();

        // Determine result - need majority (half + 1) of participants to approve
        int majority = (totalParticipants / 2) + 1;
        boolean remakeApproved = yesCount >= majority;

        if (remakeApproved) {
            // Remake approved - no cooldown set since game will restart
            // (new game starts fresh with no cooldown)
            lastVoteEndTime = 0;

            MessageUtil.broadcast("");
            MessageUtil.broadcast("&a&lREMAKE APPROVED!");
            MessageUtil.broadcast("&7Votes: &a" + yesCount + " YES &7/ &c" + noCount + " NO");
            MessageUtil.broadcast("&7Restarting game...");
            MessageUtil.broadcast("");

            // Show title
            Title approvedTitle = Title.title(
                Component.text("REMAKE APPROVED").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.text("Restarting game...").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
            );

            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.showTitle(approvedTitle);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }

            // Get current players and restart
            List<Player> currentPlayers = new ArrayList<>();
            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    currentPlayers.add(player);
                }
            }

            // Stop and restart after a short delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getBingoGame().stop();

                // Small delay before starting new game
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!currentPlayers.isEmpty()) {
                        plugin.getBingoGame().start(currentPlayers);
                    }
                }, 40L); // 2 second delay
            }, 60L); // 3 second delay

        } else {
            // Remake rejected - set cooldown timer
            lastVoteEndTime = System.currentTimeMillis();

            MessageUtil.broadcast("");
            MessageUtil.broadcast("&c&lREMAKE REJECTED");
            MessageUtil.broadcast("&7Votes: &a" + yesCount + " YES &7/ &c" + noCount + " NO");
            MessageUtil.broadcast("&7Game continues...");
            MessageUtil.broadcast("");

            // Show title
            Title rejectedTitle = Title.title(
                Component.text("REMAKE REJECTED").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Game continues").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
            );

            for (UUID uuid : participants) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.showTitle(rejectedTitle);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }

        // Clear votes
        yesVotes.clear();
        noVotes.clear();
        voteInitiator = null;
    }

    /**
     * Broadcast vote started with clickable messages.
     */
    private void broadcastVoteStarted(Player initiator, int duration) {
        MessageUtil.broadcast("");
        MessageUtil.broadcast("&e&lREMAKE VOTE STARTED!");
        MessageUtil.broadcast("&7Started by: &e" + initiator.getName());
        MessageUtil.broadcast("&7Vote duration: &e" + duration + " seconds");
        MessageUtil.broadcast("");

        // Create clickable vote buttons
        Component voteMessage = Component.text("Click to vote: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text("[YES]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/remake yes")))
            .append(Component.text(" "))
            .append(Component.text("[NO]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/remake no")));

        // Send to all participants
        Set<UUID> participants = getCurrentParticipants();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(voteMessage);
            }
        }

        MessageUtil.broadcast("");

        // Show title and play sound
        Title voteTitle = Title.title(
            Component.text("REMAKE VOTE!").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
            Component.text("Check chat to vote").color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
        );

        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(voteTitle);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Get current participants (from game if running, otherwise from cage players).
     */
    private Set<UUID> getCurrentParticipants() {
        if (plugin.getBingoGame().isRunning()) {
            return plugin.getBingoGame().getParticipants();
        } else {
            return plugin.getSpawnCageManager().getCurrentCagePlayers()
                .stream()
                .map(org.bukkit.entity.Player::getUniqueId)
                .collect(Collectors.toSet());
        }
    }

    /**
     * Check if a vote is currently in progress.
     */
    public boolean isVoteInProgress() {
        return voteInProgress;
    }

    /**
     * Cancel the current vote (for cleanup when game ends).
     */
    public void cancelVote() {
        if (voteInProgress) {
            voteInProgress = false;
            if (voteTask != null) {
                voteTask.cancel();
                voteTask = null;
            }
            yesVotes.clear();
            noVotes.clear();
            voteInitiator = null;
        }
    }
}
