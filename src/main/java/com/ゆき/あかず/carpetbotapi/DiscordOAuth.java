package com.ゆき.あかず.carpetbotapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class DiscordOAuth {
    private static final Gson GSON = new Gson();
    private final BotApiConfig config;
    private final HttpClient client = HttpClient.newHttpClient();

    public DiscordOAuth(BotApiConfig config) {
        this.config = config;
    }

    public boolean configured() {
        return !config.discordClientId.isBlank() && !config.discordClientSecret.isBlank();
    }

    public String authorizeUrl(String state) {
        return "https://discord.com/oauth2/authorize"
            + "?response_type=code"
            + "&client_id=" + enc(config.discordClientId)
            + "&scope=" + enc("identify guilds")
            + "&redirect_uri=" + enc(config.redirectUri())
            + "&state=" + enc(state);
    }

    public Optional<DiscordUser> complete(String code) throws Exception {
        String form = "client_id=" + enc(config.discordClientId)
            + "&client_secret=" + enc(config.discordClientSecret)
            + "&grant_type=authorization_code"
            + "&code=" + enc(code)
            + "&redirect_uri=" + enc(config.redirectUri());
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("https://discord.com/api/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (tokenResponse.statusCode() / 100 != 2) {
            CarpetBotApiMod.LOGGER.warn("Discord token exchange failed: {}", tokenResponse.body());
            return Optional.empty();
        }
        JsonObject tokenJson = JsonParser.parseString(tokenResponse.body()).getAsJsonObject();
        String accessToken = tokenJson.get("access_token").getAsString();
        DiscordUser user = fetchUser(accessToken).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        if (!config.discordRequiredGuildId.isBlank() && !isGuildMember(accessToken, config.discordRequiredGuildId)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    private Optional<DiscordUser> fetchUser(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://discord.com/api/users/@me"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            return Optional.empty();
        }
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        String globalName = json.has("global_name") && !json.get("global_name").isJsonNull()
            ? json.get("global_name").getAsString()
            : json.get("username").getAsString();
        return Optional.of(new DiscordUser(json.get("id").getAsString(), globalName));
    }

    private boolean isGuildMember(String accessToken, String guildId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://discord.com/api/users/@me/guilds"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            return false;
        }
        JsonArray guilds = JsonParser.parseString(response.body()).getAsJsonArray();
        for (int i = 0; i < guilds.size(); i++) {
            JsonObject guild = guilds.get(i).getAsJsonObject();
            if (guildId.equals(guild.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record DiscordUser(String id, String username) {
    }
}
