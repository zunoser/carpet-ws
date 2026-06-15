package com.ゆき.あかず.carpetbotapi;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

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

    public CompletableFuture<JsonElement> status(JsonObject params) {
        return onServer(() -> playerStatus(player(requiredString(params, "name"))));
    }

    public CompletableFuture<JsonElement> inventory(JsonObject params) {
        return onServer(() -> inventoryJson(player(requiredString(params, "name"))));
    }

    public CompletableFuture<JsonElement> inventorySet(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            int slot = inventorySlot(params, player.getInventory().size());
            if (bool(params, "empty", false)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            } else if (params.has("item")) {
                throw new JsonRpcDispatcher.RpcException(-32602, "creating arbitrary item stacks is not implemented; use empty=true or swap existing slots");
            } else {
                throw new JsonRpcDispatcher.RpcException(-32602, "missing parameter: empty");
            }
            player.getInventory().markDirty();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> inventorySwap(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            int size = player.getInventory().size();
            int a = slotNumber(params, "slotA", size);
            int b = slotNumber(params, "slotB", size);
            ItemStack stackA = player.getInventory().getStack(a);
            ItemStack stackB = player.getInventory().getStack(b);
            player.getInventory().setStack(a, stackB);
            player.getInventory().setStack(b, stackA);
            player.getInventory().markDirty();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> inventoryClear(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            if (params.has("slot")) {
                player.getInventory().setStack(inventorySlot(params, player.getInventory().size()), ItemStack.EMPTY);
            } else {
                player.getInventory().clear();
            }
            player.getInventory().markDirty();
            return inventoryJson(player);
        });
    }

    public CompletableFuture<JsonElement> effects(JsonObject params) {
        return onServer(() -> {
            JsonObject out = new JsonObject();
            out.add("effects", effectsJson(player(requiredString(params, "name")).getStatusEffects()));
            return out;
        });
    }

    public CompletableFuture<JsonElement> blocksAround(JsonObject params) {
        return onServer(() -> {
            ServerPlayerEntity player = player(requiredString(params, "name"));
            int radius = Math.min(Math.max(integer(params, "radius", 3), 0), 8);
            BlockPos center = player.getBlockPos();
            ServerWorld world = (ServerWorld) player.getWorld();
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
                        block.addProperty("id", Registries.BLOCK.getId(state.getBlock()).toString());
                        block.addProperty("state", state.toString());
                        block.addProperty("solid", state.isSolidBlock(world, pos));
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
            ServerPlayerEntity player = player(requiredString(params, "name"));
            double radius = Math.min(Math.max(number(params, "radius", 16), 0), 128);
            Box box = Box.of(player.getPos(), radius * 2, radius * 2, radius * 2);
            JsonArray entities = new JsonArray();
            for (Entity entity : player.getWorld().getOtherEntities(player, box)) {
                if (entity.squaredDistanceTo(player) > radius * radius) {
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

    private JsonObject playerStatus(ServerPlayerEntity player) {
        JsonObject out = new JsonObject();
        out.addProperty("name", player.getGameProfile().getName());
        out.addProperty("uuid", player.getUuidAsString());
        out.addProperty("bot", player instanceof EntityPlayerMPFake);
        out.add("position", vecJson(player.getPos()));
        out.add("blockPosition", blockPosJson(player.getBlockPos()));
        out.addProperty("yaw", player.getYaw());
        out.addProperty("pitch", player.getPitch());
        out.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());
        out.addProperty("health", player.getHealth());
        out.addProperty("maxHealth", player.getMaxHealth());
        out.addProperty("absorption", player.getAbsorptionAmount());
        out.addProperty("air", player.getAir());
        out.addProperty("maxAir", player.getMaxAir());
        out.addProperty("fireTicks", player.getFireTicks());
        out.addProperty("frozenTicks", player.getFrozenTicks());
        out.addProperty("food", player.getHungerManager().getFoodLevel());
        out.addProperty("saturation", player.getHungerManager().getSaturationLevel());
        out.addProperty("experienceLevel", player.experienceLevel);
        out.addProperty("totalExperience", player.totalExperience);
        out.addProperty("experienceProgress", player.experienceProgress);
        out.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        out.addProperty("sneaking", player.isSneaking());
        out.addProperty("sprinting", player.isSprinting());
        out.addProperty("swimming", player.isSwimming());
        out.addProperty("onGround", player.isOnGround());
        out.add("effects", effectsJson(player.getStatusEffects()));
        return out;
    }

    private JsonObject inventoryJson(ServerPlayerEntity player) {
        JsonObject out = new JsonObject();
        out.addProperty("selectedSlot", player.getInventory().getSelectedSlot());
        out.addProperty("size", player.getInventory().size());
        JsonArray slots = new JsonArray();
        for (int i = 0; i < player.getInventory().size(); i++) {
            JsonObject slot = itemStackJson(player.getInventory().getStack(i));
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
            out.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
            out.addProperty("name", stack.getName().getString());
            out.addProperty("count", stack.getCount());
            out.addProperty("maxCount", stack.getMaxCount());
            out.addProperty("damage", stack.getDamage());
            out.addProperty("maxDamage", stack.getMaxDamage());
        }
        return out;
    }

    private static JsonArray effectsJson(Collection<StatusEffectInstance> effects) {
        JsonArray out = new JsonArray();
        for (StatusEffectInstance effect : effects) {
            JsonObject json = new JsonObject();
            json.addProperty("id", effect.getEffectType().getIdAsString());
            json.addProperty("amplifier", effect.getAmplifier());
            json.addProperty("duration", effect.getDuration());
            json.addProperty("ambient", effect.isAmbient());
            json.addProperty("showParticles", effect.shouldShowParticles());
            json.addProperty("showIcon", effect.shouldShowIcon());
            out.add(json);
        }
        return out;
    }

    private static JsonObject entityJson(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("uuid", entity.getUuidAsString());
        out.addProperty("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        out.addProperty("name", entity.getName().getString());
        out.add("position", vecJson(entity.getPos()));
        out.addProperty("yaw", entity.getYaw());
        out.addProperty("pitch", entity.getPitch());
        out.addProperty("removed", entity.isRemoved());
        if (entity instanceof LivingEntity living) {
            out.addProperty("health", living.getHealth());
            out.addProperty("maxHealth", living.getMaxHealth());
        }
        return out;
    }

    private static JsonObject vecJson(Vec3d pos) {
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
