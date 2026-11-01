package ru.nanodev.nanoforge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ru.nanodev.nanoforge.debug.AddonProfiler;
import ru.nanodev.nanoforge.debug.NanoLens;
import ru.nanodev.nanoforge.dungeon.DungeonInstance;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.event.NanoNetworkEvent;
import ru.nanodev.nanoforge.script.BukkitBinding;
import ru.nanodev.nanoforge.script.NanoContext;
import ru.nanodev.nanoforge.script.NanoProgram;
import ru.nanodev.nanoforge.script.NanoScript;
import ru.nanodev.nanoforge.script.jit.CompiledNanoExpression;
import ru.nanodev.nanoforge.script.jit.NanoJitEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NanoForge193FeaturesTest {

    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pm);
        NanoScript.clearCache();
        AddonProfiler.reset();
        NanoLens.clear();
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void testNanoJitEngineDirectCompilation() {
        // Числовая константа
        NanoProgram numProg = NanoScript.compile("123.45");
        CompiledNanoExpression jitNum = NanoJitEngine.compile(numProg);
        assertThat(jitNum.evalDouble(null)).isEqualTo(123.45);

        // Булева константа
        NanoProgram boolProg = NanoScript.compile("true");
        CompiledNanoExpression jitBool = NanoJitEngine.compile(boolProg);
        assertThat(jitBool.evalBoolean(null)).isTrue();

        // Переменная + число
        NanoProgram varProg = NanoScript.compile("mana + 50");
        CompiledNanoExpression jitVar = NanoJitEngine.compile(varProg);
        Map<String, Object> vars = new HashMap<>();
        vars.put("mana", 75.0);
        NanoContext ctx = new NanoContext(null, null, null, null, null, null, vars);
        assertThat(jitVar.evalDouble(ctx)).isEqualTo(125.0);
    }

    @Test
    void testAdaptiveJitThresholdInNanoScript() {
        String expr = "10 + 20 * 2";
        assertThat(NanoScript.isJitCompiled(expr)).isFalse();

        // Вызываем 25 раз, чтобы превысить JIT_THRESHOLD (20)
        for (int i = 0; i < 25; i++) {
            double res = NanoScript.evalDouble(expr, null, null, null);
            assertThat(res).isEqualTo(50.0);
        }

        // Теперь выражение должно быть скомпилировано в JIT_CACHE
        assertThat(NanoScript.isJitCompiled(expr)).isTrue();
        assertThat(NanoScript.evalDouble(expr, null, null, null)).isEqualTo(50.0);
    }

    @Test
    void testNanoNetworkEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("clan", "WineSquad");
        data.put("points", 1500);

        NanoNetworkEvent ev = new NanoNetworkEvent("grief-1", "clan_update", "payload_test", data);
        assertThat(ev.getSenderServer()).isEqualTo("grief-1");
        assertThat(ev.getAction()).isEqualTo("clan_update");
        assertThat(ev.getPayload()).isEqualTo("payload_test");
        assertThat(ev.get("clan")).isEqualTo("WineSquad");
        assertThat(ev.get("points")).isEqualTo(1500);

        // BukkitBinding properties
        assertThat(BukkitBinding.getProperty(ev, "sender")).isEqualTo("grief-1");
        assertThat(BukkitBinding.getProperty(ev, "action")).isEqualTo("clan_update");
        assertThat(BukkitBinding.getProperty(ev, "clan")).isEqualTo("WineSquad");
    }

    @Test
    void testDungeonInstance() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        Location loc = new Location(world, 10, 64, 10);
        Block block = mock(Block.class);
        when(world.getBlockAt(loc)).thenReturn(block);
        when(block.getType()).thenReturn(Material.DIRT);

        DungeonInstance inst = new DungeonInstance("mine_1", "box_5x5", loc, 60000L);
        assertThat(inst.getId()).isEqualTo("mine_1");
        assertThat(inst.getTemplate()).isEqualTo("box_5x5");
        assertThat(inst.isExpired()).isFalse();

        inst.recordOriginalBlock(loc, Material.DIRT);
        assertThat(inst.getOriginalBlocks()).containsKey(loc);

        inst.restore();
        assertThat(inst.isActive()).isFalse();
    }

    @Test
    void testAddonProfiler() {
        AddonProfiler.reset();
        AddonProfiler.record("my_addon", "message", 150_000L); // 150 µs
        AddonProfiler.record("my_addon", "message", 250_000L); // 250 µs

        List<String> dump = AddonProfiler.dumpProfile();
        assertThat(dump).isNotEmpty();
        assertThat(dump.stream().anyMatch(l -> l.contains("my_addon::message"))).isTrue();
    }

    @Test
    void testNanoLensInspectToggle() {
        Player p = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(uuid);

        assertThat(NanoLens.isInspecting(p)).isFalse();
        boolean now = NanoLens.toggleInspect(p);
        assertThat(now).isTrue();
        assertThat(NanoLens.isInspecting(p)).isTrue();
        boolean toggledOff = NanoLens.toggleInspect(p);
        assertThat(toggledOff).isFalse();
        assertThat(NanoLens.isInspecting(p)).isFalse();
    }

    @Test
    void testNormalizeAction193Actions() {
        // network_broadcast
        Map<String, Object> act1 = ActionRunner.normalizeAction("network_broadcast: Всем привет из Хаба!");
        assertThat(act1).isNotNull();
        assertThat(act1.get("type")).isEqualTo("network_broadcast");
        assertThat(act1.get("message")).isEqualTo("Всем привет из Хаба!");

        // network_event
        Map<String, Object> act2 = ActionRunner.normalizeAction("network_event: global_boss_slain");
        assertThat(act2).isNotNull();
        assertThat(act2.get("type")).isEqualTo("network_event");
        assertThat(act2.get("event")).isEqualTo("global_boss_slain");

        // dungeon_spawn
        Map<String, Object> act3 = ActionRunner.normalizeAction("dungeon_spawn: auto_mine, ore_cube, 15m");
        assertThat(act3).isNotNull();
        assertThat(act3.get("type")).isEqualTo("dungeon_spawn");
        assertThat(act3.get("id")).isEqualTo("auto_mine");
        assertThat(act3.get("template")).isEqualTo("ore_cube");
        assertThat(act3.get("duration")).isEqualTo("15m");

        // dungeon_reset
        Map<String, Object> act4 = ActionRunner.normalizeAction("dungeon_reset: auto_mine");
        assertThat(act4.get("type")).isEqualTo("dungeon_reset");
        assertThat(act4.get("id")).isEqualTo("auto_mine");

        // dungeon_clear
        Map<String, Object> act5 = ActionRunner.normalizeAction("dungeon_clear: auto_mine");
        assertThat(act5.get("type")).isEqualTo("dungeon_clear");
        assertThat(act5.get("id")).isEqualTo("auto_mine");
    }
}
