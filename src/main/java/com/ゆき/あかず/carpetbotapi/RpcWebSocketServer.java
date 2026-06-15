package com.ゆき.あかず.carpetbotapi;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class RpcWebSocketServer extends WebSocketServer {
    private final ApiKeyStore apiKeys;
    private final JsonRpcDispatcher dispatcher;

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
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        dispatcher.handle(message).thenAccept(conn::send);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
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
            stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
