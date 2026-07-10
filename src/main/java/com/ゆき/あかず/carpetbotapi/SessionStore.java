package com.ゆき.あかず.carpetbotapi;

import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

public final class SessionStore {
    private static final long SESSION_TTL_SECONDS = 12 * 60 * 60;
    private static final long OAUTH_STATE_TTL_SECONDS = 10 * 60;

    private final SqliteStore database;

    public SessionStore(SqliteStore database) {
        this.database = database;
        database.purgeExpired();
    }

    public String newOAuthState() {
        String state = Secrets.randomToken();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO oauth_states(state_hash, expires_at) VALUES(?, ?)")) {
            statement.setString(1, Secrets.sha256(state));
            statement.setLong(2, Instant.now().getEpochSecond() + OAUTH_STATE_TTL_SECONDS);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create OAuth state", e);
        }
        return state;
    }

    public boolean consumeOAuthState(String state) {
        if (state == null) return false;
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM oauth_states WHERE state_hash = ? AND expires_at > ?")) {
            statement.setString(1, Secrets.sha256(state));
            statement.setLong(2, Instant.now().getEpochSecond());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not consume OAuth state", e);
        }
    }

    public String createSession(DiscordOAuth.DiscordUser user) {
        String token = Secrets.randomToken();
        long createdAt = Instant.now().getEpochSecond();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO sessions(token_hash, discord_id, username, created_at, expires_at) VALUES(?, ?, ?, ?, ?)")) {
            statement.setString(1, Secrets.sha256(token));
            statement.setString(2, user.id());
            statement.setString(3, user.username());
            statement.setLong(4, createdAt);
            statement.setLong(5, createdAt + SESSION_TTL_SECONDS);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create session", e);
        }
        return token;
    }

    public Optional<Session> get(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return database.findSession(Secrets.sha256(token), Instant.now().getEpochSecond());
    }

    public record Session(String discordId, String username, long createdAtEpochSeconds) {
    }
}
