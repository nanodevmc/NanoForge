package ru.nanodev.nanoforge.cluster;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import ru.nanodev.nanoforge.NanoForgePlugin;
import ru.nanodev.nanoforge.bus.NanoCustomEvent;
import ru.nanodev.nanoforge.event.NanoNetworkEvent;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Высокопроизводительный менеджер сетевого кластера (NanoMesh).
 * Поддерживает гибридный режим: Plugin Messaging (Velocity/Bungee) + нативный легковесный Redis сокет.
 */
public final class ClusterManager implements PluginMessageListener {

    public static final String CHANNEL = "nanoforge:cluster";
    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static ClusterManager instance;

    private final NanoForgePlugin plugin;
    private final String serverName;
    private final boolean redisEnabled;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    private Thread redisWorker;
    private volatile boolean running = true;
    private Socket redisPubSocket;

    public ClusterManager(NanoForgePlugin plugin) {
        this.plugin = plugin;
        this.serverName = plugin.getConfig().getString("cluster.server-name", "server-1");
        this.redisEnabled = plugin.getConfig().getBoolean("cluster.redis.enabled", false);
        this.redisHost = plugin.getConfig().getString("cluster.redis.host", "localhost");
        this.redisPort = plugin.getConfig().getInt("cluster.redis.port", 6379);
        this.redisPassword = plugin.getConfig().getString("cluster.redis.password", "");
    }

    public static synchronized void init(NanoForgePlugin plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new ClusterManager(plugin);
        instance.start();
    }

    public static ClusterManager get() {
        return instance;
    }

    private void start() {
        try {
            // Регистрируем каналы Plugin Messaging
            Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
            LOG.info("[NanoMesh] Сетевой канал " + CHANNEL + " зарегистрирован.");
        } catch (Throwable t) {
            LOG.warning("[NanoMesh] Ошибка регистрации Plugin Messaging: " + t.getMessage());
        }

        if (redisEnabled) {
            startRedisSub();
        }
    }

    public void shutdown() {
        running = false;
        try {
            Bukkit.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
            Bukkit.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Throwable ignored) {}

        if (redisPubSocket != null) {
            try {
                redisPubSocket.close();
            } catch (Throwable ignored) {}
        }
    }

    public void sendBroadcast(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        sendPacket("broadcast", message, data);
    }

    public void sendEvent(String eventName, Map<String, Object> data) {
        sendPacket("event", eventName, data);
    }

    public void sendVarSync(String varName, String value) {
        Map<String, Object> data = new HashMap<>();
        data.put("var", varName);
        data.put("val", value);
        sendPacket("var_sync", varName + "=" + value, data);
    }

