package ru.nanodev.nanoforge.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import ru.nanodev.nanoforge.event.NanoWebhookEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Встроенный высокопроизводительный Webhook & REST API Server.
 * Работает на стандартной Java без тяжелых зависимостей.
 */
public final class WebhookServer {

    private static final Logger LOG = Logger.getLogger("NanoForge");
    private static HttpServer server;

    private WebhookServer() {}

    public static synchronized void start(int port, String secretToken) {
        if (server != null) {
            stop();
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/v1/webhook", new WebhookHandler(secretToken));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            LOG.info("[NanoForge] Webhook Server запущен на порту " + port + " (POST /api/v1/webhook)");
        } catch (Throwable t) {
            LOG.warning("[NanoForge] Не удалось запустить Webhook Server на порту " + port + ": " + t.getMessage());
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
                LOG.info("[NanoForge] Webhook Server остановлен.");
            } catch (Throwable ignored) {}
            server = null;
        }
    }

    private static class WebhookHandler implements HttpHandler {
        private final String secretToken;

        public WebhookHandler(String secretToken) {
            this.secretToken = secretToken != null ? secretToken.trim() : "";
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }

                if (!secretToken.isEmpty()) {
                    String auth = exchange.getRequestHeaders().getFirst("Authorization");
                    String expected = "Bearer " + secretToken;
                    if (auth == null || !auth.equals(expected)) {
                        sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                        return;
                    }
                }

                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                String body = baos.toString(StandardCharsets.UTF_8.name());

                Map<String, Object> jsonMap = parseSimpleJson(body);

                // Вызываем виртуальный Bukkit-ивент
                Bukkit.getPluginManager().callEvent(new NanoWebhookEvent(body, jsonMap));

                sendResponse(exchange, 200, "{\"status\":\"ok\"}");
            } catch (Throwable t) {
                try {
                    sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
                } catch (Throwable ignored) {}
            }
        }

        private void sendResponse(HttpExchange exchange, int code, String response) throws Exception {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, Object> parseSimpleJson(String json) {
            Map<String, Object> map = new HashMap<>();
            if (json == null || json.isEmpty()) return map;
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+(?:\\.\\d+)?|true|false|null)").matcher(json);
                while (m.find()) {
                    String key = m.group(1);
                    String rawVal = m.group(2);
                    if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                        map.put(key, rawVal.substring(1, rawVal.length() - 1));
                    } else if ("true".equalsIgnoreCase(rawVal)) {
                        map.put(key, true);
                    } else if ("false".equalsIgnoreCase(rawVal)) {
                        map.put(key, false);
                    } else if ("null".equalsIgnoreCase(rawVal)) {
                        map.put(key, null);
                    } else {
                        try {
                            if (rawVal.contains(".")) {
                                map.put(key, Double.parseDouble(rawVal));
                            } else {
                                map.put(key, Long.parseLong(rawVal));
                            }
                        } catch (Throwable ignored) {
                            map.put(key, rawVal);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return map;
        }
    }
}
