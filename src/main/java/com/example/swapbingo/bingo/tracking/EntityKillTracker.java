package com.example.swapbingo.bingo.tracking;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.bingo.BingoGame;
import com.example.swapbingo.bingo.tasks.TaskType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Tracks entity kills for bingo tasks.
 */
public class EntityKillTracker implements Listener {

    private final SwapBingoPlugin plugin;
    private final BingoGame game;

    public EntityKillTracker(SwapBingoPlugin plugin, BingoGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        if (killer == null) {
            return;
        }

        if (!game.isRunning() || !game.isParticipant(killer.getUniqueId())) {
            return;
        }

        String entityType = event.getEntity().getType().name();
        game.handleTaskCompletion(killer, TaskType.KILL_ENTITY, entityType);
    }
}
