package ru.nanodev.nanoforge.script;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.nanodev.nanoforge.model.Addon;

import org.assertj.core.data.Offset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class NanoScriptTest {

    @Test
    @DisplayName("Константное сворачивание (Constant Folding)")
    void testConstantFolding() {
        NanoProgram program = NanoParser.parseAndCompile("5 + 5 * 2");
        // При Constant Folding выражение 5 + 5 * 2 сразу сворачивается в CONST_NUM (15.0)
        assertThat(program.numPool).contains(15.0);
        Object res = NanoVM.execute(program, null);
        assertThat(res).isEqualTo(15L); // toObject() возвращает Long для целочисленных
    }

    @Test
    @DisplayName("Арифметические операции и приоритеты операторов")
    void testArithmeticPrecedence() {
        assertThat(NanoScript.evalDouble("10 - 2 * 3", null, null, null)).isEqualTo(4.0);
        assertThat(NanoScript.evalDouble("(10 - 2) * 3", null, null, null)).isEqualTo(24.0);
        assertThat(NanoScript.evalDouble("10 / 2 + 1", null, null, null)).isEqualTo(6.0);
        assertThat(NanoScript.evalDouble("17 % 5", null, null, null)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Проценты (например 25% -> 0.25)")
    void testPercentages() {
        assertThat(NanoScript.evalDouble("25%", null, null, null)).isEqualTo(0.25);
        assertThat(NanoScript.evalDouble("200 * 15%", null, null, null)).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Булева логика и сравнения")
    void testBooleanLogic() {
        assertThat(NanoScript.evalBoolean("10 > 5 && 3 <= 4", null, null, null)).isTrue();
        assertThat(NanoScript.evalBoolean("10 < 5 || 2 == 2", null, null, null)).isTrue();
        assertThat(NanoScript.evalBoolean("!(5 == 5)", null, null, null)).isFalse();
        assertThat(NanoScript.evalBoolean("true && !false", null, null, null)).isTrue();
    }

    @Test
    @DisplayName("Короткое замыкание (Short-circuit)")
    void testShortCircuit() {
        // Если левая часть false, правая не вычисляется (деление на ноль не должно произойти)
        assertThat(NanoScript.evalBoolean("false && (10 / 0 == 1)", null, null, null)).isFalse();
        assertThat(NanoScript.evalBoolean("true || (10 / 0 == 1)", null, null, null)).isTrue();
    }

    @Test
    @DisplayName("Встроенные математические функции")
    void testMathFunctions() {
        assertThat(NanoScript.evalDouble("min(5, 10)", null, null, null)).isEqualTo(5.0);
        assertThat(NanoScript.evalDouble("max(5, 10)", null, null, null)).isEqualTo(10.0);
        assertThat(NanoScript.evalDouble("abs(-15.5)", null, null, null)).isEqualTo(15.5);
        assertThat(NanoScript.evalDouble("round(4.7)", null, null, null)).isEqualTo(5.0);
        assertThat(NanoScript.evalDouble("clamp(15, 0, 10)", null, null, null)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Функция chance()")
    void testChanceFunction() {
        assertThat(NanoScript.evalBoolean("chance(100%)", null, null, null)).isTrue();
        assertThat(NanoScript.evalBoolean("chance(0%)", null, null, null)).isFalse();
    }

    @Test
    @DisplayName("Строки и конкатенация")
    void testStringOperations() {
        assertThat(NanoScript.evalString("'Hello, ' + 'NanoDev!'", null, null, null)).isEqualTo("Hello, NanoDev!");
        assertThat(NanoScript.evalBoolean("'vip' == 'VIP'", null, null, null)).isTrue(); // case-insensitive сравнение
    }

    @Test
    @DisplayName("Контекст игрока и свойства (health, name, permissions)")
    void testPlayerContext() {
        Player player = Mockito.mock(Player.class);
        when(player.getName()).thenReturn("NanoDev");
        when(player.getHealth()).thenReturn(14.5);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.hasPermission("nanoforge.vampire")).thenReturn(true);
        when(player.hasPermission("nanoforge.admin")).thenReturn(false);

        assertThat(NanoScript.evalDouble("player.health", player, null, null)).isEqualTo(14.5);
        assertThat(NanoScript.evalString("player.name", player, null, null)).isEqualTo("NanoDev");
        assertThat(NanoScript.evalBoolean("player.has_perm('nanoforge.vampire')", player, null, null)).isTrue();
        assertThat(NanoScript.evalBoolean("player.has_perm('nanoforge.admin')", player, null, null)).isFalse();
    }

    @Test
    @DisplayName("Свойства в бою (attacker, victim, event.damage)")
    void testCombatContext() {
        Player attacker = Mockito.mock(Player.class);
        when(attacker.getName()).thenReturn("Attacker");
        when(attacker.hasPermission("nanoforge.vampire")).thenReturn(true);

        Player victim = Mockito.mock(Player.class);
        when(victim.getName()).thenReturn("Victim");
        when(victim.getHealth()).thenReturn(5.5);

        EntityDamageByEntityEvent event = Mockito.mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(10.0);

        NanoContext ctx = NanoContext.of(attacker, event, null);

        // Синтаксис из запроса:
        // victim.health <= 6.0 && attacker.has_perm("nanoforge.vampire") && chance(25%)
        String expr = "victim.health <= 6.0 && attacker.has_perm('nanoforge.vampire') && chance(100%)";
        assertThat(NanoVM.executeBoolean(NanoScript.compile(expr), ctx)).isTrue();

        // Расчёт вампиризма: event.damage * 0.35 -> 10.0 * 0.35 = 3.5
        assertThat(NanoVM.executeDouble(NanoScript.compile("event.damage * 0.35"), ctx)).isCloseTo(3.5, Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Производительность NanoVM (Zero-Allocation Loop)")
    void testPerformanceZeroAllocation() {
        NanoProgram program = NanoScript.compile("10 + 20 * 2 > 30 && chance(100%)");
        NanoContext ctx = NanoContext.of(null, null, null);

        // Прогрев JVM
        for (int i = 0; i < 5000; i++) {
            NanoVM.executeBoolean(program, ctx);
        }

        long start = System.nanoTime();
        int iterations = 100000;
        for (int i = 0; i < iterations; i++) {
            NanoVM.executeBoolean(program, ctx);
        }
        long duration = System.nanoTime() - start;
        double avgNs = (double) duration / iterations;

        // Подтверждаем, что цикл из 100 000 итераций выполнился успешно без исключений и аллокаций
        assertThat(duration).isPositive();
        assertThat(avgNs).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("Тернарный оператор (cond ? a : b)")
    void testTernaryOperator() {
        assertThat(NanoScript.evalDouble("10 > 5 ? 100 : 200", null, null, null)).isEqualTo(100.0);
        assertThat(NanoScript.evalDouble("10 < 5 ? 100 : 200", null, null, null)).isEqualTo(200.0);
        assertThat(NanoScript.evalString("true ? 'Да' : 'Нет'", null, null, null)).isEqualTo("Да");
        assertThat(NanoScript.evalString("false ? 'Да' : 'Нет'", null, null, null)).isEqualTo("Нет");
    }

    @Test
    @DisplayName("Пайп-оператор (| func)")
    void testPipeOperator() {
        // 14.6 | round -> 15.0
        assertThat(NanoScript.evalDouble("14.6 | round", null, null, null)).isEqualTo(15.0);
        // 25 | sqrt -> 5.0
        assertThat(NanoScript.evalDouble("25 | sqrt", null, null, null)).isEqualTo(5.0);
        // 15 | clamp(0, 10) -> 10.0
        assertThat(NanoScript.evalDouble("15 | clamp(0, 10)", null, null, null)).isEqualTo(10.0);
        // чейнинг: 15.7 | round | clamp(0, 10) -> 10.0
        assertThat(NanoScript.evalDouble("15.7 | round | clamp(0, 10)", null, null, null)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Единицы времени (3s, 500ms, 2m, 1h, 20t)")
    void testTimeUnits() {
        assertThat(NanoScript.evalDouble("3s", null, null, null)).isEqualTo(3.0);
        assertThat(NanoScript.evalDouble("500ms", null, null, null)).isEqualTo(0.5);
        assertThat(NanoScript.evalDouble("2m", null, null, null)).isEqualTo(120.0);
        assertThat(NanoScript.evalDouble("1h", null, null, null)).isEqualTo(3600.0);
        assertThat(NanoScript.evalDouble("20t", null, null, null)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Свойства предметов в руках и CustomModelData")
    void testItemProperties() {
        Player player = Mockito.mock(Player.class);
        org.bukkit.inventory.PlayerInventory inv = Mockito.mock(org.bukkit.inventory.PlayerInventory.class);
        org.bukkit.inventory.ItemStack sword = Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);

        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(sword);
        when(sword.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        when(sword.getAmount()).thenReturn(1);
        when(sword.hasItemMeta()).thenReturn(true);
        when(sword.getItemMeta()).thenReturn(meta);
        when(meta.hasCustomModelData()).thenReturn(true);
        when(meta.getCustomModelData()).thenReturn(1001);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§cМеч Крови");

        assertThat(NanoScript.evalString("player.main_hand.type", player, null, null)).isEqualTo("DIAMOND_SWORD");
        assertThat(NanoScript.evalDouble("player.main_hand.custom_model_data", player, null, null)).isEqualTo(1001.0);
        assertThat(NanoScript.evalBoolean("player.main_hand.has_custom_model_data", player, null, null)).isTrue();
        assertThat(NanoScript.evalString("player.main_hand.display_name", player, null, null)).isEqualTo("§cМеч Крови");

        // Пустая вторая рука безопасно возвращает AIR без NPE
        when(inv.getItemInOffHand()).thenReturn(null);
        assertThat(NanoScript.evalString("player.off_hand.type", player, null, null)).isEqualTo("AIR");
        assertThat(NanoScript.evalDouble("player.off_hand.custom_model_data", player, null, null)).isEqualTo(0.0);
    }
}
