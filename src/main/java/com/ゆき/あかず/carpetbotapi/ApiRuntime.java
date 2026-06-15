package com.ゆき.あかず.carpetbotapi;

import net.minecraft.server.MinecraftServer;

public final class ApiRuntime {
    private final DashboardHttpServer httpServer;
    private final RpcWebSocketServer webSocketServer;

    public ApiRuntime(MinecraftServer server, BotApiConfig config) {
        ApiKeyStore apiKeyStore = new ApiKeyStore(config);
        SessionStore sessions = new SessionStore(config.sessionSecret);
        JsonRpcDispatcher dispatcher = new JsonRpcDispatcher(new BotController(server));
        DiscordOAuth discordOAuth = new DiscordOAuth(config);
        this.httpServer = new DashboardHttpServer(config, apiKeyStore, sessions, dispatcher, discordOAuth);
        this.webSocketServer = new RpcWebSocketServer(config, apiKeyStore, dispatcher);
    }

    public void start() throws Exception {
        httpServer.start();
        webSocketServer.start();
        CarpetBotApiMod.LOGGER.info("Carpet Bot API listening on {}:{}", httpServer.host(), httpServer.port());
    }

    public void stop() {
        webSocketServer.stopServer();
        httpServer.stop();
    }
}
