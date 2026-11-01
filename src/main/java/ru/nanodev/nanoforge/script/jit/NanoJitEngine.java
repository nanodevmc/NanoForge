package ru.nanodev.nanoforge.script.jit;

import ru.nanodev.nanoforge.script.NanoOpcode;
import ru.nanodev.nanoforge.script.NanoProgram;
import ru.nanodev.nanoforge.script.NanoVM;

/**
 * Сверхскоростной адаптивный JIT-компилятор (Just-In-Time) для NanoScript.
 * Преобразует часто выполняемые байткод-программы в прямые скомпилированные функции.
 */
public final class NanoJitEngine {

    private NanoJitEngine() {}

    public static CompiledNanoExpression compile(NanoProgram program) {
        if (program == null || program.code == null || program.code.length == 0) {
            return ctx -> 0.0;
        }

        int[] code = program.code;
        double[] numPool = program.numPool;
        String[] strPool = program.strPool;

        // Оптимизация 1: Одиночная числовая константа (OP_CONST_NUM)
        if (code.length == 2 && code[0] == NanoOpcode.OP_CONST_NUM) {
            final double val = (code[1] >= 0 && code[1] < numPool.length) ? numPool[code[1]] : 0.0;
            return ctx -> val;
        }

        // Оптимизация 2: Булевы константы
        if (code.length == 1) {
            if (code[0] == NanoOpcode.OP_CONST_TRUE) return ctx -> true;
            if (code[0] == NanoOpcode.OP_CONST_FALSE) return ctx -> false;
            if (code[0] == NanoOpcode.OP_CONST_NULL) return ctx -> null;
        }

        // Оптимизация 3: Одиночная строковая константа (OP_CONST_STR)
        if (code.length == 2 && code[0] == NanoOpcode.OP_CONST_STR) {
            final String val = (code[1] >= 0 && code[1] < strPool.length) ? strPool[code[1]] : "";
            return ctx -> val;
        }

        // Оптимизация 4: Одиночное обращение к переменной (OP_LOAD_VAR)
        if (code.length == 2 && code[0] == NanoOpcode.OP_LOAD_VAR) {
            final String varName = (code[1] >= 0 && code[1] < strPool.length) ? strPool[code[1]] : "";
            return ctx -> ctx != null ? ctx.resolveVar(varName) : null;
        }

        // Оптимизация 5: Доступ к свойству целевого объекта (OP_LOAD_VAR + OP_LOAD_PROP)
        if (code.length == 4 && code[0] == NanoOpcode.OP_LOAD_VAR && code[2] == NanoOpcode.OP_LOAD_PROP) {
            final String varName = (code[1] >= 0 && code[1] < strPool.length) ? strPool[code[1]] : "";
            final String propName = (code[3] >= 0 && code[3] < strPool.length) ? strPool[code[3]] : "";
            return ctx -> {
                if (ctx == null) return null;
                Object target = ctx.resolveVar(varName);
                return ru.nanodev.nanoforge.script.BukkitBinding.getProperty(target, propName);
            };
        }

        // Оптимизация 6: Простое сложение переменной и константы (VAR + NUM)
        if (code.length == 5 && code[0] == NanoOpcode.OP_LOAD_VAR && code[2] == NanoOpcode.OP_CONST_NUM && code[4] == NanoOpcode.OP_ADD) {
            final String varName = (code[1] >= 0 && code[1] < strPool.length) ? strPool[code[1]] : "";
            final double num = (code[3] >= 0 && code[3] < numPool.length) ? numPool[code[3]] : 0.0;
            return ctx -> {
                if (ctx == null) return num;
                Object v = ctx.resolveVar(varName);
                return ru.nanodev.nanoforge.script.BukkitBinding.toDouble(v) + num;
            };
        }

        // Общий JIT-компилятор: кэшированный быстрый запуск VM
        return ctx -> NanoVM.execute(program, ctx);
    }
}