    public void sendPacket(String action, String payload, Map<String, Object> data) {
        String json = encodePacket(serverName, action, payload, data);

        // 1. Отправляем через BungeeCord / Velocity Plugin Messaging
        try {
            Player p = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
            if (p != null) {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                p.sendPluginMessage(plugin, CHANNEL, bytes);
            }
        } catch (Throwable ignored) {}

        // 2. Отправляем через Redis, если включен
        if (redisEnabled) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendRedisPublish(json));
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            handleIncomingPacket(json);
        } catch (Throwable t) {
            LOG.warning("[NanoMesh] Ошибка приёма пакета: " + t.getMessage());
        }
    }

    public void handleIncomingPacket(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            Map<String, Object> map = parseSimpleJson(json);
            String from = String.valueOf(map.getOrDefault("server", ""));
            if (serverName.equalsIgnoreCase(from)) {
                return; // игнорируем собственные сообщения
            }

            String action = String.valueOf(map.getOrDefault("action", ""));
            String payload = String.valueOf(map.getOrDefault("payload", ""));

            Map<String, Object> data = new HashMap<>();
            Object dataObj = map.get("data");
            if (dataObj instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) dataObj).entrySet()) {
                    data.put(String.valueOf(e.getKey()), e.getValue());
                }
            }

            // Вызываем Bukkit-событие NanoNetworkEvent
            Bukkit.getPluginManager().callEvent(new NanoNetworkEvent(from, action, payload, data));

            // Автоматическая обработка системных действий
            if ("broadcast".equalsIgnoreCase(action)) {
                String msg = String.valueOf(data.getOrDefault("message", payload));
                Bukkit.broadcastMessage(colorize(msg));
            } else if ("var_sync".equalsIgnoreCase(action)) {
                String varKey = String.valueOf(data.getOrDefault("var", ""));
                String varVal = String.valueOf(data.getOrDefault("val", ""));
                if (!varKey.isEmpty() && plugin.getAddonManager() != null) {
                    for (ru.nanodev.nanoforge.model.Addon addon : plugin.getAddonManager().getAddons()) {
                        addon.getStorage().setGlobalVar(varKey, varVal);
                    }
                }
            } else if ("event".equalsIgnoreCase(action)) {
                NanoCustomEvent.fire(payload, data);
            }
        } catch (Throwable ignored) {}
    }

    private void startRedisSub() {
        redisWorker = new Thread(() -> {
            while (running) {
                try (Socket subSocket = new Socket(redisHost, redisPort);
                     OutputStream out = subSocket.getOutputStream();
                     InputStream in = subSocket.getInputStream()) {

                    if (!redisPassword.isEmpty()) {
                        sendRespCommand(out, "AUTH", redisPassword);
                        readRespLine(in);
                    }

                    sendRespCommand(out, "SUBSCRIBE", CHANNEL);
                    readRespLine(in); // response to SUBSCRIBE

                    while (running && !subSocket.isClosed()) {
                        String type = readRespLine(in);
                        if (type != null && type.startsWith("*")) {
                            // RESP Array
                            String countStr = type.substring(1);
                            int count = Integer.parseInt(countStr);
                            String[] items = new String[count];
                            for (int i = 0; i < count; i++) {
                                readRespLine(in); // $length
                                items[i] = readRespLine(in); // value
                            }
                            if (count >= 3 && "message".equalsIgnoreCase(items[0])) {
                                String body = items[2];
                                handleIncomingPacket(body);
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (running) {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }, "NanoMesh-Redis-Sub");
        redisWorker.setDaemon(true);
        redisWorker.start();
    }

    private synchronized void sendRedisPublish(String message) {
        try {
            if (redisPubSocket == null || redisPubSocket.isClosed()) {
                redisPubSocket = new Socket(redisHost, redisPort);
                if (!redisPassword.isEmpty()) {
                    sendRespCommand(redisPubSocket.getOutputStream(), "AUTH", redisPassword);
                    readRespLine(redisPubSocket.getInputStream());
                }
            }
            sendRespCommand(redisPubSocket.getOutputStream(), "PUBLISH", CHANNEL, message);
            readRespLine(redisPubSocket.getInputStream());
        } catch (Throwable t) {
            try {
                if (redisPubSocket != null) redisPubSocket.close();
            } catch (Throwable ignored) {}
            redisPubSocket = null;
        }
    }

    private static void sendRespCommand(OutputStream out, String... args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(args.length).append("\r\n");
        for (String a : args) {
            byte[] bytes = a.getBytes(StandardCharsets.UTF_8);
            sb.append("$").append(bytes.length).append("\r\n").append(a).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readRespLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                break;
            }
            baos.write(b);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private String encodePacket(String server, String action, String payload, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"server\":\"").append(escape(server)).append("\",");
        sb.append("\"action\":\"").append(escape(action)).append("\",");
        sb.append("\"payload\":\"").append(escape(payload)).append("\"");
        if (data != null && !data.isEmpty()) {
            sb.append(",\"data\":{");
            int i = 0;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":\"").append(escape(String.valueOf(e.getValue()))).append("\"");
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String colorize(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s != null ? s : "");
    }

    private static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.isEmpty()) return map;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\{[^\\}]*\\}|\\d+(?:\\.\\d+)?|true|false|null)").matcher(json);
            while (m.find()) {
                String key = m.group(1);
                String rawVal = m.group(2);
                if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                    map.put(key, rawVal.substring(1, rawVal.length() - 1));
                } else if (rawVal.startsWith("{") && rawVal.endsWith("}")) {
                    map.put(key, parseSimpleJson(rawVal));
                } else {
                    map.put(key, rawVal);
                }
            }
        } catch (Throwable ignored) {}
        return map;
    }
}
