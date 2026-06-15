package com.ゆき.あかず.carpetbotapi;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class BotController {
    private final MinecraftServer server;

    public BotController(MinecraftServer server) {
        this.server = server;
    }

    public CompletableFuture<JsonElement> create(JsonObject params) {
        return onServer(() -> {
            String name = requiredString(params, "name");
            Vec3d pos = new Vec3d(number(params, "x", 0), number(params, "y", 80), number(params, "z", 0));
            double yaw = number(params, "yaw", 0);
            double pitch = number(params, "pitch", 0);
            RegistryKey<World> dimension = dimension(string(params, "dimension", "minecraft:overworld"));
            GameMode gameMode = gameMode(string(params, "gamemode", "survival"));
            boolean flying = bool(params, "flying", false);
            boolean created = EntityPlayerMPFake.createFake(name, server, pos, yaw, pitch, dimension, gameMode, flying);
            JsonObject out = new JsonObject();
            out.addProperty("created", created);
            out.addProperty("name", name);
            return out;
        });
    }

    public CompletableFuture<JsonElement> remove(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            player.kill((ServerWorld) player.getWorld());
            return ok();
        });
    }

    public CompletableFuture<JsonElement> list() {
        return onServer(() -> {
            JsonArray bots = new JsonArray();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player instanceof EntityPlayerMPFake) {
                    JsonObject bot = new JsonObject();
                    bot.addProperty("name", player.getGameProfile().getName());
                    bot.addProperty("uuid", player.getUuidAsString());
                    bot.addProperty("x", player.getX());
                    bot.addProperty("y", player.getY());
                    bot.addProperty("z", player.getZ());
                    bot.addProperty("yaw", player.getYaw());
                    bot.addProperty("pitch", player.getPitch());
                    bot.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());
                    bots.add(bot);
                }
            }
            JsonObject out = new JsonObject();
            out.add("bots", bots);
            return out;
        });
    }

    public CompletableFuture<JsonElement> action(JsonObject params) {
        return onServer(() -> {
            EntityPlayerActionPack pack = actionPack(player(requiredString(params, "name")));
            EntityPlayerActionPack.ActionType type = actionType(requiredString(params, "action"));
            EntityPlayerActionPack.Action action = actionMode(string(params, "mode", "once"), integer(params, "interval", 1), integer(params, "offset", 0));
            pack.start(type, action);
            return ok();
        });
    }

    public CompletableFuture<JsonElement> move(JsonObject params) {
        return onServer(() -> {
            EntityPlayerActionPack pack = actionPack(player(requiredString(params, "name")));
            if (params.has("forward")) {
                pack.setForward((float) number(params, "forward", 0));
            }
            if (params.has("strafing")) {
                pack.setStrafing((float) number(params, "strafing", 0));
            }
            if (params.has("sneaking")) {
                pack.setSneaking(bool(params, "sneaking", false));
            }
            if (params.has("sprinting")) {
                pack.setSprinting(bool(params, "sprinting", false));
            }
            return ok();
        });
    }

    public CompletableFuture<JsonElement> look(JsonObject params) {
        return onServer(() -> {
            actionPack(player(requiredString(params, "name")))
                .look((float) number(params, "yaw", 0), (float) number(params, "pitch", 0));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> turn(JsonObject params) {
        return onServer(() -> {
            actionPack(player(requiredString(params, "name")))
                .turn((float) number(params, "yaw", 0), (float) number(params, "pitch", 0));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> drop(JsonObject params) {
        return onServer(() -> {
            actionPack(player(requiredString(params, "name")))
                .drop(integer(params, "amount", 1), bool(params, "all", false));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> slot(JsonObject params) {
        return onServer(() -> {
            int slot = integer(params, "slot", 0);
            if (slot < 0 || slot > 8) {
                throw new JsonRpcDispatcher.RpcException(-32602, "slot must be 0..8");
            }
            actionPack(player(requiredString(params, "name"))).setSlot(slot);
            return ok();
        });
    }

    public CompletableFuture<JsonElement> mount(JsonObject params) {
        return onServer(() -> {
            actionPack(player(requiredString(params, "name"))).mount(bool(params, "ride", true));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> dismount(JsonObject params) {
        return onServer(() -> {
            actionPack(player(requiredString(params, "name"))).dismount();
            return ok();
        });
    }

    public CompletableFuture<JsonElement> stop(JsonObject params) {
        return onServer(() -> {
            EntityPlayerActionPack pack = actionPack(player(requiredString(params, "name")));
            if (bool(params, "movementOnly", false)) {
                pack.stopMovement();
            } else {
                pack.stopAll();
            }
            return ok();
        });
    }

    public CompletableFuture<JsonElement> command(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            String command = requiredString(params, "command");
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), command);
            return ok();
        });
    }

    private CompletableFuture<JsonElement> onServer(Supplier<JsonElement> supplier) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private ServerPlayerEntity player(String name) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) {
            throw new JsonRpcDispatcher.RpcException(-32602, "player not found: " + name);
        }
        return player;
    }

    private EntityPlayerActionPack actionPack(ServerPlayerEntity player) {
        return ((ServerPlayerInterface) player).getActionPack();
    }

    private static JsonObject ok() {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        return out;
    }

    private static EntityPlayerActionPack.ActionType actionType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "use" -> EntityPlayerActionPack.ActionType.USE;
            case "attack" -> EntityPlayerActionPack.ActionType.ATTACK;
            case "jump" -> EntityPlayerActionPack.ActionType.JUMP;
            case "drop_item", "drop-item", "dropitem" -> EntityPlayerActionPack.ActionType.DROP_ITEM;
            case "drop_stack", "drop-stack", "dropstack" -> EntityPlayerActionPack.ActionType.DROP_STACK;
            case "swap_hands", "swap-hands", "swaphands" -> EntityPlayerActionPack.ActionType.SWAP_HANDS;
            default -> throw new JsonRpcDispatcher.RpcException(-32602, "unknown action: " + raw);
        };
    }

    private static EntityPlayerActionPack.Action actionMode(String raw, int interval, int offset) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "once" -> EntityPlayerActionPack.Action.once();
            case "continuous" -> EntityPlayerActionPack.Action.continuous();
            case "interval" -> EntityPlayerActionPack.Action.interval(interval, offset);
            default -> throw new JsonRpcDispatcher.RpcException(-32602, "unknown action mode: " + raw);
        };
    }

    private static RegistryKey<World> dimension(String raw) {
        return switch (raw) {
            case "overworld", "minecraft:overworld" -> World.OVERWORLD;
            case "nether", "minecraft:the_nether" -> World.NETHER;
            case "end", "minecraft:the_end" -> World.END;
            default -> RegistryKey.of(RegistryKeys.WORLD, Identifier.of(raw));
        };
    }

    private static GameMode gameMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "creative" -> GameMode.CREATIVE;
            case "adventure" -> GameMode.ADVENTURE;
            case "spectator" -> GameMode.SPECTATOR;
            case "survival" -> GameMode.SURVIVAL;
            default -> throw new JsonRpcDispatcher.RpcException(-32602, "unknown gamemode: " + raw);
        };
    }

    private static String requiredString(JsonObject params, String key) {
        if (!params.has(key) || params.get(key).isJsonNull()) {
            throw new JsonRpcDispatcher.RpcException(-32602, "missing parameter: " + key);
        }
        return params.get(key).getAsString();
    }

    private static String string(JsonObject params, String key, String fallback) {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject params, String key, double fallback) {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsDouble() : fallback;
    }

    private static int integer(JsonObject params, String key, int fallback) {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject params, String key, boolean fallback) {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsBoolean() : fallback;
    }
}
