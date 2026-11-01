package ru.nanodev.nanoforge.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * Виртуальное событие вебхука для вызова в аддонах (trigger: WEBHOOK_RECEIVED).
 */
public class NanoWebhookEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String payload;
    private final Map<String, Object> jsonMap;

    public NanoWebhookEvent(String payload, Map<String, Object> jsonMap) {
        super(true); // асинхронное событие
        this.payload = payload != null ? payload : "";
        this.jsonMap = jsonMap != null ? jsonMap : Collections.emptyMap();
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, Object> getJsonMap() {
        return jsonMap;
    }

    public Object get(String key) {
        return jsonMap.get(key);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
