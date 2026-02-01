package com.example.swapbingo.util;

import com.example.swapbingo.SwapBingoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Utility class for chat message formatting using Adventure components.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    /**
     * Convert legacy color codes (&) to Adventure Component.
     */
    public static Component fromLegacy(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(message);
    }

    /**
     * Send a message to a player with the plugin prefix.
     */
    public static void send(Player player, String message) {
        String prefix = SwapBingoPlugin.getInstance().getConfigManager().getPrefix();
        player.sendMessage(fromLegacy(prefix + message));
    }

    /**
     * Send a message to a player without prefix.
     */
    public static void sendRaw(Player player, String message) {
        player.sendMessage(fromLegacy(message));
    }

    /**
     * Broadcast a message to all online players with prefix.
     */
    public static void broadcast(String message) {
        String prefix = SwapBingoPlugin.getInstance().getConfigManager().getPrefix();
        Component component = fromLegacy(prefix + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    /**
     * Broadcast a message without prefix.
     */
    public static void broadcastRaw(String message) {
        Component component = fromLegacy(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    /**
     * Replace placeholders in a message.
     */
    public static String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (message == null) return "";
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Format a time in seconds to a readable string.
     */
    public static String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (secs == 0) {
            return minutes + "m";
        }
        return minutes + "m " + secs + "s";
    }
}
