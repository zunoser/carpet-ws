package com.ゆき.あかず.carpetbotapi;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Collection;
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
            Vec3 pos = new Vec3(number(params, "x", 0), number(params, "y", 80), number(params, "z", 0));
            double yaw = number(params, "yaw", 0);
            double pitch = number(params, "pitch", 0);
            ResourceKey<Level> dimension = dimension(string(params, "dimension", "minecraft:overworld"));
            GameType gameMode = gameMode(string(params, "gamemode", "survival"));
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
            ServerPlayer player = bot(requiredString(params, "name"));
            player.kill((ServerLevel) player.level());
            return ok();
        });
    }

    public CompletableFuture<JsonElement> list() {
        return onServer(() -> {
            JsonArray bots = new JsonArray();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player instanceof EntityPlayerMPFake) {
                    JsonObject bot = new JsonObject();
                    bot.addProperty("name", player.getGameProfile().name());
                    bot.addProperty("uuid", player.getStringUUID());
                    bot.addProperty("x", player.getX());
                    bot.addProperty("y", player.getY());
                    bot.addProperty("z", player.getZ());
                    bot.addProperty("yaw", player.getYRot());
                    bot.addProperty("pitch", player.getXRot());
                    bot.addProperty("dimension", player.level().dimension().identifier().toString());
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
            EntityPlayerActionPack pack = actionPack(bot(requiredString(params, "name")));
            EntityPlayerActionPack.ActionType type = actionType(requiredString(params, "action"));
            EntityPlayerActionPack.Action action = actionMode(string(params, "mode", "once"), integer(params, "interval", 1), integer(params, "offset", 0));
            pack.start(type, action);
            return ok();
        });
    }

    public CompletableFuture<JsonElement> move(JsonObject params) {
        return onServer(() -> {
            EntityPlayerActionPack pack = actionPack(bot(requiredString(params, "name")));
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
            actionPack(bot(requiredString(params, "name")))
                .look((float) number(params, "yaw", 0), (float) number(params, "pitch", 0));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> turn(JsonObject params) {
        return onServer(() -> {
            actionPack(bot(requiredString(params, "name")))
                .turn((float) number(params, "yaw", 0), (float) number(params, "pitch", 0));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> drop(JsonObject params) {
        return onServer(() -> {
            actionPack(bot(requiredString(params, "name")))
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
            actionPack(bot(requiredString(params, "name"))).setSlot(slot);
            return ok();
        });
    }

    public CompletableFuture<JsonElement> mount(JsonObject params) {
        return onServer(() -> {
            actionPack(bot(requiredString(params, "name"))).mount(bool(params, "ride", true));
            return ok();
        });
    }

    public CompletableFuture<JsonElement> dismount(JsonObject params) {
        return onServer(() -> {
            actionPack(bot(requiredString(params, "name"))).dismount();
            return ok();
        });
    }

    public CompletableFuture<JsonElement> stop(JsonObject params) {
        return onServer(() -> {
            EntityPlayerActionPack pack = actionPack(bot(requiredString(params, "name")));
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
            ServerPlayer player = bot(requiredString(params, "name"));
            String command = requiredString(params, "command");
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            return ok();
        });
    }

    public CompletableFuture<JsonElement> status(JsonObject params) {
        return onServer(() -> playerStatus(player(requiredString(params, "name"))));
    }

    public CompletableFuture<JsonElement> inventory(JsonObject params) {
        return onServer(() -> inventoryJson(player(requiredString(params, "name"))));
    }

    public CompletableFuture<JsonElement> inventorySet(JsonObject params) {
        return onServer(() -> {
            ServerPlayer player = bot(requiredString(params, "name"));
            int slot = inventorySlot(params, player.getInventory().getContainerSize());
            if (bool(params, "empty", false)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else if (params.has("item")) {
                throw new JsonRpcDispatcher.RpcException(-32602, "creating arbitrary item stacks is not implemented; use empty=true or swap existing slots");
            } else {
                throw new JsonRpcDispatcher.RpcException(-32602, "missing parameter: empty");
            }
            player.getInventory().setChanged();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> inventorySwap(JsonObject params) {
        return onServer(() -> {
            ServerPlayer player = bot(requiredString(params, "name"));
            int size = player.getInventory().getContainerSize();
            int a = slotNumber(params, "slotA", size);
            int b = slotNumber(params, "slotB", size);
            ItemStack stackA = player.getInventory().getItem(a);
            ItemStack stackB = player.getInventory().getItem(b);
            player.getInventory().setItem(a, stackB);
            player.getInventory().setItem(b, stackA);
            player.getInventory().setChanged();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> inventoryClear(JsonObject params) {
        return onServer(() -> {
            ServerPlayer player = bot(requiredString(params, "name"));
            if (params.has("slot")) {
                player.getInventory().setItem(inventorySlot(params, player.getInventory().getContainerSize()), ItemStack.EMPTY);
            } else {
                player.getInventory().clearContent();
            }
            player.getInventory().setChanged();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> effects(JsonObject params) {
        return onServer(() -> {
            JsonObject out = new JsonObject();
            out.add("effects", effectsJson(player(requiredString(params, "name")).getActiveEffects()));
            return out;
        });
    }

    public CompletableFuture<JsonElement> blocksAround(JsonObject params) {
        return onServer(() -> {
            ServerPlayer player = player(requiredString(params, "name"));
            int radius = Math.min(Math.max(integer(params, "radius", 3), 0), 8);
            BlockPos center = player.blockPosition();
            ServerLevel world = (ServerLevel) player.level();
            JsonArray blocks = new JsonArray();
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                    for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        JsonObject block = new JsonObject();
                        block.addProperty("x", x);
                        block.addProperty("y", y);
                        block.addProperty("z", z);
                        block.addProperty("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                        block.addProperty("state", state.toString());
                        block.addProperty("solid", state.isSolidRender());
                        block.addProperty("air", state.isAir());
                        blocks.add(block);
                    }
                }
            }
            JsonObject out = new JsonObject();
            out.add("center", blockPosJson(center));
            out.addProperty("radius", radius);
            out.add("blocks", blocks);
            return out;
        });
    }

    public CompletableFuture<JsonElement> entitiesAround(JsonObject params) {
        return onServer(() -> {
            ServerPlayer player = player(requiredString(params, "name"));
            double radius = Math.min(Math.max(number(params, "radius", 16), 0), 128);
            AABB box = AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2);
            JsonArray entities = new JsonArray();
            for (Entity entity : player.level().getEntities(player, box, ignored -> true)) {
                if (entity.distanceToSqr(player) > radius * radius) {
                    continue;
                }
                entities.add(entityJson(entity));
            }
            JsonObject out = new JsonObject();
            out.addProperty("radius", radius);
            out.add("entities", entities);
            return out;
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

    private ServerPlayer player(String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            throw new JsonRpcDispatcher.RpcException(-32602, "player not found: " + name);
        }
        return player;
    }

    private EntityPlayerMPFake bot(String name) {
        ServerPlayer player = player(name);
        if (!(player instanceof EntityPlayerMPFake fake)) {
            throw new JsonRpcDispatcher.RpcException(-32602, "not a fake player: " + name);
        }
        return fake;
    }

    private EntityPlayerActionPack actionPack(ServerPlayer player) {
        return ((ServerPlayerInterface) player).getActionPack();
    }

    private JsonObject playerStatus(ServerPlayer player) {
        JsonObject out = new JsonObject();
        out.addProperty("name", player.getGameProfile().name());
        out.addProperty("uuid", player.getStringUUID());
        out.addProperty("bot", player instanceof EntityPlayerMPFake);
        out.add("position", vecJson(player.position()));
        out.add("blockPosition", blockPosJson(player.blockPosition()));
        out.addProperty("yaw", player.getYRot());
        out.addProperty("pitch", player.getXRot());
        out.addProperty("dimension", player.level().dimension().identifier().toString());
        out.addProperty("health", player.getHealth());
        out.addProperty("maxHealth", player.getMaxHealth());
        out.addProperty("absorption", player.getAbsorptionAmount());
        out.addProperty("air", player.getAirSupply());
        out.addProperty("maxAir", player.getMaxAirSupply());
        out.addProperty("fireTicks", player.getRemainingFireTicks());
        out.addProperty("frozenTicks", player.getTicksFrozen());
        out.addProperty("food", player.getFoodData().getFoodLevel());
        out.addProperty("saturation", player.getFoodData().getSaturationLevel());
        out.addProperty("experienceLevel", player.experienceLevel);
        out.addProperty("experienceProgress", player.experienceProgress);
        out.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        out.addProperty("sneaking", player.isShiftKeyDown());
        out.addProperty("sprinting", player.isSprinting());
        out.addProperty("swimming", player.isSwimming());
        out.addProperty("onGround", player.onGround());
        out.add("effects", effectsJson(player.getActiveEffects()));
        return out;
    }

    private JsonObject inventoryJson(ServerPlayer player) {
        JsonObject out = new JsonObject();
        out.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        out.addProperty("size", player.getInventory().getContainerSize());
        JsonArray slots = new JsonArray();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            JsonObject slot = itemStackJson(player.getInventory().getItem(i));
            slot.addProperty("slot", i);
            slot.addProperty("section", inventorySection(i));
            slots.add(slot);
        }
        out.add("slots", slots);
        return out;
    }

    private static JsonObject itemStackJson(ItemStack stack) {
        JsonObject out = new JsonObject();
        out.addProperty("empty", stack.isEmpty());
        if (!stack.isEmpty()) {
            out.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            out.addProperty("name", stack.getHoverName().getString());
            out.addProperty("count", stack.count());
            out.addProperty("maxCount", stack.getMaxStackSize());
            out.addProperty("damage", stack.getDamageValue());
            out.addProperty("maxDamage", stack.getMaxDamage());
        }
        return out;
    }

    private static JsonArray effectsJson(Collection<MobEffectInstance> effects) {
        JsonArray out = new JsonArray();
        for (MobEffectInstance effect : effects) {
            JsonObject json = new JsonObject();
            json.addProperty("id", effect.getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown"));
            json.addProperty("amplifier", effect.getAmplifier());
            json.addProperty("duration", effect.getDuration());
            json.addProperty("ambient", effect.isAmbient());
            json.addProperty("showParticles", effect.isVisible());
            json.addProperty("showIcon", effect.showIcon());
            out.add(json);
        }
        return out;
    }

    private static JsonObject entityJson(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("uuid", entity.getStringUUID());
        out.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        out.addProperty("name", entity.getName().getString());
        out.add("position", vecJson(entity.position()));
        out.addProperty("yaw", entity.getYRot());
        out.addProperty("pitch", entity.getXRot());
        out.addProperty("removed", entity.isRemoved());
        if (entity instanceof LivingEntity living) {
            out.addProperty("health", living.getHealth());
            out.addProperty("maxHealth", living.getMaxHealth());
        }
        return out;
    }

    private static JsonObject vecJson(Vec3 pos) {
        JsonObject out = new JsonObject();
        out.addProperty("x", pos.x);
        out.addProperty("y", pos.y);
        out.addProperty("z", pos.z);
        return out;
    }

    private static JsonObject blockPosJson(BlockPos pos) {
        JsonObject out = new JsonObject();
        out.addProperty("x", pos.getX());
        out.addProperty("y", pos.getY());
        out.addProperty("z", pos.getZ());
        return out;
    }

    private static String inventorySection(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "hotbar";
        }
        if (slot >= 9 && slot <= 35) {
            return "main";
        }
        if (slot >= 36 && slot <= 39) {
            return "armor";
        }
        if (slot == 40) {
            return "offhand";
        }
        return "extra";
    }

    private static int inventorySlot(JsonObject params, int size) {
        return slotNumber(params, "slot", size);
    }

    private static int slotNumber(JsonObject params, String key, int size) {
        int slot = integer(params, key, -1);
        if (slot < 0 || slot >= size) {
            throw new JsonRpcDispatcher.RpcException(-32602, key + " must be 0.." + (size - 1));
        }
        return slot;
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

    private static ResourceKey<Level> dimension(String raw) {
        return switch (raw) {
            case "overworld", "minecraft:overworld" -> Level.OVERWORLD;
            case "nether", "minecraft:the_nether" -> Level.NETHER;
            case "end", "minecraft:the_end" -> Level.END;
            default -> ResourceKey.create(Registries.DIMENSION, Identifier.parse(raw));
        };
    }

    private static GameType gameMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "creative" -> GameType.CREATIVE;
            case "adventure" -> GameType.ADVENTURE;
            case "spectator" -> GameType.SPECTATOR;
            case "survival" -> GameType.SURVIVAL;
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
