package com.example.swapbingo.scoreboard;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks and persists player wins across rounds.
 */
public class WinTracker {

    private final SwapBingoPlugin plugin;
    private final File winsFile;
    private final Map<UUID, Integer> deathSwapWins;
    private final Map<UUID, Integer> bingoWins;

    public WinTracker(SwapBingoPlugin plugin) {
        this.plugin = plugin;
        this.winsFile = new File(plugin.getDataFolder(),
            plugin.getConfigManager().getWinsFile());
        this.deathSwapWins = new HashMap<>();
        this.bingoWins = new HashMap<>();

        load();
    }

    /**
     * Add a win for a player.
     */
    public void addWin(UUID playerId, String gameType) {
        switch (gameType.toLowerCase()) {
            case "deathswap" -> {
                int wins = deathSwapWins.getOrDefault(playerId, 0) + 1;
                deathSwapWins.put(playerId, wins);
            }
            case "bingo" -> {
                int wins = bingoWins.getOrDefault(playerId, 0) + 1;
                bingoWins.put(playerId, wins);
            }
        }
        save();
    }

    /**
     * Get death swap wins for a player.
     */
    public int getDeathSwapWins(UUID playerId) {
        return deathSwapWins.getOrDefault(playerId, 0);
    }

    /**
     * Get bingo wins for a player.
     */
    public int getBingoWins(UUID playerId) {
        return bingoWins.getOrDefault(playerId, 0);
    }

    /**
     * Get total wins for a player.
     */
    public int getTotalWins(UUID playerId) {
        return getDeathSwapWins(playerId) + getBingoWins(playerId);
    }

    /**
     * Load wins from file.
     */
    public void load() {
        if (!winsFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(winsFile);

        // Load death swap wins
        var deathSwapSection = config.getConfigurationSection("deathswap");
        if (deathSwapSection != null) {
            for (String key : deathSwapSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int wins = deathSwapSection.getInt(key);
                    deathSwapWins.put(uuid, wins);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Load bingo wins
        var bingoSection = config.getConfigurationSection("bingo");
        if (bingoSection != null) {
            for (String key : bingoSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int wins = bingoSection.getInt(key);
                    bingoWins.put(uuid, wins);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("Loaded win data for " +
            (deathSwapWins.size() + bingoWins.size()) + " entries");
    }

    /**
     * Save wins to file.
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        // Save death swap wins
        for (Map.Entry<UUID, Integer> entry : deathSwapWins.entrySet()) {
            config.set("deathswap." + entry.getKey().toString(), entry.getValue());
        }

        // Save bingo wins
        for (Map.Entry<UUID, Integer> entry : bingoWins.entrySet()) {
            config.set("bingo." + entry.getKey().toString(), entry.getValue());
        }

        try {
            config.save(winsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save wins: " + e.getMessage());
        }
    }

    /**
     * Reset wins for a player.
     */
    public void resetPlayer(UUID playerId) {
        deathSwapWins.remove(playerId);
        bingoWins.remove(playerId);
        save();
    }

    /**
     * Get all death swap wins.
     */
    public Map<UUID, Integer> getAllDeathSwapWins() {
        return new HashMap<>(deathSwapWins);
    }

    /**
     * Get all bingo wins.
     */
    public Map<UUID, Integer> getAllBingoWins() {
        return new HashMap<>(bingoWins);
    }
}
