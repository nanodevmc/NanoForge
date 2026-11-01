package ru.nanodev.nanoforge.debug;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.nanodev.nanoforge.NanoForgePlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Hot-Swap фоновый сервис мгновенной перезагрузки аддонов (NanoLens).
 * Отслеживает изменения .yml файлов через Java WatchService и применяет правки без /nano reload.
 */
public final class AddonFileWatcher {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static AddonFileWatcher instance;

    private final NanoForgePlugin plugin;
    private final File addonsDir;
    private final Map<String, Long> lastChangeTimes = new ConcurrentHashMap<>();
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean running = true;

    public AddonFileWatcher(NanoForgePlugin plugin) {
        this.plugin = plugin;
        this.addonsDir = new File(plugin.getDataFolder(), "addons");
    }

    public static synchronized void init(NanoForgePlugin plugin) {
        if (instance != null) {
            instance.stop();
        }
        instance = new AddonFileWatcher(plugin);
        instance.start();
    }

    public static AddonFileWatcher get() {
        return instance;
    }

    private void start() {
        if (!addonsDir.exists()) return;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursive(addonsDir.toPath());

            watcherThread = new Thread(this::runWatcher, "NanoLens-HotSwap-Watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            LOG.info("[NanoLens] Hot-Swap FileWatcher запущен (папка: " + addonsDir.getName() + ").");
        } catch (Throwable t) {
            LOG.warning("[NanoLens] Не удалось запустить FileWatcher: " + t.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Throwable ignored) {}
        }
    }

    private void registerRecursive(Path root) throws IOException {
        Files.walk(root).filter(Files::isDirectory).forEach(path -> {
            try {
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
            } catch (IOException ignored) {}
        });
    }

    private void runWatcher() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (Exception e) {
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                Path fileName = (Path) event.context();
                if (fileName != null && fileName.toString().endsWith(".yml")) {
                    handleFileModified(fileName.toString());
                }
            }

            boolean valid = key.reset();
            if (!valid) break;
        }
    }

    private void handleFileModified(String fileName) {
        long now = System.currentTimeMillis();
        Long last = lastChangeTimes.get(fileName);
        if (last != null && (now - last) < 300) {
            return; // debounce 300ms
        }
        lastChangeTimes.put(fileName, now);

        Bukkit.getScheduler().runTask(plugin, () -> {
            long startNanos = System.nanoTime();
            try {
                if (plugin.getAddonManager() != null) {
                    plugin.getAddonManager().reloadAll();
                    long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
                    String badge = "§8[§aNanoLens§8] §a⚡ Аддоны горячо перезагружены §7(" + tookMs + " ms)";
                    LOG.info("[NanoLens] ⚡ Hot-Swap: Аддоны мгновенно перезагружены (" + tookMs + " ms)");

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.isOp() || p.hasPermission("nanoforge.admin")) {
                            p.sendMessage(badge);
                        }
                    }
                }
            } catch (Throwable t) {
                LOG.warning("[NanoLens] Ошибка Hot-Swap перезагрузки: " + t.getMessage());
            }
        });
    }
}
