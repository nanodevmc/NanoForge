package ru.nanodev.nanoforge.debug;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Инспектор визуальной отладки лучей, векторов и хитбоксов в реальном времени.
 */
public final class NanoLens {

    private static final Set<UUID> INSPECTING_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private NanoLens() {}

    public static boolean isInspecting(Player player) {
        return player != null && INSPECTING_PLAYERS.contains(player.getUniqueId());
    }

    public static boolean toggleInspect(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (INSPECTING_PLAYERS.contains(uuid)) {
            INSPECTING_PLAYERS.remove(uuid);
            return false;
        } else {
            INSPECTING_PLAYERS.add(uuid);
            return true;
        }
    }

    public static void clear() {
        INSPECTING_PLAYERS.clear();
    }
}
