package com.ゆき.あかず.carpetbotapi;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CompletableFuture;

public final class JsonRpcDispatcher {
    private static final Gson GSON = new Gson();
    private final BotController bots;

    public JsonRpcDispatcher(BotController bots) {
        this.bots = bots;
    }

    public CompletableFuture<String> handle(String raw) {
        JsonObject request;
        try {
            request = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(error(JsonNull.INSTANCE, -32700, "Parse error"));
        }
        JsonElement id = request.has("id") ? request.get("id") : JsonNull.INSTANCE;
        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
            ? request.getAsJsonObject("params")
            : new JsonObject();

        CompletableFuture<JsonElement> result;
        try {
            result = dispatch(method, params);
        } catch (Exception e) {
            result = CompletableFuture.failedFuture(e);
        }

        return result.handle((json, throwable) -> response(id, json, throwable));
    }

    public CompletableFuture<JsonElement> dispatch(String method, JsonObject params) {
        return switch (method) {
            case "bot.create" -> bots.create(params);
            case "bot.remove" -> bots.remove(params);
            case "bot.list" -> bots.list();
            case "bot.action" -> bots.action(params);
            case "bot.move" -> bots.move(params);
            case "bot.look" -> bots.look(params);
            case "bot.turn" -> bots.turn(params);
            case "bot.drop" -> bots.drop(params);
            case "bot.slot" -> bots.slot(params);
            case "bot.mount" -> bots.mount(params);
            case "bot.dismount" -> bots.dismount(params);
            case "bot.stop" -> bots.stop(params);
            case "bot.command" -> bots.command(params);
            case "player.status" -> bots.status(params);
            case "player.inventory" -> bots.inventory(params);
            case "player.inventory.set" -> bots.inventorySet(params);
            case "player.inventory.swap" -> bots.inventorySwap(params);
            case "player.inventory.clear" -> bots.inventoryClear(params);
            case "player.effects" -> bots.effects(params);
            case "world.blocksAround" -> bots.blocksAround(params);
            case "world.entitiesAround" -> bots.entitiesAround(params);
            default -> CompletableFuture.failedFuture(new RpcException(-32601, "Method not found"));
        };
    }

    public static String formatResponse(JsonElement id, JsonElement json, Throwable throwable) {
        return response(id, json, throwable);
    }

    private static String response(JsonElement id, JsonElement json, Throwable throwable) {
        if (throwable != null) {
            Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
            if (cause instanceof RpcException rpcException) {
                return error(id, rpcException.code(), rpcException.getMessage());
            }
            CarpetBotApiMod.LOGGER.warn("JSON-RPC request failed", cause);
            return error(id, -32000, cause.getMessage() == null ? "Server error" : cause.getMessage());
        }
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", json == null ? JsonNull.INSTANCE : json);
        return GSON.toJson(response);
    }

    public static String formatError(JsonElement id, int code, String message) {
        return error(id, code, message);
    }

    private static String error(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return GSON.toJson(response);
    }

    public static final class RpcException extends RuntimeException {
        private final int code;

        public RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
