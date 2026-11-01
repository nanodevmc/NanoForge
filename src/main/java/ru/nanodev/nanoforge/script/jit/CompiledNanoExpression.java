package ru.nanodev.nanoforge.script.jit;

import ru.nanodev.nanoforge.script.NanoContext;

/**
 * Функциональный интерфейс скомпилированного NanoScript JIT-выражения.
 * Позволяет вызывать скомпилированное выражение напрямую без прохода по виртуальной машине.
 */
@FunctionalInterface
public interface CompiledNanoExpression {

    Object eval(NanoContext ctx);

    default double evalDouble(NanoContext ctx) {
        Object res = eval(ctx);
        if (res instanceof Number) return ((Number) res).doubleValue();
        if (res instanceof Boolean) return ((Boolean) res) ? 1.0 : 0.0;
        try {
            return res != null ? Double.parseDouble(String.valueOf(res)) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    default boolean evalBoolean(NanoContext ctx) {
        Object res = eval(ctx);
        if (res instanceof Boolean) return (Boolean) res;
        if (res instanceof Number) return ((Number) res).doubleValue() != 0.0;
        if (res == null) return false;
        String s = String.valueOf(res);
        return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
    }
}
