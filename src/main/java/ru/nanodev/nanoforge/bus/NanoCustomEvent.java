package ru.nanodev.nanoforge.bus;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * Межаддонная шина событий (Cross-Addon Event Bus).
 * Позволяет аддонам общаться между собой без связанности.
 */
public class NanoCustomEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String name;
    private final Map<String, Object> data;

    public NanoCustomEvent(String name, Map<String, Object> data) {
        this.name = name != null ? name.trim() : "";
        this.data = data != null ? data : Collections.emptyMap();
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public static void fire(String name, Map<String, Object> data) {
        Bukkit.getPluginManager().callEvent(new NanoCustomEvent(name, data));
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
