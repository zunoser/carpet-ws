package com.ゆき.あかず.carpetbotapi;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

public final class SqliteStore {
    private final String url;

    public SqliteStore(Path path) {
        this.url = "jdbc:sqlite:" + path.toAbsolutePath();
        initialize();
    }

    private void initialize() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE IF NOT EXISTS api_keys (id TEXT PRIMARY KEY, label TEXT NOT NULL, owner_discord_id TEXT NOT NULL, sha256 TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, discord_id TEXT NOT NULL, username TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS oauth_states (state_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions(expires_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS oauth_states_expiry ON oauth_states(expires_at)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize SQLite store", e);
        }
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    public void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        try (Connection connection = open()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
                statement.setLong(1, now);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM oauth_states WHERE expires_at <= ?")) {
                statement.setLong(1, now);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not purge expired authentication data", e);
        }
    }

    public Optional<SessionStore.Session> findSession(String tokenHash, long now) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT discord_id, username, created_at FROM sessions WHERE token_hash = ? AND expires_at > ?")) {
            statement.setString(1, tokenHash);
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new SessionStore.Session(result.getString(1), result.getString(2), result.getLong(3))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read session", e);
        }
    }
}
