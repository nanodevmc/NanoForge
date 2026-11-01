package ru.nanodev.nanoforge.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Движок процедурных инстанс-данжей, босс-арен и авто-шахт (NanoDungeon).
 * Поддерживает NBT-структуры через нативный Bukkit StructureManager и процедурные генераторы шахт/коробок.
 */
public final class DungeonManager {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static DungeonManager instance;

    private final Plugin plugin;
    private final File structuresFolder;
    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();
    private int cleanupTaskId = -1;

    public DungeonManager(Plugin plugin) {
        this.plugin = plugin;
        this.structuresFolder = new File(plugin.getDataFolder(), "structures");
        if (!structuresFolder.exists()) {
            structuresFolder.mkdirs();
        }
    }

    public static synchronized void init(Plugin plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new DungeonManager(plugin);
        instance.start();
    }

    public static DungeonManager get() {
        return instance;
    }

    private void start() {
        try {
            cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::cleanupExpired, 100L, 100L);
            LOG.info("[NanoDungeon] Движок процедурных инстансов инициализирован.");
        } catch (Throwable ignored) {}
    }

    public void shutdown() {
        if (cleanupTaskId != -1) {
            try {
                Bukkit.getScheduler().cancelTask(cleanupTaskId);
            } catch (Throwable ignored) {}
        }
        for (DungeonInstance inst : instances.values()) {
            inst.restore();
        }
        instances.clear();
    }

    public DungeonInstance spawnDungeon(String id, String template, Location origin, long durationMillis) {
        if (id == null || origin == null || origin.getWorld() == null) return null;
        String instId = id.trim().toLowerCase();

        // Если такой инстанс уже существует - сначала сбрасываем его
        DungeonInstance old = instances.remove(instId);
        if (old != null) {
            old.restore();
        }

        DungeonInstance instance = new DungeonInstance(instId, template, origin, durationMillis);

        // 1. Проверяем NBT-структуру в папке structures
        File nbtFile = new File(structuresFolder, template + ".nbt");
        boolean structureLoaded = false;

        if (nbtFile.exists()) {
            try {
                org.bukkit.structure.StructureManager sm = Bukkit.getStructureManager();
                if (sm != null) {
                    org.bukkit.structure.Structure struct = sm.loadStructure(nbtFile);
                    if (struct != null) {
                        struct.place(origin, false, org.bukkit.block.structure.StructureRotation.NONE,
                                org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, new java.util.Random());
                        structureLoaded = true;
                    }
                }
            } catch (Throwable t) {
                LOG.warning("[NanoDungeon] Ошибка загрузки структуры " + nbtFile.getName() + ": " + t.getMessage());
            }
        }

        // 2. Если структуры нет или это процедурный шаблон (например, шахта / куб 5x3x5)
        if (!structureLoaded) {
            spawnProceduralBox(instance, origin, template);
        }

        instances.put(instId, instance);
        LOG.info("[NanoDungeon] Заспавнен инстанс '" + instId + "' (шаблон: " + template + ") в "
                + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
        return instance;
    }

    private void spawnProceduralBox(DungeonInstance inst, Location origin, String template) {
        int radiusX = 4;
        int heightY = 4;
        int radiusZ = 4;
        Material fillMat = Material.STONE;
        Material wallMat = Material.COBBLESTONE;

        if (template != null && template.contains("x")) {
            try {
                String[] dims = template.split("x");
                if (dims.length >= 2) radiusX = Integer.parseInt(dims[0]);
                if (dims.length >= 2) heightY = Integer.parseInt(dims[1]);
                if (dims.length >= 3) radiusZ = Integer.parseInt(dims[2]);
            } catch (Throwable ignored) {}
        }

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = 0; y <= heightY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    Location loc = origin.clone().add(x, y, z);
                    Block block = loc.getBlock();
                    inst.recordOriginalBlock(loc, block.getType());

                    boolean isWall = (x == -radiusX || x == radiusX || z == -radiusZ || z == radiusZ || y == 0 || y == heightY);
                    if (isWall) {
                        block.setType(wallMat, false);
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public boolean resetDungeon(String id) {
        if (id == null) return false;
        DungeonInstance inst = instances.get(id.trim().toLowerCase());
        if (inst != null) {
            inst.restore();
            // Повторный спавн
            spawnDungeon(inst.getId(), inst.getTemplate(), inst.getOrigin(),
                    inst.getExpireTimeMillis() - System.currentTimeMillis());
            return true;
        }
        return false;
    }

    public boolean clearDungeon(String id) {
        if (id == null) return false;
        DungeonInstance inst = instances.remove(id.trim().toLowerCase());
        if (inst != null) {
            inst.restore();
            return true;
        }
        return false;
    }

    public DungeonInstance getInstance(String id) {
        return id != null ? instances.get(id.trim().toLowerCase()) : null;
    }

    public Map<String, DungeonInstance> getActiveInstances() {
        return Collections.unmodifiableMap(instances);
    }

    private void cleanupExpired() {
        for (Map.Entry<String, DungeonInstance> e : instances.entrySet()) {
            if (e.getValue().isExpired()) {
                e.getValue().restore();
                instances.remove(e.getKey());
                LOG.info("[NanoDungeon] Инстанс '" + e.getKey() + "' истёк по времени и восстановлен.");
            }
        }
    }
}
