package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import com.example.swapbingo.core.PointsManager;
import com.example.swapbingo.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Listener for awarding trap kill points when a player dies at another player's
 * swapped location.
 */
public class DeathPointsListener implements Listener {

    private final SwapBingoPlugin plugin;

    public DeathPointsListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Check if game is running and player is a participant
        if (!plugin.getBingoGame().isRunning()) {
            return;
        }

        if (!plugin.getBingoGame().isParticipant(victim.getUniqueId())) {
            return;
        }

        // Try to find who set the trap (whose location the victim was swapped to)
        // If the victim died within the configured time window after a swap,
        // the player whose location they went to gets credited with the trap kill
        PointsManager pointsManager = plugin.getPointsManager();
        UUID trapSetterId = pointsManager.getTrapSetter(victim.getUniqueId());

        if (trapSetterId != null) {
            // Award trap kill points
            pointsManager.awardTrapKill(trapSetterId);

            // Notify the trap setter
            Player trapSetter = plugin.getServer().getPlayer(trapSetterId);
            if (trapSetter != null && trapSetter.isOnline()) {
                int points = plugin.getConfigManager().getPointsTrapKill();

                trapSetter.sendMessage(Component.text("")
                    .append(Component.text("TRAP KILL! ").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                    .append(Component.text(victim.getName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" died at your swapped location! ").color(NamedTextColor.GRAY))
                    .append(Component.text("+" + points + " points").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));

                trapSetter.playSound(trapSetter.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }

            // Broadcast the trap kill
            MessageUtil.broadcast("&c" + victim.getName() + " &7fell victim to &e" +
                (trapSetter != null ? trapSetter.getName() : "someone") + "&7's trap!");
        }
    }
}
