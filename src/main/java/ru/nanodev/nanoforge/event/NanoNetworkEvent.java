package ru.nanodev.nanoforge.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * Событие входящего сетевого пакета из кластера (NanoMesh / Velocity / Bungee / Redis).
 */
public class NanoNetworkEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String senderServer;
    private final String action;
    private final String payload;
    private final Map<String, Object> data;

    public NanoNetworkEvent(String senderServer, String action, String payload, Map<String, Object> data) {
        super(true); // асинхронное событие
        this.senderServer = senderServer != null ? senderServer : "";
        this.action = action != null ? action : "";
        this.payload = payload != null ? payload : "";
        this.data = data != null ? data : Collections.emptyMap();
    }

    public String getSenderServer() {
        return senderServer;
    }

    public String getAction() {
        return action;
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
