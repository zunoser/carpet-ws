package com.ゆき.あかず.carpetbotapi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class RpcWebSocketServer extends WebSocketServer {
    private static final Gson GSON = new Gson();
    private static final int MIN_WATCH_INTERVAL_MILLIS = 100;
    private static final int MAX_WATCH_INTERVAL_MILLIS = 60_000;

    private final ApiKeyStore apiKeys;
    private final JsonRpcDispatcher dispatcher;
    private final ScheduledExecutorService watchExecutor = Executors.newScheduledThreadPool(2);
    private final Map<WebSocket, Map<String, ScheduledFuture<?>>> watches = new ConcurrentHashMap<>();

    public RpcWebSocketServer(BotApiConfig config, ApiKeyStore apiKeys, JsonRpcDispatcher dispatcher) {
        super(new InetSocketAddress(config.host, config.websocketPort));
        this.apiKeys = apiKeys;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String token = token(handshake);
        if (apiKeys.authenticate(token).isEmpty()) {
            conn.close(1008, "invalid api key");
            return;
        }
        watches.put(conn, new ConcurrentHashMap<>());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (handleWatchMessage(conn, message)) {
            return;
        }
        dispatcher.handle(message).thenAccept(conn::send);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        cancelAll(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        CarpetBotApiMod.LOGGER.warn("WebSocket error", ex);
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(30);
    }

    public void stopServer() {
        try {
            watchExecutor.shutdownNow();
            stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean handleWatchMessage(WebSocket conn, String raw) {
        JsonObject request;
        try {
            request = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return false;
        }
        String method = request.has("method") ? request.get("method").getAsString() : "";
        if (!method.startsWith("watch.")) {
            return false;
        }

        JsonElement id = request.has("id") ? request.get("id") : JsonNull.INSTANCE;
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
            ? request.getAsJsonObject("params")
            : new JsonObject();

        try {
            switch (method) {
                case "watch.start" -> conn.send(JsonRpcDispatcher.formatResponse(id, startWatch(conn, params), null));
                case "watch.stop" -> conn.send(JsonRpcDispatcher.formatResponse(id, stopWatch(conn, params), null));
                case "watch.list" -> conn.send(JsonRpcDispatcher.formatResponse(id, listWatches(conn), null));
                default -> conn.send(JsonRpcDispatcher.formatError(id, -32601, "Method not found"));
            }
        } catch (JsonRpcDispatcher.RpcException e) {
            conn.send(JsonRpcDispatcher.formatError(id, e.code(), e.getMessage()));
        } catch (Exception e) {
            CarpetBotApiMod.LOGGER.warn("Watch request failed", e);
            conn.send(JsonRpcDispatcher.formatError(id, -32000, e.getMessage() == null ? "Server error" : e.getMessage()));
        }
        return true;
    }

    private JsonObject startWatch(WebSocket conn, JsonObject params) {
        String watchId = params.has("watchId") && !params.get("watchId").isJsonNull()
            ? params.get("watchId").getAsString()
            : UUID.randomUUID().toString();
        String targetMethod = requiredString(params, "method");
        if (targetMethod.startsWith("watch.")) {
            throw new JsonRpcDispatcher.RpcException(-32602, "watching watch.* methods is not allowed");
        }
        JsonObject targetParams = params.has("targetParams") && params.get("targetParams").isJsonObject()
            ? params.getAsJsonObject("targetParams")
            : params.has("params") && params.get("params").isJsonObject()
                ? params.getAsJsonObject("params")
                : new JsonObject();
        int intervalMillis = Math.max(MIN_WATCH_INTERVAL_MILLIS, Math.min(MAX_WATCH_INTERVAL_MILLIS, intParam(params, "intervalMillis", 1000)));

        Map<String, ScheduledFuture<?>> connWatches = watches.computeIfAbsent(conn, ignored -> new ConcurrentHashMap<>());
        ScheduledFuture<?> existing = connWatches.remove(watchId);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = watchExecutor.scheduleAtFixedRate(
            () -> sendWatchEvent(conn, watchId, targetMethod, targetParams),
            0,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
        connWatches.put(watchId, future);

        JsonObject out = new JsonObject();
        out.addProperty("watchId", watchId);
        out.addProperty("method", targetMethod);
        out.addProperty("intervalMillis", intervalMillis);
        return out;
    }

    private JsonObject stopWatch(WebSocket conn, JsonObject params) {
        String watchId = requiredString(params, "watchId");
        ScheduledFuture<?> future = watches.computeIfAbsent(conn, ignored -> new ConcurrentHashMap<>()).remove(watchId);
        JsonObject out = new JsonObject();
        out.addProperty("watchId", watchId);
        out.addProperty("stopped", future != null);
        if (future != null) {
            future.cancel(false);
        }
        return out;
    }

    private JsonObject listWatches(WebSocket conn) {
        JsonObject out = new JsonObject();
        JsonObject active = new JsonObject();
        for (String watchId : watches.computeIfAbsent(conn, ignored -> new ConcurrentHashMap<>()).keySet()) {
            active.addProperty(watchId, true);
        }
        out.add("watches", active);
        return out;
    }

    private void sendWatchEvent(WebSocket conn, String watchId, String method, JsonObject params) {
        if (!conn.isOpen()) {
            cancelAll(conn);
            return;
        }
        dispatcher.dispatch(method, params).handle((result, throwable) -> {
            if (!conn.isOpen()) {
                return null;
            }
            JsonObject event = new JsonObject();
            event.addProperty("jsonrpc", "2.0");
            event.addProperty("method", "watch.event");
            JsonObject eventParams = new JsonObject();
            eventParams.addProperty("watchId", watchId);
            eventParams.addProperty("method", method);
            if (throwable == null) {
                eventParams.add("result", result == null ? JsonNull.INSTANCE : result);
            } else {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                JsonObject error = new JsonObject();
                error.addProperty("message", cause.getMessage() == null ? "Server error" : cause.getMessage());
                error.addProperty("code", cause instanceof JsonRpcDispatcher.RpcException rpcException ? rpcException.code() : -32000);
                eventParams.add("error", error);
            }
            event.add("params", eventParams);
            conn.send(GSON.toJson(event));
            return null;
        });
    }

    private void cancelAll(WebSocket conn) {
        Map<String, ScheduledFuture<?>> connWatches = watches.remove(conn);
        if (connWatches == null) {
            return;
        }
        for (ScheduledFuture<?> future : connWatches.values()) {
            future.cancel(false);
        }
    }

    private static String token(ClientHandshake handshake) {
        String auth = handshake.getFieldValue("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length());
        }
        String resource = handshake.getResourceDescriptor();
        int idx = resource.indexOf('?');
        if (idx < 0) {
            return null;
        }
        Map<String, String> query = parseQuery(resource.substring(idx + 1));
        return query.get("api_key");
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            out.put(dec(pair[0]), pair.length == 2 ? dec(pair[1]) : "");
        }
        return out;
    }

    private static String dec(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String requiredString(JsonObject params, String key) {
        if (!params.has(key) || params.get(key).isJsonNull()) {
            throw new JsonRpcDispatcher.RpcException(-32602, "missing parameter: " + key);
        }
        return params.get(key).getAsString();
    }

    private static int intParam(JsonObject params, String key, int fallback) {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsInt() : fallback;
    }
}
