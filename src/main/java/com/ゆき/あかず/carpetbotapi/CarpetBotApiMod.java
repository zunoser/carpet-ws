package com.ゆき.あかず.carpetbotapi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CarpetBotApiMod implements ModInitializer {
    public static final String MOD_ID = "carpet-bot-api";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ApiRuntime runtime;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
    }

    private void start(MinecraftServer server) {
        try {
            BotApiConfig config = BotApiConfig.load();
            if (!config.enabled) {
                LOGGER.info("Carpet Bot API is disabled. Enable it in config/carpet-bot-api.json");
                return;
            }
            runtime = new ApiRuntime(server, config);
            runtime.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start Carpet Bot API", e);
        }
    }

    private void stop() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
    }
}
