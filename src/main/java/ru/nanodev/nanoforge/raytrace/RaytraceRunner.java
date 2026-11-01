package ru.nanodev.nanoforge.raytrace;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.model.Addon;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Высокопроизводительный движок визуальной трассировки лучей и снарядов.
 * Позволяет создавать магические посохи, пушки, лазеры и скиллы в одну строчку YAML.
 */
public final class RaytraceRunner {

    private RaytraceRunner() {}

    @SuppressWarnings("unchecked")
    public static void execute(Map<String, Object> action, Player sourcePlayer, Entity sourceEntity,
                               Addon addon, String[] args) {
        Entity origin = sourcePlayer != null ? sourcePlayer : sourceEntity;
        if (origin == null || origin.getWorld() == null) return;

        Location startLoc = origin instanceof LivingEntity
                ? ((LivingEntity) origin).getEyeLocation()
                : origin.getLocation().clone().add(0, 1.0, 0);

        Vector direction = startLoc.getDirection().normalize();
        double maxDist = ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("distance"), 25.0);
        double step = Math.max(0.2, ru.nanodev.nanoforge.util.NumberUtil.toDouble(action.get("step"), 0.5));

        String particleName = String.valueOf(action.getOrDefault("particle", "CRIT")).toUpperCase();
        Particle particle = null;
        try {
            particle = Particle.valueOf(particleName);
        } catch (Throwable ignored) {}

        Object onHitRaw = action.get("on_hit");
        List<?> onHitActions = onHitRaw instanceof List ? (List<?>) onHitRaw : Collections.emptyList();

        Object onHitBlockRaw = action.get("on_hit_block");
        List<?> onHitBlockActions = onHitBlockRaw instanceof List ? (List<?>) onHitBlockRaw : Collections.emptyList();

        // Проверяем рейтрейс через нативный Bukkit RayTrace
        RayTraceResult blockHit = null;
        try {
            blockHit = origin.getWorld().rayTraceBlocks(startLoc, direction, maxDist, FluidCollisionMode.NEVER, true);
        } catch (Throwable ignored) {}

        double blockDist = (blockHit != null && blockHit.getHitPosition() != null)
                ? startLoc.toVector().distance(blockHit.getHitPosition())
                : maxDist;

        RayTraceResult entityHit = null;
        try {
            entityHit = origin.getWorld().rayTraceEntities(startLoc, direction, Math.min(maxDist, blockDist),
                    0.35, e -> e != origin && e instanceof LivingEntity && !e.isDead());
        } catch (Throwable ignored) {}

        double hitDist = Math.min(maxDist, blockDist);
        Entity hitVictim = null;
        if (entityHit != null && entityHit.getHitEntity() != null) {
            hitVictim = entityHit.getHitEntity();
            hitDist = startLoc.toVector().distance(entityHit.getHitPosition());
        }

        if (sourcePlayer != null && ru.nanodev.nanoforge.debug.NanoLens.isInspecting(sourcePlayer)) {
            try {
                sourcePlayer.sendMessage(org.bukkit.ChatColor.GRAY + "[NanoLens Raytrace] Dist: "
                        + String.format("%.2f", hitDist) + " | Hit: "
                        + (hitVictim != null ? hitVictim.getName() : (blockHit != null ? "Block" : "None")));
            } catch (Throwable ignored) {}
        }

        // Спавн визуальных партиклов по траектории луча
        if (particle != null) {
            try {
                Vector stepVec = direction.clone().multiply(step);
                Location current = startLoc.clone();
                double traveled = 0.0;
                while (traveled <= hitDist) {
                    current.getWorld().spawnParticle(particle, current, 1, 0, 0, 0, 0);
                    current.add(stepVec);
                    traveled += step;
                }
            } catch (Throwable ignored) {}
        }

        // Вызов логики при попадании во врага
        if (hitVictim != null && !onHitActions.isEmpty()) {
            LivingEntity victim = (LivingEntity) hitVictim;
            org.bukkit.event.Event hitEvent = null;
            if (sourcePlayer != null) {
                try {
                    hitEvent = new org.bukkit.event.entity.EntityDamageByEntityEvent(sourcePlayer, victim,
                            org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM, 0.0);
                } catch (Throwable ignored) {}
            }
            ActionRunner.run(onHitActions, sourcePlayer, hitEvent, null, addon, args);
        } else if (blockHit != null && !onHitBlockActions.isEmpty()) {
            ActionRunner.run(onHitBlockActions, sourcePlayer, null, null, addon, args);
        }
    }
}
