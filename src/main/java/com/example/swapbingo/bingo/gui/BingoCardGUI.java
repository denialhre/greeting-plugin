package com.example.swapbingo.bingo.gui;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoCard;
import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.BingoTask;
import com.example.swapbingo.bingo.WinPattern;
import com.example.swapbingo.bingo.tasks.TaskType;
import com.example.swapbingo.core.PlayerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for displaying a player's bingo card with improved layout.
 */
public class BingoCardGUI {

    private final SwapBingoPlugin plugin;

    // Special entity display materials (for entities without standard spawn eggs)
    private static final Map<String, Material> SPECIAL_ENTITY_ITEMS = Map.of(
        "ENDER_DRAGON", Material.DRAGON_HEAD,
        "WITHER", Material.WITHER_SKELETON_SKULL,
        "IRON_GOLEM", Material.IRON_BLOCK,
        "SNOW_GOLEM", Material.CARVED_PUMPKIN,
        "GIANT", Material.ZOMBIE_HEAD
    );

    /**
     * Get the spawn egg material for an entity name.
     * Dynamically tries {ENTITY_NAME}_SPAWN_EGG pattern first, then special cases.
     */
    private static Material getSpawnEggForEntity(String entityName) {
        String upperName = entityName.toUpperCase();

        // Check special cases first
        Material special = SPECIAL_ENTITY_ITEMS.get(upperName);
        if (special != null) {
            return special;
        }

        // Try the standard spawn egg pattern: ENTITY_NAME_SPAWN_EGG
        String spawnEggName = upperName + "_SPAWN_EGG";
        try {
            Material spawnEgg = Material.valueOf(spawnEggName);
            if (spawnEgg.isItem()) {
                return spawnEgg;
            }
        } catch (IllegalArgumentException ignored) {
            // No spawn egg with this name
        }

        // Try mooshroom special case (Material is MOOSHROOM_SPAWN_EGG but entity might be different)
        if (upperName.equals("MUSHROOM_COW") || upperName.equals("MOOSHROOM")) {
            return Material.MOOSHROOM_SPAWN_EGG;
        }

        // Fallback to a generic mob head or zombie spawn egg
        return Material.ZOMBIE_SPAWN_EGG;
    }

    public static final String GUI_TITLE = "Bingo Card";

    public BingoCardGUI(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the bingo card GUI for a player.
     */
    public void open(Player player, BingoCard card) {
        open(player, card, plugin.getBingoGame());
    }

    /**
     * Open the bingo card GUI for a player with game context.
     */
    public void open(Player player, BingoCard card, BingoGame game) {
        // Create 6-row inventory (54 slots)
        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(GUI_TITLE).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // Fill entire background with black glass
        ItemStack background = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, background);
        }

        // === ROW 0: Pattern info and title ===
        // Slot 4: Pattern info (center)
        inv.setItem(4, createPatternInfoItem(card));

        // === ROWS 1-5: Bingo grid (centered) ===
        // Grid is 5x5, centered in slots starting at position 2 of each row
        // Row 1: slots 11-15 (9+2 to 9+6)
        // Row 2: slots 20-24 (18+2 to 18+6)
        // Row 3: slots 29-33 (27+2 to 27+6)
        // Row 4: slots 38-42 (36+2 to 36+6)
        // Row 5: slots 47-51 (45+2 to 45+6)

        int[] rowStarts = {11, 20, 29, 38, 47};

        for (int row = 0; row < card.getSize(); row++) {
            for (int col = 0; col < card.getSize(); col++) {
                BingoTask task = card.getTask(row, col);
                if (task != null) {
                    int slot = rowStarts[row] + col;
                    ItemStack item = createTaskItem(task);
                    inv.setItem(slot, item);
                }
            }
        }

        // === RIGHT SIDE: Player progress (slots 8, 17, 26, 35, 44) ===
        List<PlayerProgress> playerProgress = calculatePlayerProgress(game, player);
        int[] progressSlots = {8, 17, 26, 35, 44};

        for (int i = 0; i < Math.min(playerProgress.size(), progressSlots.length); i++) {
            inv.setItem(progressSlots[i], createPlayerHead(playerProgress.get(i)));
        }

        // === LEFT SIDE: Your stats (slot 0) ===
        inv.setItem(0, createYourStatsItem(card, game));

