package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.BingoTask;
import com.example.swapbingo.bingo.pool.TaskPoolLoader;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.example.swapbingo.core.PlayerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener to handle GUI interactions and prevent item theft.
 */
public class GUIListener implements Listener {

    private final SwapBingoPlugin plugin;
    private final RecipeGUI recipeGUI;
    private final ShopGUI shopGUI;
    private final ConfirmationGUI confirmationGUI;
    private final TaskSelectGUI taskSelectGUI;
    private final ViewOnlyBingoCardGUI viewOnlyGUI;

    // Slot to grid mapping: rowStarts = {11, 20, 29, 38, 47}
    private static final int[] ROW_STARTS = {11, 20, 29, 38, 47};

    // Progress head slots
    private static final int[] PROGRESS_SLOTS = {8, 17, 26, 35, 44};

    // Track recipe viewing state per player
    private final Map<UUID, RecipeViewState> recipeViewStates = new HashMap<>();

    // Track pending confirmations per player
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new HashMap<>();

    // Track whose card a player is viewing
    private final Map<UUID, UUID> viewingCards = new HashMap<>();

    /**
     * State for tracking recipe viewing.
     */
    private record RecipeViewState(String itemName, String displayName,
                                   List<TaskPoolLoader.Recipe> recipes, int currentIndex) {}

    /**
     * Types of actions that can be confirmed.
     */
    private enum ConfirmationType {
        TASK_REROLL,
        SURPRISE_SWAP,
        LOCATION_TRACK
    }

    /**
     * Pending confirmation data.
     */
    private record PendingConfirmation(ConfirmationType type, int row, int col, String targetName) {}

    public GUIListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.recipeGUI = new RecipeGUI(plugin);
        this.shopGUI = new ShopGUI(plugin);
        this.confirmationGUI = new ConfirmationGUI(plugin);
        this.taskSelectGUI = new TaskSelectGUI(plugin);
        this.viewOnlyGUI = new ViewOnlyBingoCardGUI(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());

