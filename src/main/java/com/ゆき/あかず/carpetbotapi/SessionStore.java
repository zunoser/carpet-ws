package com.ゆき.あかず.carpetbotapi;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionStore {
    private static final long SESSION_TTL_SECONDS = 12 * 60 * 60;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> oauthStates = new ConcurrentHashMap<>();
    private final String secret;

    public SessionStore(String secret) {
        this.secret = secret;
    }

    public String newOAuthState() {
        String state = Secrets.sha256(secret + ":" + Secrets.randomToken());
        oauthStates.put(state, state);
        return state;
    }

    public boolean consumeOAuthState(String state) {
        return state != null && oauthStates.remove(state) != null;
    }

    public String createSession(DiscordOAuth.DiscordUser user) {
        String token = Secrets.randomToken();
        sessions.put(token, new Session(user.id(), user.username(), Instant.now().getEpochSecond()));
        return token;
    }

    public Optional<Session> get(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() - session.createdAtEpochSeconds > SESSION_TTL_SECONDS) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public record Session(String discordId, String username, long createdAtEpochSeconds) {
    }
}
