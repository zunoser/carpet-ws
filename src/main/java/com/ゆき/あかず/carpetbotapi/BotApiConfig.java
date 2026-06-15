package com.ゆき.あかず.carpetbotapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BotApiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = false;
    public String host = "0.0.0.0";
    public int port = 8765;
    public int websocketPort = 8766;
    public String publicBaseUrl = "http://localhost:8765";
    public String discordClientId = "";
    public String discordClientSecret = "";
    public String discordRedirectPath = "/oauth/callback";
    public String discordRequiredGuildId = "";
    public String sessionSecret = "change-me";
    public List<ApiKeyRecord> apiKeys = new ArrayList<>();

    public static BotApiConfig load() throws IOException {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("carpet-bot-api.json");
        if (Files.notExists(path)) {
            BotApiConfig config = new BotApiConfig();
            config.sessionSecret = Secrets.randomToken();
            config.save();
            return config;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            BotApiConfig config = GSON.fromJson(reader, BotApiConfig.class);
            if (config.apiKeys == null) {
                config.apiKeys = new ArrayList<>();
            }
            return config;
        }
    }

    public synchronized void save() throws IOException {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("carpet-bot-api.json");
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        }
    }

    public String redirectUri() {
        return publicBaseUrl + discordRedirectPath;
    }

    public static final class ApiKeyRecord {
        public String id;
        public String label;
        public String ownerDiscordId;
        public String sha256;
        public long createdAtEpochSeconds;
    }
}