        // Handle bingo card GUI
        if (title.equals(BingoCardGUI.GUI_TITLE)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleBingoCardClick(player, event.getRawSlot());
            }
            return;
        }

        // Handle recipe GUI
        if (title.startsWith(RecipeGUI.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleRecipeGuiClick(player, event.getRawSlot());
            }
            return;
        }

        // Handle shop GUI
        if (title.equals(ShopGUI.GUI_TITLE)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleShopClick(player, event.getRawSlot());
            }
            return;
        }

        // Handle confirmation GUI
        if (title.startsWith(ConfirmationGUI.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleConfirmationClick(player, event.getRawSlot());
            }
            return;
        }

        // Handle task select GUI
        if (title.equals(TaskSelectGUI.GUI_TITLE)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleTaskSelectClick(player, event.getRawSlot());
            }
            return;
        }

        // Handle view-only card GUI
        if (title.startsWith(ViewOnlyBingoCardGUI.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleViewOnlyClick(player, event.getRawSlot());
            }
        }
    }

    /**
     * Handle a click on the bingo card.
     */
    private void handleBingoCardClick(Player player, int slot) {
        // Check for shop button click (slot 53)
        if (slot == 53) {
            shopGUI.open(player);
            return;
        }

        // Check for player head clicks (view other player's card)
        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            if (slot == PROGRESS_SLOTS[i]) {
                handlePlayerHeadClick(player, i);
                return;
            }
        }

        // Convert slot to row/col for task grid
        int row = -1;
        int col = -1;

        for (int r = 0; r < ROW_STARTS.length; r++) {
            int rowStart = ROW_STARTS[r];
            if (slot >= rowStart && slot < rowStart + 5) {
                row = r;
                col = slot - rowStart;
                break;
            }
        }

        if (row == -1) {
            return; // Not a task slot
        }

        // Get player's bingo card
        PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
        if (session == null || session.getBingoCard() == null) {
            return;
        }

        BingoCard card = session.getBingoCard();
        BingoTask task = card.getTask(row, col);

        if (task == null) {
            return;
        }

        switch (task.getType()) {
            case COLLECT_ITEM -> {
                // Show recipe GUI
                String itemName = task.getTarget().toLowerCase();
                TaskPoolLoader poolLoader = plugin.getBingoGame().getPoolLoader();
                List<TaskPoolLoader.Recipe> recipes = poolLoader.getRecipes(itemName);

                if (recipes.isEmpty()) {
                    return; // No recipe - already shown in tooltip
                }

                recipeViewStates.put(player.getUniqueId(),
                    new RecipeViewState(itemName, task.getDisplayName(), recipes, 0));
                recipeGUI.open(player, itemName, task.getDisplayName(), recipes, 0);
            }
            case VISIT_BIOME, DISCOVER_STRUCTURE -> {
                // Purchase location tracker using points
                if (task.isCompleted()) {
                    player.sendMessage(Component.text("You already completed this task!")
                        .color(NamedTextColor.YELLOW));
                    return;
                }

                // Open confirmation for location tracking
                int cost = plugin.getPointsManager().getLocationTrackCost();
                pendingConfirmations.put(player.getUniqueId(),
                    new PendingConfirmation(ConfirmationType.LOCATION_TRACK, row, col, task.getTarget()));

                String typeStr = task.getType() == TaskType.VISIT_BIOME ? "biome" : "structure";
                confirmationGUI.open(player, "Track Location",
                    "Track the nearest " + typeStr + ":\n" + task.getDisplayName() +
                    "\n\nYou'll receive a compass pointing\nto the nearest location.",
                    cost);
            }
            default -> {
                // KILL_ENTITY - no action needed
            }
        }
    }

    /**
     * Handle click on a player head to view their card.
     */
    private void handlePlayerHeadClick(Player viewer, int headIndex) {
        // Get the sorted list of players by progress
        var game = plugin.getBingoGame();
        List<UUID> participants = game.getParticipants().stream().toList();

        // Build sorted progress list (same logic as BingoCardGUI)
        List<UUID> sortedPlayers = participants.stream()
            .filter(uuid -> {
                PlayerSession s = plugin.getGameManager().getSessionIfExists(uuid);
                return s != null && s.getBingoCard() != null;
            })
            .sorted((a, b) -> {
                PlayerSession sa = plugin.getGameManager().getSessionIfExists(a);
                PlayerSession sb = plugin.getGameManager().getSessionIfExists(b);
                int tasksA = game.calculateTasksToWin(sa.getBingoCard());
                int tasksB = game.calculateTasksToWin(sb.getBingoCard());
                return Integer.compare(tasksA, tasksB);
            })
            .toList();

        if (headIndex >= sortedPlayers.size()) {
            return;
        }

        UUID targetUuid = sortedPlayers.get(headIndex);

        // Don't open view for own card
        if (targetUuid.equals(viewer.getUniqueId())) {
            return;
        }

        PlayerSession targetSession = plugin.getGameManager().getSessionIfExists(targetUuid);
        if (targetSession == null || targetSession.getBingoCard() == null) {
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        viewingCards.put(viewer.getUniqueId(), targetUuid);
        viewOnlyGUI.open(viewer, targetName, targetSession.getBingoCard());
    }

    /**
     * Handle a click on the recipe GUI.
     */
    private void handleRecipeGuiClick(Player player, int slot) {
        RecipeViewState state = recipeViewStates.get(player.getUniqueId());

        switch (slot) {
            case 45 -> {
                // Back button - return to bingo card
                recipeViewStates.remove(player.getUniqueId());
                PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
                if (session != null && session.getBingoCard() != null) {
                    plugin.getBingoGame().getBingoCardGUI().open(player, session.getBingoCard());
                } else {
                    player.closeInventory();
                }
            }
            case 48 -> {
                // Previous recipe
                if (state != null && state.currentIndex() > 0) {
                    int newIndex = state.currentIndex() - 1;
                    recipeViewStates.put(player.getUniqueId(),
                        new RecipeViewState(state.itemName(), state.displayName(), state.recipes(), newIndex));
                    recipeGUI.open(player, state.itemName(), state.displayName(), state.recipes(), newIndex);
                }
            }
            case 50 -> {
                // Next recipe
                if (state != null && state.currentIndex() < state.recipes().size() - 1) {
                    int newIndex = state.currentIndex() + 1;
                    recipeViewStates.put(player.getUniqueId(),
                        new RecipeViewState(state.itemName(), state.displayName(), state.recipes(), newIndex));
                    recipeGUI.open(player, state.itemName(), state.displayName(), state.recipes(), newIndex);
                }
            }
        }
    }

    /**
     * Handle a click on the shop GUI.
     */
    private void handleShopClick(Player player, int slot) {
        switch (slot) {
            case ShopGUI.SLOT_BACK -> {
                // Back to bingo card
                PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
                if (session != null && session.getBingoCard() != null) {
                    plugin.getBingoGame().getBingoCardGUI().open(player, session.getBingoCard());
                } else {
                    player.closeInventory();
                }
            }
            case ShopGUI.SLOT_REROLL -> {
                // Re-roll task - open task select GUI
                int cost = plugin.getConfigManager().getPointsTaskReroll();
                if (!plugin.getPointsManager().hasEnoughPoints(player.getUniqueId(), cost)) {
                    player.sendMessage(Component.text("Not enough points!")
                        .color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
                if (session != null && session.getBingoCard() != null) {
                    taskSelectGUI.open(player, session.getBingoCard());
                }
            }
            case ShopGUI.SLOT_SURPRISE_SWAP -> {
                // Surprise swap - open confirmation
                int cost = plugin.getConfigManager().getPointsSurpriseSwap();
                if (!plugin.getPointsManager().hasEnoughPoints(player.getUniqueId(), cost)) {
                    player.sendMessage(Component.text("Not enough points!")
                        .color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Check if enough time before next swap
                int minTime = plugin.getConfigManager().getSurpriseSwapMinTimeBeforeSwap();
                int timeUntilSwap = plugin.getBingoGame().getSecondsUntilNextSwap();

                if (timeUntilSwap >= 0 && timeUntilSwap < minTime) {
                    player.sendMessage(Component.text("Too close to next scheduled swap! Wait " +
                        (minTime - timeUntilSwap) + " more seconds.")
                        .color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                pendingConfirmations.put(player.getUniqueId(),
                    new PendingConfirmation(ConfirmationType.SURPRISE_SWAP, -1, -1, null));

                int warningSeconds = plugin.getConfigManager().getSurpriseSwapWarningSeconds();
                confirmationGUI.open(player, "Surprise Swap",
                    "Trigger an immediate swap!\n\nAll players will be warned\n" + warningSeconds +
                    " seconds before the swap.\n\nCatch enemies off guard!",
                    cost);
            }
        }
    }

    /**
     * Handle a click on the confirmation GUI.
     */
    private void handleConfirmationClick(Player player, int slot) {
        PendingConfirmation pending = pendingConfirmations.get(player.getUniqueId());

        switch (slot) {
            case 11 -> {
                // Confirm button
                if (pending != null) {
                    executePendingConfirmation(player, pending);
                }
                pendingConfirmations.remove(player.getUniqueId());
            }
            case 15 -> {
                // Cancel button
                pendingConfirmations.remove(player.getUniqueId());
                shopGUI.open(player);
            }
        }
    }

    /**
     * Execute a confirmed action.
     */
    private void executePendingConfirmation(Player player, PendingConfirmation pending) {
        switch (pending.type()) {
            case TASK_REROLL -> {
                int cost = plugin.getConfigManager().getPointsTaskReroll();
                if (plugin.getPointsManager().removePoints(player.getUniqueId(), cost)) {
                    plugin.getBingoGame().rerollTask(player, pending.row(), pending.col());
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } else {
                    player.sendMessage(Component.text("Not enough points!")
                        .color(NamedTextColor.RED));
                }
                player.closeInventory();
            }
            case SURPRISE_SWAP -> {
                int cost = plugin.getConfigManager().getPointsSurpriseSwap();
                if (plugin.getPointsManager().removePoints(player.getUniqueId(), cost)) {
                    plugin.getBingoGame().triggerSurpriseSwap(player);
                } else {
                    player.sendMessage(Component.text("Not enough points!")
                        .color(NamedTextColor.RED));
                }
                player.closeInventory();
            }
            case LOCATION_TRACK -> {
                int cost = plugin.getPointsManager().getLocationTrackCost();
                PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
                if (session == null || session.getBingoCard() == null) {
                    player.closeInventory();
                    return;
                }

                BingoTask task = session.getBingoCard().getTask(pending.row(), pending.col());
                if (task == null) {
                    player.closeInventory();
                    return;
                }

                // Use the modified purchaseTracker which now uses points
                plugin.getBingoGame().getLocationTrackerManager()
                    .purchaseTracker(player, task.getType(), task.getTarget(), task.getDisplayName());
                player.closeInventory();
            }
        }
    }

    /**
     * Handle a click on the task select GUI.
     */
    private void handleTaskSelectClick(Player player, int slot) {
        // Back button
        if (slot == 45) {
            shopGUI.open(player);
            return;
        }

        // Check for task slot click
        int row = -1;
        int col = -1;

        for (int r = 0; r < ROW_STARTS.length; r++) {
            int rowStart = ROW_STARTS[r];
            if (slot >= rowStart && slot < rowStart + 5) {
                row = r;
                col = slot - rowStart;
                break;
            }
        }

        if (row == -1) {
            return;
        }

        PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
        if (session == null || session.getBingoCard() == null) {
            return;
        }

        BingoTask task = session.getBingoCard().getTask(row, col);
        if (task == null) {
            return;
        }

        // Can't re-roll completed tasks
        if (task.isCompleted()) {
            player.sendMessage(Component.text("Cannot re-roll completed tasks!")
                .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Open confirmation
        int cost = plugin.getConfigManager().getPointsTaskReroll();
        pendingConfirmations.put(player.getUniqueId(),
            new PendingConfirmation(ConfirmationType.TASK_REROLL, row, col, task.getTarget()));

        confirmationGUI.open(player, "Re-roll Task",
            "Replace this task:\n" + task.getDisplayName() + "\n\nwith a new random task.\n\n" +
            "WARNING: This changes the task\nfor ALL players!",
            cost);
    }

    /**
     * Handle a click on the view-only card GUI.
     */
    private void handleViewOnlyClick(Player player, int slot) {
        // Back button (slot 45)
        if (slot == 45) {
            viewingCards.remove(player.getUniqueId());
            PlayerSession session = plugin.getGameManager().getSessionIfExists(player.getUniqueId());
            if (session != null && session.getBingoCard() != null) {
                plugin.getBingoGame().getBingoCardGUI().open(player, session.getBingoCard());
            } else {
                player.closeInventory();
            }
        }
        // All other clicks are ignored (view-only)
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());

        if (title.equals(BingoCardGUI.GUI_TITLE) ||
            title.startsWith(RecipeGUI.GUI_TITLE_PREFIX) ||
            title.equals(ShopGUI.GUI_TITLE) ||
            title.startsWith(ConfirmationGUI.GUI_TITLE_PREFIX) ||
            title.equals(TaskSelectGUI.GUI_TITLE) ||
            title.startsWith(ViewOnlyBingoCardGUI.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
        }
    }
}
