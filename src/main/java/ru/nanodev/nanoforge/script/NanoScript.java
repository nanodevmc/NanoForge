package ru.nanodev.nanoforge.script;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import ru.nanodev.nanoforge.model.Addon;
import ru.nanodev.nanoforge.script.jit.CompiledNanoExpression;
import ru.nanodev.nanoforge.script.jit.NanoJitEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Главный фасад для NanoScript Expression Engine, Micro-VM & NanoJIT.
 * Автоматически кэширует скомпилированный байткод и адаптивно переводит горячие участки (Hot-Paths) в JIT.
 */
public final class NanoScript {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final int JIT_THRESHOLD = 20;

    private static final Map<String, NanoProgram> PROGRAM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> HIT_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, CompiledNanoExpression> JIT_CACHE = new ConcurrentHashMap<>();

    private NanoScript() {}

    public static NanoProgram compile(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return new NanoProgram(new int[0], new double[0], new String[0], "");
        }
        String trimmed = expression.trim();
        NanoProgram cached = PROGRAM_CACHE.get(trimmed);
        if (cached != null) {
            return cached;
        }

        NanoProgram compiled = NanoParser.parseAndCompile(trimmed);
        if (PROGRAM_CACHE.size() < MAX_CACHE_SIZE) {
            PROGRAM_CACHE.put(trimmed, compiled);
        }
        return compiled;
    }

    public static Object eval(String expression, NanoContext context) {
        if (expression == null || expression.trim().isEmpty()) return null;
        String trimmed = expression.trim();

        CompiledNanoExpression jit = JIT_CACHE.get(trimmed);
        if (jit != null) {
            return jit.eval(context);
        }

        NanoProgram program = compile(trimmed);
        AtomicInteger counter = HIT_COUNTERS.computeIfAbsent(trimmed, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() >= JIT_THRESHOLD) {
            CompiledNanoExpression compiled = NanoJitEngine.compile(program);
            JIT_CACHE.put(trimmed, compiled);
            return compiled.eval(context);
        }

        return NanoVM.execute(program, context);
    }

    public static Object eval(String expression, Player player, Event event, Addon addon) {
        NanoContext ctx = NanoContext.of(player, event, addon);
        return eval(expression, ctx);
    }

    public static boolean evalBoolean(String expression, Player player, Event event, Addon addon) {
        if (expression == null || expression.trim().isEmpty()) return true;
        String trimmed = expression.trim();
        NanoContext ctx = NanoContext.of(player, event, addon);

        CompiledNanoExpression jit = JIT_CACHE.get(trimmed);
        if (jit != null) {
            return jit.evalBoolean(ctx);
        }

        NanoProgram program = compile(trimmed);
        AtomicInteger counter = HIT_COUNTERS.computeIfAbsent(trimmed, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() >= JIT_THRESHOLD) {
            CompiledNanoExpression compiled = NanoJitEngine.compile(program);
            JIT_CACHE.put(trimmed, compiled);
            return compiled.evalBoolean(ctx);
        }

        return NanoVM.executeBoolean(program, ctx);
    }

    public static double evalDouble(String expression, Player player, Event event, Addon addon) {
        if (expression == null || expression.trim().isEmpty()) return 0.0;
        String trimmed = expression.trim();
        NanoContext ctx = NanoContext.of(player, event, addon);

        CompiledNanoExpression jit = JIT_CACHE.get(trimmed);
        if (jit != null) {
            return jit.evalDouble(ctx);
        }

        NanoProgram program = compile(trimmed);
        AtomicInteger counter = HIT_COUNTERS.computeIfAbsent(trimmed, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() >= JIT_THRESHOLD) {
            CompiledNanoExpression compiled = NanoJitEngine.compile(program);
            JIT_CACHE.put(trimmed, compiled);
            return compiled.evalDouble(ctx);
        }

        return NanoVM.executeDouble(program, ctx);
    }

    public static String evalString(String expression, Player player, Event event, Addon addon) {
        Object val = eval(expression, player, event, addon);
        if (val == null) return "";
        if (val instanceof Double) {
            double d = (Double) val;
            if (d == (long) d) return String.valueOf((long) d);
        }
        return String.valueOf(val);
    }

    public static boolean isJitCompiled(String expression) {
        if (expression == null) return false;
        return JIT_CACHE.containsKey(expression.trim());
    }

    public static void clearCache() {
        PROGRAM_CACHE.clear();
        HIT_COUNTERS.clear();
        JIT_CACHE.clear();
    }
}
