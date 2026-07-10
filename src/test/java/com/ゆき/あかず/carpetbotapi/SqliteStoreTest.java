package com.ゆき.あかず.carpetbotapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqliteStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void apiKeysAreStoredAndAuthenticated() throws Exception {
        SqliteStore database = new SqliteStore(temporaryDirectory.resolve("test.sqlite3"));
        ApiKeyStore keys = new ApiKeyStore(new BotApiConfig(), database);
        ApiKeyStore.CreatedKey created = keys.create("test", "discord-user");

        assertTrue(keys.authenticate(created.key()).isPresent());
        assertFalse(keys.authenticate("wrong-key").isPresent());
    }

    @Test
    void oauthStatesAreSingleUseAndSessionsPersist() {
        Path path = temporaryDirectory.resolve("auth.sqlite3");
        SessionStore first = new SessionStore(new SqliteStore(path));
        String state = first.newOAuthState();
        assertTrue(first.consumeOAuthState(state));
        assertFalse(first.consumeOAuthState(state));

        String token = first.createSession(new DiscordOAuth.DiscordUser("123", "tester"));
        SessionStore restarted = new SessionStore(new SqliteStore(path));
        assertTrue(restarted.get(token).isPresent());
        assertFalse(restarted.get("wrong-token").isPresent());
    }
}
