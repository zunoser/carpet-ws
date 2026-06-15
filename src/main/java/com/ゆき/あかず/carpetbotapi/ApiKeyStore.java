package com.ゆき.あかず.carpetbotapi;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ApiKeyStore {
    private final BotApiConfig config;

    public ApiKeyStore(BotApiConfig config) {
        this.config = config;
    }

    public synchronized CreatedKey create(String label, String ownerDiscordId) throws IOException {
        String raw = "cbapi_" + Secrets.randomToken();
        BotApiConfig.ApiKeyRecord record = new BotApiConfig.ApiKeyRecord();
        record.id = UUID.randomUUID().toString();
        record.label = label == null || label.isBlank() ? "dashboard key" : label;
        record.ownerDiscordId = ownerDiscordId;
        record.sha256 = Secrets.sha256(raw);
        record.createdAtEpochSeconds = Instant.now().getEpochSecond();
        config.apiKeys.add(record);
        config.save();
        return new CreatedKey(record.id, raw);
    }

    public synchronized Optional<BotApiConfig.ApiKeyRecord> authenticate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String hash = Secrets.sha256(raw);
        return config.apiKeys.stream().filter(record -> hash.equals(record.sha256)).findFirst();
    }

    public record CreatedKey(String id, String key) {
    }
}
