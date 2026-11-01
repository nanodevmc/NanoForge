package ru.nanodev.nanoforge.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Легковесный движок всплывающего урона и временных голограмм (Floating Damage Displays).
 * Совместим с 1.16-1.21.x без установки сторонних плагинов (DecentHolograms / HolographicDisplays).
 */
public final class HologramManager {

    private HologramManager() {}

    public static void spawn(Plugin plugin, Entity target, Location explicitLoc, String text,
                             long durationTicks, double offsetY, double velocityY) {
        if (plugin == null || text == null || text.isEmpty()) return;

        Location spawnLoc = explicitLoc != null
                ? explicitLoc.clone()
                : (target != null ? target.getLocation().clone() : null);

        if (spawnLoc == null || spawnLoc.getWorld() == null) return;

        if (target instanceof LivingEntity) {
            spawnLoc.add(0, ((LivingEntity) target).getEyeHeight() * 0.75 + offsetY, 0);
        } else {
            spawnLoc.add(0, offsetY, 0);
        }

        ArmorStand stand = null;
        try {
            stand = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setMarker(true);
                as.setGravity(false);
                as.setCustomName(text);
                as.setCustomNameVisible(true);
                as.setSmall(true);
                as.setBasePlate(false);
                as.setArms(false);
                as.setCollidable(false);
                as.setInvulnerable(true);
            });
        } catch (Throwable ignored) {}

        if (stand == null) return;

        final ArmorStand finalStand = stand;

        // Плавный подъём вверх
        if (velocityY > 0) {
            try {
                int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                    try {
                        if (finalStand.isValid() && !finalStand.isDead()) {
                            finalStand.teleport(finalStand.getLocation().add(0, velocityY, 0));
                        }
                    } catch (Throwable ignored) {}
                }, 1L, 1L);

                // Удаление по истечении durationTicks
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        Bukkit.getScheduler().cancelTask(taskId);
                        if (finalStand.isValid()) {
                            finalStand.remove();
                        }
                    } catch (Throwable ignored) {}
                }, Math.max(5L, durationTicks));
            } catch (Throwable ignored) {}
        } else {
            try {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        if (finalStand.isValid()) {
                            finalStand.remove();
                        }
                    } catch (Throwable ignored) {}
                }, Math.max(5L, durationTicks));
            } catch (Throwable ignored) {}
        }
    }
}
