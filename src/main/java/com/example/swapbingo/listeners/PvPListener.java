package com.example.swapbingo.listeners;

import com.example.swapbingo.SwapBingoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Handles PvP restrictions during grace period.
 */
public class PvPListener implements Listener {

    private final SwapBingoPlugin plugin;

    public PvPListener(SwapBingoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only care about player vs player damage
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;

        // Direct hit
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        }
        // Projectile damage
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player p) {
                attacker = p;
            }
        }

        if (attacker == null) {
            return;
        }

        // Check if game is running and in grace period
        if (!plugin.getBingoGame().isRunning()) {
            return;
        }

        // Only apply to game participants
        if (!plugin.getBingoGame().isParticipant(attacker.getUniqueId()) ||
            !plugin.getBingoGame().isParticipant(victim.getUniqueId())) {
            return;
        }

        // Block PvP during grace period
        if (plugin.getBingoGame().isInGracePeriod()) {
            event.setCancelled(true);
            attacker.sendActionBar(
                net.kyori.adventure.text.Component.text("PvP disabled during grace period!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED)
            );
        }
    }
}
