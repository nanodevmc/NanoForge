package ru.nanodev.nanoforge.script;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.nanodev.nanoforge.integration.LuckPermsBridge;
import ru.nanodev.nanoforge.integration.VaultBridge;
import ru.nanodev.nanoforge.integration.WorldGuardBridge;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast-path binding к Bukkit/Spigot объектам и математическим функциям.
 * Прямой доступ без медленной обобщённой рефлексии.
 */
public final class BukkitBinding {

    private BukkitBinding() {}

    public static Object getProperty(Object target, String prop) {
        if (target == null || prop == null) return null;
        String p = prop.toLowerCase();

        // Свойства Entity / Damageable
        if (target instanceof Entity) {
            Entity entity = (Entity) target;
            switch (p) {
                case "name": return entity.getName();
                case "uuid": return entity.getUniqueId().toString();
                case "type": return entity.getType().name();
                case "world": return entity.getWorld().getName();
                case "x": return entity.getLocation().getX();
                case "y": return entity.getLocation().getY();
                case "z": return entity.getLocation().getZ();
                case "yaw": return (double) entity.getLocation().getYaw();
                case "pitch": return (double) entity.getLocation().getPitch();
                case "is_dead": return entity.isDead();
                case "is_valid": return entity.isValid();
                default: break;
            }

            if (target instanceof Damageable) {
                Damageable d = (Damageable) target;
                if ("health".equals(p)) {
                    return d.getHealth();
                } else if ("max_health".equals(p)) {
                    if (target instanceof org.bukkit.attribute.Attributable) {
                        try {
                            org.bukkit.attribute.AttributeInstance attr = ((org.bukkit.attribute.Attributable) target).getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            if (attr != null) return attr.getValue();
                        } catch (Throwable ignored) {}
                    }
                    return d.getMaxHealth();
                }
            }

            if (target instanceof Player) {
                Player player = (Player) target;
                switch (p) {
                    case "food": return (double) player.getFoodLevel();
                    case "level": return (double) player.getLevel();
                    case "exp": return (double) player.getExp();
                    case "balance": return VaultBridge.getBalance(player);
                    case "is_sneaking": return player.isSneaking();
                    case "is_flying": return player.isFlying();
                    case "is_sprinting": return player.isSprinting();
                    case "is_gliding": return player.isGliding();
                    case "is_swimming": return player.isSwimming();
                    case "is_op": return player.isOp();
                    case "gamemode": return player.getGameMode().name();
                    case "main_hand": return getItemOrAir(player.getInventory().getItemInMainHand());
                    case "off_hand": return getItemOrAir(player.getInventory().getItemInOffHand());
                    case "world_time": return player.getWorld() != null ? (double) player.getWorld().getTime() : 0.0;
                    case "is_day": {
                        if (player.getWorld() == null) return true;
                        long time = player.getWorld().getTime();
                        return time < 12300 || time > 23850;
                    }
                    case "is_night": {
                        if (player.getWorld() == null) return false;
                        long time = player.getWorld().getTime();
                        return time >= 12300 && time <= 23850;
                    }
                    case "has_storm": return player.getWorld() != null && player.getWorld().hasStorm();
                    case "is_thundering": return player.getWorld() != null && player.getWorld().isThundering();
                    default: break;
                }
            }
        }

        // Свойства Event
        if (target instanceof Event) {
            Event ev = (Event) target;
            if ("name".equals(p)) {
                if (target instanceof ru.nanodev.nanoforge.bus.NanoCustomEvent) {
                    return ((ru.nanodev.nanoforge.bus.NanoCustomEvent) target).getName();
                }
                return ev.getEventName();
            }
            if ("cancelled".equals(p) || "is_cancelled".equals(p)) {
                return ev instanceof Cancellable && ((Cancellable) ev).isCancelled();
            }
            if (target instanceof EntityDamageEvent) {
                EntityDamageEvent ede = (EntityDamageEvent) target;
                if ("damage".equals(p)) return ede.getDamage();
                if ("final_damage".equals(p)) return ede.getFinalDamage();
                if ("cause".equals(p)) return ede.getCause().name();
            }
            if (target instanceof ru.nanodev.nanoforge.bus.NanoCustomEvent) {
                ru.nanodev.nanoforge.bus.NanoCustomEvent nce = (ru.nanodev.nanoforge.bus.NanoCustomEvent) target;
                if ("event_name".equals(p)) return nce.getName();
                Object val = nce.get(prop);
                if (val != null) return val;
            }
            if (target instanceof ru.nanodev.nanoforge.event.NanoWebhookEvent) {
                ru.nanodev.nanoforge.event.NanoWebhookEvent nwe = (ru.nanodev.nanoforge.event.NanoWebhookEvent) target;
                if ("payload".equals(p)) return nwe.getPayload();
                Object val = nwe.get(prop);
                if (val != null) return val;
            }
            if (target instanceof ru.nanodev.nanoforge.event.NanoNetworkEvent) {
                ru.nanodev.nanoforge.event.NanoNetworkEvent nne = (ru.nanodev.nanoforge.event.NanoNetworkEvent) target;
                if ("sender".equals(p) || "sender_server".equals(p) || "server".equals(p)) return nne.getSenderServer();
                if ("action".equals(p)) return nne.getAction();
                if ("payload".equals(p)) return nne.getPayload();
                Object val = nne.get(prop);
                if (val != null) return val;
            }
        }

        // Свойства пустой руки (AIR)
        if ("AIR".equals(target)) {
            switch (p) {
                case "type":
                case "material":
                    return "AIR";
                case "amount":
                case "custom_model_data":
                case "cmd":
                    return 0.0;
                case "has_custom_model_data":
                case "has_cmd":
                case "has_display_name":
                case "has_lore":
                    return false;
                case "display_name":
                case "name":
                    return "";
                default: break;
            }
        }

        // Свойства ItemStack (предмет в руке)
        if (target instanceof org.bukkit.inventory.ItemStack) {
            org.bukkit.inventory.ItemStack item = (org.bukkit.inventory.ItemStack) target;
            switch (p) {
                case "type":
                case "material":
                    return item.getType().name();
                case "amount":
                    return (double) item.getAmount();
                case "has_custom_model_data":
                case "has_cmd": {
                    try {
                        if (!item.hasItemMeta()) return false;
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        return meta != null && meta.hasCustomModelData();
                    } catch (Throwable t) {
                        return false;
                    }
                }
                case "custom_model_data":
                case "cmd": {
                    try {
                        if (!item.hasItemMeta()) return 0.0;
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        return (meta != null && meta.hasCustomModelData()) ? (double) meta.getCustomModelData() : 0.0;
                    } catch (Throwable t) {
                        return 0.0;
                    }
                }
                case "display_name":
                case "name": {
                    try {
                        if (!item.hasItemMeta()) return "";
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        return (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : "";
                    } catch (Throwable t) {
                        return "";
                    }
                }
                case "has_display_name": {
                    try {
                        if (!item.hasItemMeta()) return false;
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        return meta != null && meta.hasDisplayName();
                    } catch (Throwable t) {
                        return false;
                    }
                }
                case "has_lore": {
                    try {
                        if (!item.hasItemMeta()) return false;
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        return meta != null && meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty();
                    } catch (Throwable t) {
                        return false;
                    }
                }
                default: break;
            }
        }

        return null;
    }

    public static Object invokeMethod(Object target, String method, Object[] args, NanoContext ctx) {
        if (target == null || method == null) return null;
        String m = method.toLowerCase();

        if (target instanceof Player) {
            Player p = (Player) target;
            if (("has_perm".equals(m) || "has_permission".equals(m)) && args.length >= 1) {
                return p.hasPermission(String.valueOf(args[0]));
            }
            if ("has_tag".equals(m) && args.length >= 1) {
                return p.getScoreboardTags().contains(String.valueOf(args[0]));
            }
            if ("in_region".equals(m) && args.length >= 1) {
                return WorldGuardBridge.isInRegion(p, String.valueOf(args[0]));
            }
            if ("in_group".equals(m) && args.length >= 1) {
                return LuckPermsBridge.isInGroup(p, String.valueOf(args[0]));
            }
            if (("has_item".equals(m) || "has_material".equals(m)) && args.length >= 1) {
                String mat = String.valueOf(args[0]);
                int amount = args.length >= 2 ? (int) toDouble(args[1]) : 1;
                return hasPlayerItem(p, mat, amount);
            }
        }

        if (target instanceof Entity) {
            Entity e = (Entity) target;
            if ("distance_to".equals(m) && args.length >= 1 && args[0] instanceof Entity) {
                return e.getLocation().distance(((Entity) args[0]).getLocation());
            }
            if ("pdc".equals(m) && args.length >= 1) {
                return getPdcValue(e, String.valueOf(args[0]));
            }
        }

        if (target instanceof org.bukkit.inventory.ItemStack) {
            org.bukkit.inventory.ItemStack is = (org.bukkit.inventory.ItemStack) target;
            if ("pdc".equals(m) && args.length >= 1) {
                return getItemPdcValue(is, String.valueOf(args[0]));
            }
        }

        return null;
    }

    public static Object invokeGlobal(String funcName, Object[] args, NanoContext ctx) {
        if (funcName == null) return null;
        String fn = funcName.toLowerCase();

        switch (fn) {
            case "chance": {
                if (args.length == 0) return false;
                double prob = toDouble(args[0]);
                if (prob > 1.0) prob = prob / 100.0;
                return ThreadLocalRandom.current().nextDouble() < prob;
            }
            case "has_perm":
            case "has_permission": {
                if (args.length >= 1 && ctx != null && ctx.getPlayer() != null) {
                    return ctx.getPlayer().hasPermission(String.valueOf(args[0]));
                }
                return false;
            }
            case "balance": {
                if (ctx != null && ctx.getPlayer() != null) {
                    return VaultBridge.getBalance(ctx.getPlayer());
                }
                return 0.0;
            }
            case "min": {
                if (args.length >= 2) return Math.min(toDouble(args[0]), toDouble(args[1]));
                return args.length == 1 ? toDouble(args[0]) : 0.0;
            }
            case "max": {
                if (args.length >= 2) return Math.max(toDouble(args[0]), toDouble(args[1]));
                return args.length == 1 ? toDouble(args[0]) : 0.0;
            }
            case "abs": {
                if (args.length >= 1) return Math.abs(toDouble(args[0]));
                return 0.0;
            }
            case "round": {
                if (args.length >= 1) return (double) Math.round(toDouble(args[0]));
                return 0.0;
            }
            case "floor": {
                if (args.length >= 1) return Math.floor(toDouble(args[0]));
                return 0.0;
            }
            case "ceil": {
                if (args.length >= 1) return Math.ceil(toDouble(args[0]));
                return 0.0;
            }
            case "sqrt": {
                if (args.length >= 1) return Math.sqrt(toDouble(args[0]));
                return 0.0;
            }
            case "clamp": {
                if (args.length >= 3) {
                    double v = toDouble(args[0]);
                    double min = toDouble(args[1]);
                    double max = toDouble(args[2]);
                    return Math.max(min, Math.min(max, v));
                }
                return 0.0;
            }
            case "random": {
                double min = args.length >= 1 ? toDouble(args[0]) : 0.0;
                double max = args.length >= 2 ? toDouble(args[1]) : 1.0;
                if (min >= max) return min;
                return ThreadLocalRandom.current().nextDouble(min, max);
            }
            case "var": {
                if (args.length >= 1 && ctx != null && ctx.getAddon() != null && ctx.getAddon().getStorage() != null) {
                    String def = args.length >= 2 ? String.valueOf(args[1]) : "0";
                    return ctx.getAddon().getStorage().getVar(ctx.getPlayer(), String.valueOf(args[0]), def);
                }
                return "0";
            }
            case "global_var": {
                if (args.length >= 1 && ctx != null && ctx.getAddon() != null && ctx.getAddon().getStorage() != null) {
                    String def = args.length >= 2 ? String.valueOf(args[1]) : "0";
                    return ctx.getAddon().getStorage().getGlobalVar(String.valueOf(args[0]), def);
                }
                return "0";
            }
            case "online_count":
            case "online": {
                try {
                    return (double) org.bukkit.Bukkit.getOnlinePlayers().size();
                } catch (Throwable t) {
                    return 0.0;
                }
            }
            case "cooldown": {
                // cooldown('name', 3.5s)
                if (args.length >= 2 && ctx != null && ctx.getPlayer() != null && ctx.getAddon() != null && ctx.getAddon().getStorage() != null) {
                    String cdKey = String.valueOf(args[0]);
                    long sec = (long) Math.ceil(toDouble(args[1]));
                    long remaining = ctx.getAddon().getStorage().remainingCooldownSeconds(ctx.getPlayer(), cdKey, sec);
                    if (remaining <= 0) {
                        ctx.getAddon().getStorage().markCooldown(ctx.getPlayer(), cdKey);
                        return true;
                    }
                    return false;
                }
                return true;
            }
            case "has_item": {
                if (args.length >= 1 && ctx != null && ctx.getPlayer() != null) {
                    String mat = String.valueOf(args[0]);
                    int amount = args.length >= 2 ? (int) toDouble(args[1]) : 1;
                    return hasPlayerItem(ctx.getPlayer(), mat, amount);
                }
                return false;
            }
            case "pdc": {
                if (args.length >= 2 && args[0] instanceof Entity) {
                    return getPdcValue((Entity) args[0], String.valueOf(args[1]));
                }
                if (args.length >= 1 && ctx != null && ctx.getPlayer() != null) {
                    return getPdcValue(ctx.getPlayer(), String.valueOf(args[0]));
                }
                return null;
            }
            case "trigger_event":
            case "fire_event": {
                if (args.length >= 1) {
                    String evName = String.valueOf(args[0]);
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    if (args.length >= 2 && args[1] instanceof java.util.Map) {
                        data.putAll((java.util.Map<String, Object>) args[1]);
                    }
                    ru.nanodev.nanoforge.bus.NanoCustomEvent.fire(evName, data);
                    return true;
                }
                return false;
            }
            default:
                return null;
        }
    }

    public static double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof Boolean) return ((Boolean) obj) ? 1.0 : 0.0;
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static Object getItemOrAir(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return "AIR";
        }
        return item;
    }

    private static boolean hasPlayerItem(Player player, String materialName, int count) {
        if (player == null || materialName == null) return false;
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(materialName.toUpperCase());
        if (mat == null) return false;
        int found = 0;
        for (org.bukkit.inventory.ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == mat) {
                found += is.getAmount();
                if (found >= count) return true;
            }
        }
        return false;
    }

    public static Object getPdcValue(Entity entity, String key) {
        if (entity == null || key == null) return null;
        try {
            org.bukkit.NamespacedKey nKey = new org.bukkit.NamespacedKey(ru.nanodev.nanoforge.NanoForgePlugin.get(), key.toLowerCase());
            org.bukkit.persistence.PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                return pdc.get(nKey, org.bukkit.persistence.PersistentDataType.STRING);
            }
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                return pdc.get(nKey, org.bukkit.persistence.PersistentDataType.DOUBLE);
            }
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                return (double) pdc.get(nKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object getItemPdcValue(org.bukkit.inventory.ItemStack item, String key) {
        if (item == null || key == null || !item.hasItemMeta()) return null;
        try {
            org.bukkit.NamespacedKey nKey = new org.bukkit.NamespacedKey(ru.nanodev.nanoforge.NanoForgePlugin.get(), key.toLowerCase());
            org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                return pdc.get(nKey, org.bukkit.persistence.PersistentDataType.STRING);
            }
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                return pdc.get(nKey, org.bukkit.persistence.PersistentDataType.DOUBLE);
            }
            if (pdc.has(nKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                return (double) pdc.get(nKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
