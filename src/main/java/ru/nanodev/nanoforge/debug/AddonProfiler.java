package ru.nanodev.nanoforge.debug;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Высокоточный микросекундный профайлер аддонов и действий (NanoLens Profiler).
 */
public final class AddonProfiler {

    private static final Map<String, ProfileMetric> METRICS = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;

    private AddonProfiler() {}

    public static void setEnabled(boolean state) {
        enabled = state;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void record(String addonName, String actionType, long nanos) {
        if (!enabled) return;
        String key = (addonName != null ? addonName : "global") + "::" + (actionType != null ? actionType : "action");
        METRICS.computeIfAbsent(key, k -> new ProfileMetric()).record(nanos);
    }

    public static List<String> dumpProfile() {
        List<String> lines = new ArrayList<>();
        lines.add("§8§m----------------------------------------");
        lines.add("§a§l⚡ NanoLens Profiler §7(микросекунды, µs)");
        lines.add("§8§m----------------------------------------");

        if (METRICS.isEmpty()) {
            lines.add("§7Данные профайлера пусты (действия ещё не выполнялись).");
            lines.add("§8§m----------------------------------------");
            return lines;
        }

        List<Map.Entry<String, ProfileMetric>> sorted = new ArrayList<>(METRICS.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().totalNanos.get(), a.getValue().totalNanos.get()));

        int count = 0;
        for (Map.Entry<String, ProfileMetric> entry : sorted) {
            if (++count > 15) break; // топ 15
            String key = entry.getKey();
            ProfileMetric m = entry.getValue();
            long calls = m.calls.get();
            long avgMicros = calls > 0 ? (m.totalNanos.get() / calls) / 1000 : 0;
            long maxMicros = m.maxNanos.get() / 1000;

            String color = avgMicros > 1000 ? "§c" : (avgMicros > 200 ? "§e" : "§a");
            lines.add(String.format("§f%s §8➜ %s%d µs §7(max: %d µs, вызовов: %d)",
                    key, color, avgMicros, maxMicros, calls));
        }

        lines.add("§8§m----------------------------------------");
        return lines;
    }

    public static void reset() {
        METRICS.clear();
    }

    public static class ProfileMetric {
        public final AtomicLong calls = new AtomicLong(0);
        public final AtomicLong totalNanos = new AtomicLong(0);
        public final AtomicLong maxNanos = new AtomicLong(0);

        public void record(long nanos) {
            calls.incrementAndGet();
            totalNanos.addAndGet(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }
    }
}
