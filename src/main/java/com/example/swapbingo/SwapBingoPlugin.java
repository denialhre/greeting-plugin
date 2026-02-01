package com.example.swapbingo;

import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.RelocationItem;
import com.example.swapbingo.bingo.RelocationItemListener;
import com.example.swapbingo.commands.AdminCommand;
import com.example.swapbingo.commands.BingoCommand;
import com.example.swapbingo.commands.RemakeCommand;
import com.example.swapbingo.config.ConfigManager;
import com.example.swapbingo.core.DifficultyVoteManager;
import com.example.swapbingo.core.GameBossBarManager;
import com.example.swapbingo.core.GameManager;
import com.example.swapbingo.core.PointsManager;
import com.example.swapbingo.core.RelocationVoteManager;
import com.example.swapbingo.core.RemakeVoteManager;
import com.example.swapbingo.listeners.DeathPointsListener;
import com.example.swapbingo.listeners.DebugListener;
import com.example.swapbingo.listeners.PlayerJoinLeaveListener;
import com.example.swapbingo.listeners.PlayerRespawnListener;
import com.example.swapbingo.listeners.PortalListener;
import com.example.swapbingo.listeners.PvPListener;
import com.example.swapbingo.scoreboard.WinTracker;
import com.example.swapbingo.world.SpawnCageManager;
import com.example.swapbingo.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for SwapBingo.
 * Features Bingo minigame with integrated Death Swap mechanic.
 */
public class SwapBingoPlugin extends JavaPlugin {

    private static SwapBingoPlugin instance;

    private ConfigManager configManager;
    private GameManager gameManager;
    private WorldManager worldManager;
    private WinTracker winTracker;
    private BingoGame bingoGame;
    private PointsManager pointsManager;
    private RemakeVoteManager remakeVoteManager;
    private RelocationVoteManager relocationVoteManager;
    private SpawnCageManager spawnCageManager;
    private GameBossBarManager bossBarManager;
    private DifficultyVoteManager difficultyVoteManager;
    private DebugListener debugListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        worldManager = new WorldManager(this);
        winTracker = new WinTracker(this);
        gameManager = new GameManager(this);
        pointsManager = new PointsManager(this);
        remakeVoteManager = new RemakeVoteManager(this);
        relocationVoteManager = new RelocationVoteManager(this);
        spawnCageManager = new SpawnCageManager(this);
        bossBarManager = new GameBossBarManager();
        difficultyVoteManager = new DifficultyVoteManager(this);

        // Initialize item keys
        RelocationItem.init(this);
        com.example.swapbingo.bingo.DifficultyVoteItem.init(this);

        // Initialize game
        bingoGame = new BingoGame(this);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        getLogger().info("SwapBingo has been enabled!");
    }

    @Override
    public void onDisable() {
        // Stop any running games
        if (bingoGame != null && bingoGame.isRunning()) {
            bingoGame.stop();
        }

        // Cleanup boss bars
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }

        // Save win data
        if (winTracker != null) {
            winTracker.save();
        }

        getLogger().info("SwapBingo has been disabled!");
    }

    private void registerCommands() {
        var bingoCmd = getCommand("bingo");
        if (bingoCmd != null) {
            BingoCommand executor = new BingoCommand(this);
            bingoCmd.setExecutor(executor);
            bingoCmd.setTabCompleter(executor);
        }

        var adminCmd = getCommand("swapbingo");
        if (adminCmd != null) {
            AdminCommand executor = new AdminCommand(this);
            adminCmd.setExecutor(executor);
            adminCmd.setTabCompleter(executor);
        }

        var remakeCmd = getCommand("remake");
        if (remakeCmd != null) {
            RemakeCommand executor = new RemakeCommand(this);
            remakeCmd.setExecutor(executor);
            remakeCmd.setTabCompleter(executor);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinLeaveListener(this), this);
        pm.registerEvents(new PlayerRespawnListener(this), this);
        pm.registerEvents(new PvPListener(this), this);
        pm.registerEvents(new PortalListener(this), this);
        pm.registerEvents(new com.example.swapbingo.bingo.gui.GUIListener(this), this);
        pm.registerEvents(new com.example.swapbingo.bingo.BingoItemListener(this), this);
        debugListener = new DebugListener(this);
        pm.registerEvents(debugListener, this);
        pm.registerEvents(new DeathPointsListener(this), this);
        pm.registerEvents(new RelocationItemListener(this), this);
        pm.registerEvents(new com.example.swapbingo.bingo.DifficultyVoteListener(this), this);
    }

    public static SwapBingoPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public WinTracker getWinTracker() {
        return winTracker;
    }

    public BingoGame getBingoGame() {
        return bingoGame;
    }

    public PointsManager getPointsManager() {
        return pointsManager;
    }

    public RemakeVoteManager getRemakeVoteManager() {
        return remakeVoteManager;
    }

    public RelocationVoteManager getRelocationVoteManager() {
        return relocationVoteManager;
    }

    public SpawnCageManager getSpawnCageManager() {
        return spawnCageManager;
    }

    public GameBossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public DifficultyVoteManager getDifficultyVoteManager() {
        return difficultyVoteManager;
    }

    public DebugListener getDebugListener() {
        return debugListener;
    }
}
