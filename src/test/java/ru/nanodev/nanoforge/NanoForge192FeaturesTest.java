package ru.nanodev.nanoforge;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ru.nanodev.nanoforge.bus.NanoCustomEvent;
import ru.nanodev.nanoforge.dynamic.DynamicListener;
import ru.nanodev.nanoforge.engine.ActionRunner;
import ru.nanodev.nanoforge.event.NanoWebhookEvent;
import ru.nanodev.nanoforge.script.BukkitBinding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NanoForge192FeaturesTest {

    private MockedStatic<Bukkit> bukkitMock;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void testNormalizeActionRaytrace() {
        Map<String, Object> act = ActionRunner.normalizeAction("raytrace: 30 FLAME 0.2");
        assertThat(act).isNotNull();
        assertThat(act.get("type")).isEqualTo("raytrace");
        assertThat(act.get("distance")).isEqualTo("30");
        assertThat(act.get("particle")).isEqualTo("FLAME");
        assertThat(act.get("step")).isEqualTo("0.2");
    }

    @Test
    void testNormalizeActionHologram() {
        Map<String, Object> act = ActionRunner.normalizeAction("hologram: &a+100 HP, 3s, 0.5, 0.04");
        assertThat(act).isNotNull();
        assertThat(act.get("type")).isEqualTo("hologram");
        assertThat(act.get("text")).isEqualTo("&a+100 HP");
        assertThat(act.get("duration")).isEqualTo("3s");
        assertThat(act.get("offset_y")).isEqualTo("0.5");
        assertThat(act.get("velocity_y")).isEqualTo("0.04");

        Map<String, Object> actWithTarget = ActionRunner.normalizeAction("hologram: victim, &c-50 HP, 2s, 0.05");
        assertThat(actWithTarget).isNotNull();
        assertThat(actWithTarget.get("type")).isEqualTo("hologram");
        assertThat(actWithTarget.get("target")).isEqualTo("victim");
        assertThat(actWithTarget.get("text")).isEqualTo("&c-50 HP");
        assertThat(actWithTarget.get("duration")).isEqualTo("2s");
        assertThat(actWithTarget.get("velocity_y")).isEqualTo("0.05");
    }

    @Test
    void testNormalizeActionTriggerEvent() {
        Map<String, Object> act = ActionRunner.normalizeAction("trigger_event: boss_defeat");
        assertThat(act).isNotNull();
        assertThat(act.get("type")).isEqualTo("trigger_event");
        assertThat(act.get("event")).isEqualTo("boss_defeat");
    }

    @Test
    void testNormalizeActionPdc() {
        Map<String, Object> act = ActionRunner.normalizeAction("pdc: player, mana, 100");
        assertThat(act).isNotNull();
        assertThat(act.get("type")).isEqualTo("pdc");
        assertThat(act.get("target")).isEqualTo("player");
        assertThat(act.get("key")).isEqualTo("mana");
        assertThat(act.get("value")).isEqualTo("100");
    }

    @Test
    void testParseDurationTicks() {
        assertThat(ActionRunner.parseDurationTicks("500ms", 20L)).isEqualTo(10L);
        assertThat(ActionRunner.parseDurationTicks("3s", 20L)).isEqualTo(60L);
        assertThat(ActionRunner.parseDurationTicks("2m", 20L)).isEqualTo(2400L);
        assertThat(ActionRunner.parseDurationTicks("1h", 20L)).isEqualTo(72000L);
        assertThat(ActionRunner.parseDurationTicks("45t", 20L)).isEqualTo(45L);
        assertThat(ActionRunner.parseDurationTicks("30", 20L)).isEqualTo(30L);
    }

    @Test
    void testNanoCustomEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("reward", 500);
        data.put("killer", "NanoDev");

        NanoCustomEvent ev = new NanoCustomEvent("dragon_slain", data);
        assertThat(ev.getName()).isEqualTo("dragon_slain");
        assertThat(ev.get("reward")).isEqualTo(500);
        assertThat(ev.get("killer")).isEqualTo("NanoDev");

        // Test fire
        NanoCustomEvent.fire("dragon_slain", data);
        verify(pluginManager, times(1)).callEvent(any(NanoCustomEvent.class));
    }

    @Test
    void testNanoWebhookEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "payment_received");
        data.put("amount", 199.0);

        NanoWebhookEvent we = new NanoWebhookEvent("{\"action\":\"payment_received\",\"amount\":199.0}", data);
        assertThat(we.isAsynchronous()).isTrue();
        assertThat(we.getPayload()).contains("payment_received");
        assertThat(we.get("action")).isEqualTo("payment_received");
        assertThat(we.get("amount")).isEqualTo(199.0);
    }

    @Test
    void testDynamicListenerResolveEventClass() {
        assertThat(DynamicListener.resolveEventClass("custom")).isEqualTo(NanoCustomEvent.class);
        assertThat(DynamicListener.resolveEventClass("NanoCustomEvent")).isEqualTo(NanoCustomEvent.class);
        assertThat(DynamicListener.resolveEventClass("webhook")).isEqualTo(NanoWebhookEvent.class);
        assertThat(DynamicListener.resolveEventClass("NanoWebhookEvent")).isEqualTo(NanoWebhookEvent.class);
    }

    @Test
    void testBukkitBindingWithCustomEvents() {
        Map<String, Object> data = new HashMap<>();
        data.put("boss", "WITHER");
        data.put("score", 999);
        NanoCustomEvent ev = new NanoCustomEvent("boss_kill", data);

        assertThat(BukkitBinding.getProperty(ev, "name")).isEqualTo("boss_kill");
        assertThat(BukkitBinding.getProperty(ev, "event_name")).isEqualTo("boss_kill");
        assertThat(BukkitBinding.getProperty(ev, "boss")).isEqualTo("WITHER");
        assertThat(BukkitBinding.getProperty(ev, "score")).isEqualTo(999);

        // Global trigger_event
        Object res = BukkitBinding.invokeGlobal("trigger_event", new Object[]{"custom_quest_done", data}, null);
        assertThat(res).isEqualTo(true);
        verify(pluginManager, atLeastOnce()).callEvent(any(NanoCustomEvent.class));
    }
}