        // === Points display (slot 1) ===
        inv.setItem(1, createPointsItem(player));

        // === Shop button (slot 53) ===
        inv.setItem(53, createShopButton());

        player.openInventory(inv);
    }

    /**
     * Create the pattern info item.
     */
    private ItemStack createPatternInfoItem(BingoCard card) {
        WinPattern pattern = card.getRequiredPattern();
        Material material = pattern == WinPattern.ROW ? Material.ARROW : Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String patternName = pattern == WinPattern.ROW ? "Complete a ROW" : "Complete a COLUMN";
        meta.displayName(Component.text("Win Condition")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(patternName)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (pattern == WinPattern.ROW) {
            lore.add(Component.text("Complete all 5 tasks in")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("any horizontal row to win!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Complete all 5 tasks in")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("any vertical column to win!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create your stats item.
     */
    private ItemStack createYourStatsItem(BingoCard card, BingoGame game) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Your Progress")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        int completed = card.getCompletedCount();
        int total = card.getTotalTasks();
        int tasksToWin = calculateTasksToWin(card);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Tasks Completed: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(completed + "/" + total)
                .color(NamedTextColor.GREEN)));
        lore.add(Component.empty());
        lore.add(Component.text("Tasks to Win: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(String.valueOf(tasksToWin))
                .color(tasksToWin <= 2 ? NamedTextColor.GOLD : NamedTextColor.WHITE)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the points display item.
     * Uses stack size to visually represent points (capped at 64).
     */
    private ItemStack createPointsItem(Player player) {
        int points = plugin.getPointsManager().getPoints(player.getUniqueId());

        // Use stack size to represent points (min 1, max 64)
        int stackSize = Math.max(1, Math.min(64, points));
        ItemStack item = new ItemStack(Material.EMERALD, stackSize);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Your Points")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(points + " Points")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Earn points by:")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  + Task completion")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  + Trap kills")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the shop button.
     */
    private ItemStack createShopButton() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Points Shop")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Spend your points on")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("powerful abilities!")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to open shop")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a glass pane item.
     */
    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item representing a bingo task.
     */
    public ItemStack createTaskItem(BingoTask task) {
        Material material;

        if (task.isCompleted()) {
            // Use green concrete for completed tasks
            material = Material.LIME_CONCRETE;
        } else {
            material = getTaskMaterial(task);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Set display name
        Component name;
        if (task.isCompleted()) {
            name = Component.text("✔ " + task.getDisplayName())
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        } else {
            name = Component.text(task.getDisplayName())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
        }
        meta.displayName(name);

        // Set lore
        List<Component> lore = new ArrayList<>();

        // Task type with icon
        String typeIcon = switch (task.getType()) {
            case COLLECT_ITEM -> "\uD83D\uDCE6"; // 📦
            case KILL_ENTITY -> "⚔";
            case VISIT_BIOME -> "\uD83C\uDF0D"; // 🌍
            case DISCOVER_STRUCTURE -> "\uD83C\uDFDB"; // 🏛
        };
        String typeStr = switch (task.getType()) {
            case COLLECT_ITEM -> "Collect Item";
            case KILL_ENTITY -> "Kill Entity";
            case VISIT_BIOME -> "Visit Biome";
            case DISCOVER_STRUCTURE -> "Discover Structure";
        };

        lore.add(Component.text(typeStr)
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // Status
        if (task.isCompleted()) {
            lore.add(Component.text("Status: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("COMPLETED ✔")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)));
        } else {
            lore.add(Component.text("Status: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Incomplete")
                    .color(NamedTextColor.RED)));
        }

        // Recipe info for item tasks
        if (task.getType() == com.example.swapbingo.bingo.tasks.TaskType.COLLECT_ITEM) {
            lore.add(Component.empty());

            String itemName = task.getTarget().toLowerCase();
            var recipes = plugin.getBingoGame().getPoolLoader().getRecipes(itemName);

            if (recipes.isEmpty()) {
                lore.add(Component.text("Recipe: ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("No crafting recipe")
                        .color(NamedTextColor.DARK_GRAY)));
            } else {
                lore.add(Component.text("Recipe: ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("Click to view")
                        .color(NamedTextColor.AQUA)));
                if (recipes.size() > 1) {
                    lore.add(Component.text("(" + recipes.size() + " recipes available)")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        // Location tracking for biome/structure tasks
        if (task.getType() == TaskType.VISIT_BIOME || task.getType() == TaskType.DISCOVER_STRUCTURE) {
            lore.add(Component.empty());
            int trackCost = plugin.getConfigManager().getPointsLocationTrack();
            lore.add(Component.text("Click to track location")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Cost: " + trackCost + " points")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        }

        // Position
        lore.add(Component.empty());
        lore.add(Component.text("Row " + (task.getRow() + 1) + ", Column " + (task.getCol() + 1))
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Get the display material for a task (when not completed).
     */
    private Material getTaskMaterial(BingoTask task) {
        return switch (task.getType()) {
            case COLLECT_ITEM -> {
                try {
                    Material mat = Material.valueOf(task.getTarget());
                    yield mat.isItem() ? mat : Material.BARRIER;
                } catch (IllegalArgumentException e) {
                    yield Material.BARRIER;
                }
            }
            case KILL_ENTITY -> getSpawnEggForEntity(task.getTarget());
            case VISIT_BIOME -> Material.FILLED_MAP;
            case DISCOVER_STRUCTURE -> Material.MAP;
        };
    }

    /**
     * Calculate tasks remaining to win for a card.
     */
    private int calculateTasksToWin(BingoCard card) {
        int size = card.getSize();
        int minRemaining = size; // Maximum possible

        if (card.getRequiredPattern() == WinPattern.ROW) {
            // Check each row
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
            // Check each column
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
     * Calculate progress for all players.
     */
    private List<PlayerProgress> calculatePlayerProgress(BingoGame game, Player viewer) {
        List<PlayerProgress> progress = new ArrayList<>();

        for (UUID uuid : game.getParticipants()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            PlayerSession session = plugin.getGameManager().getSessionIfExists(uuid);

            if (session == null || session.getBingoCard() == null) {
                continue;
            }

            BingoCard card = session.getBingoCard();
            int tasksToWin = calculateTasksToWin(card);
            int completed = card.getCompletedCount();

            progress.add(new PlayerProgress(
                uuid,
                offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown",
                tasksToWin,
                completed,
                uuid.equals(viewer.getUniqueId())
            ));
        }

        // Sort by tasks to win (ascending) - closest to winning first
        progress.sort(Comparator.comparingInt(PlayerProgress::tasksToWin));

        return progress;
    }

    /**
     * Create a player head showing their progress.
     */
    private ItemStack createPlayerHead(PlayerProgress progress) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Set the skull owner
        OfflinePlayer player = Bukkit.getOfflinePlayer(progress.uuid());
        meta.setOwningPlayer(player);

        // Set display name
        NamedTextColor nameColor = progress.isViewer() ? NamedTextColor.AQUA : NamedTextColor.WHITE;
        meta.displayName(Component.text(progress.name())
            .color(nameColor)
            .decorate(progress.isViewer() ? TextDecoration.BOLD : TextDecoration.OBFUSCATED)
            .decoration(TextDecoration.OBFUSCATED, false)
            .decoration(TextDecoration.ITALIC, false));

        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Tasks to win with color coding
        NamedTextColor progressColor;
        if (progress.tasksToWin() == 0) {
            progressColor = NamedTextColor.GOLD;
        } else if (progress.tasksToWin() <= 2) {
            progressColor = NamedTextColor.YELLOW;
        } else {
            progressColor = NamedTextColor.WHITE;
        }

        lore.add(Component.text("Tasks to Win: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(String.valueOf(progress.tasksToWin()))
                .color(progressColor)
                .decorate(progress.tasksToWin() <= 2 ? TextDecoration.BOLD : TextDecoration.OBFUSCATED)
                .decoration(TextDecoration.OBFUSCATED, false)));

        lore.add(Component.text("Completed: ")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(progress.completed() + "/25")
                .color(NamedTextColor.GREEN)));

        if (progress.isViewer()) {
            lore.add(Component.empty());
            lore.add(Component.text("(You)")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click to view their card")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Record for player progress data.
     */
    private record PlayerProgress(UUID uuid, String name, int tasksToWin, int completed, boolean isViewer) {}
}
