package com.ゆき.あかず.carpetbotapi;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ApiKeyStore {
    private final SqliteStore database;

    public ApiKeyStore(BotApiConfig config, SqliteStore database) {
        this.database = database;
        migrate(config);
    }

    public synchronized CreatedKey create(String label, String ownerDiscordId) throws IOException {
        String raw = "cbapi_" + Secrets.randomToken();
        BotApiConfig.ApiKeyRecord record = new BotApiConfig.ApiKeyRecord();
        record.id = UUID.randomUUID().toString();
        record.label = label == null || label.isBlank() ? "dashboard key" : label;
        record.ownerDiscordId = ownerDiscordId;
        record.sha256 = Secrets.sha256(raw);
        record.createdAtEpochSeconds = Instant.now().getEpochSecond();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO api_keys(id, label, owner_discord_id, sha256, created_at) VALUES(?, ?, ?, ?, ?)")) {
            statement.setString(1, record.id);
            statement.setString(2, record.label);
            statement.setString(3, record.ownerDiscordId);
            statement.setString(4, record.sha256);
            statement.setLong(5, record.createdAtEpochSeconds);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Could not save API key", e);
        }
        return new CreatedKey(record.id, raw);
    }

    public synchronized Optional<BotApiConfig.ApiKeyRecord> authenticate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String hash = Secrets.sha256(raw);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT id, label, owner_discord_id, sha256, created_at FROM api_keys WHERE sha256 = ?")) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                BotApiConfig.ApiKeyRecord record = new BotApiConfig.ApiKeyRecord();
                record.id = result.getString(1);
                record.label = result.getString(2);
                record.ownerDiscordId = result.getString(3);
                record.sha256 = result.getString(4);
                record.createdAtEpochSeconds = result.getLong(5);
                return Optional.of(record);
            }
        } catch (SQLException e) {
            CarpetBotApiMod.LOGGER.error("Could not authenticate API key", e);
            return Optional.empty();
        }
    }

    private void migrate(BotApiConfig config) {
        if (config.apiKeys.isEmpty()) return;
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT OR IGNORE INTO api_keys(id, label, owner_discord_id, sha256, created_at) VALUES(?, ?, ?, ?, ?)")) {
            for (BotApiConfig.ApiKeyRecord record : config.apiKeys) {
                statement.setString(1, record.id);
                statement.setString(2, record.label == null ? "migrated key" : record.label);
                statement.setString(3, record.ownerDiscordId == null ? "unknown" : record.ownerDiscordId);
                statement.setString(4, record.sha256);
                statement.setLong(5, record.createdAtEpochSeconds);
                statement.addBatch();
            }
            statement.executeBatch();
            config.apiKeys.clear();
            config.save();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not migrate API keys to SQLite", e);
        }
    }

    public record CreatedKey(String id, String key) {
    }
}
