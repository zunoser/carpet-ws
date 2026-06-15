package com.ゆき.あかず.carpetbotapi;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

public final class DashboardHttpServer {
    private final BotApiConfig config;
    private final ApiKeyStore apiKeys;
    private final SessionStore sessions;
    private final JsonRpcDispatcher dispatcher;
    private final DiscordOAuth discord;
    private final HttpServer server;

    public DashboardHttpServer(
        BotApiConfig config,
        ApiKeyStore apiKeys,
        SessionStore sessions,
        JsonRpcDispatcher dispatcher,
        DiscordOAuth discord
    ) {
        try {
            this.config = config;
            this.apiKeys = apiKeys;
            this.sessions = sessions;
            this.dispatcher = dispatcher;
            this.discord = discord;
            this.server = HttpServer.create(new InetSocketAddress(config.host, config.port), 0);
            this.server.createContext("/", this::dashboard);
            this.server.createContext("/login", this::login);
            this.server.createContext(config.discordRedirectPath, this::callback);
            this.server.createContext("/api/keys", this::createKey);
            this.server.createContext("/rpc", this::rpc);
            this.server.setExecutor(Executors.newCachedThreadPool());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create HTTP server", e);
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    public String host() {
        return config.host;
    }

    public int port() {
        return config.port;
    }

    private void dashboard(HttpExchange exchange) throws java.io.IOException {
        if (!"GET".equals(exchange.getRequestMethod()) || !"/".equals(exchange.getRequestURI().getPath())) {
            HttpUtil.send(exchange, 404, "text/plain", "not found");
            return;
        }
        SessionStore.Session session = sessions.get(HttpUtil.cookie(exchange, "cbapi_session")).orElse(null);
        String body = """
            <!doctype html>
            <html lang="ja">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Carpet Bot API</title>
              <style>
                body{font-family:system-ui,sans-serif;margin:40px;max-width:760px;background:#101418;color:#eef3f8}
                a,button{background:#3fb37f;color:#08110d;border:0;border-radius:6px;padding:10px 14px;font-weight:700;text-decoration:none;cursor:pointer}
                input{padding:10px;border-radius:6px;border:1px solid #54616d;background:#151b21;color:#eef3f8}
                pre{white-space:pre-wrap;background:#151b21;padding:16px;border-radius:8px}
                .row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
              </style>
            </head>
            <body>
              <h1>Carpet Bot API</h1>
              %s
            </body>
            </html>
            """.formatted(session == null ? loginHtml() : keyHtml(session));
        HttpUtil.send(exchange, 200, "text/html", body);
    }

    private String loginHtml() {
        if (!discord.configured()) {
            return "<p>Discord OAuth is not configured. Edit config/carpet-bot-api.json first.</p>";
        }
        return "<p>Discord でログインして API key を発行します。</p><p><a href=\"/login\">Login with Discord</a></p>";
    }

    private String keyHtml(SessionStore.Session session) {
        return """
            <p>Logged in as %s</p>
            <div class="row">
              <input id="label" placeholder="key label" value="local bot client">
              <button onclick="createKey()">Generate API Key</button>
            </div>
            <pre id="out"></pre>
            <script>
            async function createKey(){
              const res = await fetch('/api/keys', {method:'POST', headers:{'content-type':'application/json'}, body: JSON.stringify({label:document.getElementById('label').value})});
              document.getElementById('out').textContent = await res.text();
            }
            </script>
            """.formatted(escape(session.username()));
    }

    private void login(HttpExchange exchange) throws java.io.IOException {
        if (!discord.configured()) {
            HttpUtil.send(exchange, 503, "text/plain", "Discord OAuth is not configured");
            return;
        }
        String state = sessions.newOAuthState();
        exchange.getResponseHeaders().set("Location", discord.authorizeUrl(state));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void callback(HttpExchange exchange) throws java.io.IOException {
        try {
            Map<String, String> query = HttpUtil.query(exchange);
            if (!sessions.consumeOAuthState(query.get("state"))) {
                HttpUtil.send(exchange, 400, "text/plain", "invalid state");
                return;
            }
            DiscordOAuth.DiscordUser user = discord.complete(query.get("code")).orElse(null);
            if (user == null) {
                HttpUtil.send(exchange, 403, "text/plain", "Discord authentication failed or guild membership is missing");
                return;
            }
            String token = sessions.createSession(user);
            exchange.getResponseHeaders().add("Set-Cookie", "cbapi_session=" + token + "; HttpOnly; SameSite=Lax; Path=/");
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        } catch (Exception e) {
            CarpetBotApiMod.LOGGER.warn("Discord callback failed", e);
            HttpUtil.send(exchange, 500, "text/plain", "oauth failed");
        }
    }

    private void createKey(HttpExchange exchange) throws java.io.IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.send(exchange, 405, "text/plain", "method not allowed");
            return;
        }
        SessionStore.Session session = sessions.get(HttpUtil.cookie(exchange, "cbapi_session")).orElse(null);
        if (session == null) {
            HttpUtil.send(exchange, 401, "application/json", "{\"error\":\"login required\"}");
            return;
        }
        JsonObject body = com.google.gson.JsonParser.parseString(HttpUtil.readBody(exchange)).getAsJsonObject();
        ApiKeyStore.CreatedKey key = apiKeys.create(body.has("label") ? body.get("label").getAsString() : "", session.discordId());
        JsonObject out = new JsonObject();
        out.addProperty("id", key.id());
        out.addProperty("apiKey", key.key());
        HttpUtil.send(exchange, 200, "application/json", out.toString());
    }

    private void rpc(HttpExchange exchange) throws java.io.IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.send(exchange, 405, "text/plain", "method not allowed");
            return;
        }
        if (apiKeys.authenticate(HttpUtil.bearer(exchange)).isEmpty()) {
            HttpUtil.send(exchange, 401, "application/json", "{\"error\":\"invalid api key\"}");
            return;
        }
        String response = dispatcher.handle(HttpUtil.readBody(exchange)).join();
        HttpUtil.send(exchange, 200, "application/json", response);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
