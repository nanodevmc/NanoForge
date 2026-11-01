package ru.nanodev.nanoforge.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Экземпляр активного процедурного данжа, босс-арены или авто-шахты.
 * Позволяет восстанавливать оригинальные блоки мира при сбросе/окончании таймера.
 */
public class DungeonInstance {

    private final String id;
    private final String template;
    private final Location origin;
    private final long createdAt;
    private final long expireTimeMillis;
    private final Map<Location, Material> originalBlocks;
    private boolean active = true;

    public DungeonInstance(String id, String template, Location origin, long durationMillis) {
        this.id = id;
        this.template = template;
        this.origin = origin != null ? origin.clone() : null;
        this.createdAt = System.currentTimeMillis();
        this.expireTimeMillis = durationMillis > 0 ? createdAt + durationMillis : Long.MAX_VALUE;
        this.originalBlocks = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getTemplate() {
        return template;
    }

    public Location getOrigin() {
        return origin;
    }

    public long getExpireTimeMillis() {
        return expireTimeMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expireTimeMillis;
    }

    public boolean isActive() {
        return active;
    }

    public void recordOriginalBlock(Location loc, Material originalMat) {
        if (loc != null && !originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc.clone(), originalMat);
        }
    }

    public Map<Location, Material> getOriginalBlocks() {
        return Collections.unmodifiableMap(originalBlocks);
    }

    public void restore() {
        if (!active) return;
        active = false;
        for (Map.Entry<Location, Material> e : originalBlocks.entrySet()) {
            Location loc = e.getKey();
            Material mat = e.getValue();
            try {
                if (loc.getWorld() != null) {
                    Block b = loc.getBlock();
                    if (b.getType() != mat) {
                        b.setType(mat, false);
                    }
                }
            } catch (Throwable ignored) {}
        }
        originalBlocks.clear();
    }
}
