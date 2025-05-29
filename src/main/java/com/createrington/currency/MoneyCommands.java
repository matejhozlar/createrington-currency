package com.createrington.currency;

import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateringtonCurrency.MODID)
public class MoneyCommands {
    // Refetching JWT
    private static final Map<UUID, Long> TOKEN_EXPIRATION = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = 9 * 60 * 1000;
    // JWT Authentication
    private static final Map<UUID, String> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static Component message(String emoji, String text, ChatFormatting color) {
        return Component.literal(emoji + " " + text).withStyle(color);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    // Reusing threads instead of creating new ones
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);
    // Shutdown EXECUTOR on server stop
    public static void shutdownExecutor() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
                LOGGER.warn("Executor force-shutdown due to timeout");
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted during shutdown", e);
            Thread.currentThread().interrupt();
        }
    }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server is stopping, shutting down MoneyCommands executor.");
        shutdownExecutor();
        COOLDOWNS.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        COOLDOWNS.remove(event.getEntity().getUUID());
        TOKEN_CACHE.remove(event.getEntity().getUUID());
        TOKEN_EXPIRATION.remove(event.getEntity().getUUID());
    }
    // JSON parser
    private static final Gson GSON = new GsonBuilder().create();
    // Cooldown
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static long getCooldownMs() {
        return Config.commandCooldownMs;
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        if (!Config.apiBaseUrl.endsWith("/")) {
            LOGGER.warn("API base URL is missing a trailing slash. This may cause URL errors.");
        }
        event.getDispatcher().register(
                Commands.literal("money")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;

                            String uuid = player.getUUID().toString();

                            EXECUTOR.submit(() -> {
                                try {
                                    URL url = URI.create(safeJoin(Config.apiBaseUrl, Config.apiBalanceUrl) + "?uuid=" + uuid).toURL();
                                    HttpResponse response = sendGet(url, player);

                                    String body = response.body;

                                    // Parse balance from JSON
                                    JsonObject json = GSON.fromJson(body, JsonObject.class);
                                    if(json.has("balance")) {
                                        int balance = json.get("balance").getAsInt();
                                        String formatted = NumberFormat.getInstance().format(balance);
                                        player.sendSystemMessage(message("💰", "Balance: $" + formatted, ChatFormatting.GREEN));
                                    } else {
                                        player.sendSystemMessage(message("[ERROR]", "Missing 'balance' in response: " + body, ChatFormatting.RED));
                                    }

                                } catch (Exception e) {
                                    player.sendSystemMessage(message("[ERROR]", "Request failed: " + e.getMessage(), ChatFormatting.RED));
                                    LOGGER.error("Exception in /money command for {} (UUID: {}",player.getName().getString(), uuid, e);
                                }
                            });

                            return 1;
                        })
        );
        event.getDispatcher().register(
                Commands.literal("pay")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            ServerPlayer sender = context.getSource().getPlayerOrException();
                                            if (isOnCooldown(sender)) return 0;
                                            String toName = StringArgumentType.getString(context, "target");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            String fromUuid = sender.getUUID().toString();
                                            String toUuid;

                                            // Dev-only UUID map
                                            if (toName.equalsIgnoreCase("self")) {
                                                toUuid = fromUuid;
                                            } else {
                                                context.getSource().sendFailure(message("[ERROR]" ,"Unknown target: " + toName, ChatFormatting.RED));
                                                return 0;
                                            }

                                            Map<String, Object> payload = new HashMap<>();
                                            payload.put("fromUuid", fromUuid);
                                            payload.put("toUuid", toUuid);
                                            payload.put("amount", amount);

                                            String json = GSON.toJson(payload);

                                            EXECUTOR.submit(() -> {
                                                try {
                                                    URL url = URI.create(safeJoin(Config.apiBaseUrl, Config.apiPayUrl)).toURL();
                                                    sendPost(url, sender,  json);
                                                    String formatted = NumberFormat.getInstance().format(amount);

                                                    sender.sendSystemMessage(message("✅", "Sent $" + formatted + " to " + toName, ChatFormatting.GREEN));

                                                } catch (Exception e) {
                                                    sender.sendSystemMessage(message("[ERROR]", "Request failed: " + e.getMessage(), ChatFormatting.RED));
                                                    LOGGER.error("Exception in /pay command for {} (UUID: {})", sender.getName().getString(), fromUuid, e);
                                                }
                                            });


                                            return 1;
                                        })))
        );
        event.getDispatcher().register(
                Commands.literal("deposit")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;
                            final String uuid = player.getUUID().toString();

                            // Define the bill items and their values
                            Map<Item, Integer> billValues = Map.of(
                                    CreateringtonCurrency.BILL_1.get(), 1,
                                    CreateringtonCurrency.BILL_5.get(), 5,
                                    CreateringtonCurrency.BILL_10.get(), 10,
                                    CreateringtonCurrency.BILL_20.get(), 20,
                                    CreateringtonCurrency.BILL_50.get(), 50,
                                    CreateringtonCurrency.BILL_100.get(), 100,
                                    CreateringtonCurrency.BILL_500.get(), 500,
                                    CreateringtonCurrency.BILL_1000.get(), 1000
                            );

                            // Step 1: Scan inventory for bills
                            final Map<Integer, List<Integer>> slotsByDenomination = new HashMap<>();
                            int computedTotal = 0;

                            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                ItemStack stack = player.getInventory().getItem(i);
                                if (!stack.isEmpty() && billValues.containsKey(stack.getItem())) {
                                    int value = billValues.get(stack.getItem());
                                    int count = stack.getCount();
                                    computedTotal += value * count;

                                    slotsByDenomination.computeIfAbsent(value, k -> new ArrayList<>()).add(i);
                                }
                            }

                            if (computedTotal == 0) {
                                player.sendSystemMessage(message("[ERROR]" , "No bills to deposit.", ChatFormatting.RED));
                                return 1;
                            }

                            final int totalAmount = computedTotal; // must be final for use in thread

                            // Step 2: Make API request
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("uuid", uuid);
                            payload.put("amount", totalAmount);

                            final String json = GSON.toJson(payload);
                            String formatted = NumberFormat.getInstance().format(totalAmount);

                            player.sendSystemMessage(message("Processing deposit of", "$" + formatted + "...", ChatFormatting.YELLOW));

                            EXECUTOR.submit(() -> {
                                try {
                                    URL url = URI.create(safeJoin(Config.apiBaseUrl, Config.apiDepositUrl)).toURL();
                                    HttpResponse response = sendPost(url, player, json);

                                    if (response.code == 200) {
                                        // Step 3: Remove items AFTER successful deposit
                                        for (Map.Entry<Integer, List<Integer>> entry : slotsByDenomination.entrySet()) {
                                            for (int slot : entry.getValue()) {
                                                player.getInventory().setItem(slot, ItemStack.EMPTY);
                                            }
                                        }
                                        player.sendSystemMessage(message("✅", "Deposited $" + formatted + " into your account!", ChatFormatting.GREEN));
                                    } else {
                                        player.sendSystemMessage(message("[ERROR]", "Deposit failed: " + response.body, ChatFormatting.RED));
                                    }

                                } catch (Exception e) {
                                    player.sendSystemMessage(message("[ERROR]", "Deposit failed or server unavailable. No money was lost.", ChatFormatting.RED));
                                    LOGGER.error("Exception in /deposit command for {} (UUID: {})",player.getName().getString(), uuid, e);
                                }
                            });

                            return 1;
                        })
        );
        event.getDispatcher().register(
                Commands.literal("withdraw")
                        .then(Commands.argument("input", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (isOnCooldown(player)) return 0;
                                    String input = StringArgumentType.getString(context, "input").trim();

                                    // Match: "50 2"
                                    if (input.matches("^\\d+ \\d+$")) {
                                        String[] parts = input.split(" ");
                                        int denom = Integer.parseInt(parts[0]);
                                        int count = Integer.parseInt(parts[1]);
                                        return withdrawFixed(player, denom, count);
                                    }

                                    // Match: "50:2 20:1 5:3"
                                    if (input.contains(":")) {
                                        return withdrawCustomBundle(player, input);
                                    }

                                    // Match: "185"
                                    if (input.matches("^\\d+$")) {
                                        int total = Integer.parseInt(input);
                                        return withdrawOptimized(player, total);
                                    }

                                    player.sendSystemMessage(message("[ERROR]" , "Invalid command format.", ChatFormatting.RED));
                                    return 0;
                                })
                        )
        );
        event.getDispatcher().register(
                Commands.literal("baltop")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (isOnCooldown(player)) return 0;

                            EXECUTOR.submit(() -> {
                                try {
                                    URL url = URI.create(safeJoin(Config.apiBaseUrl, Config.apiTopUrl)).toURL();
                                    HttpResponse response = sendGet(url, player);

                                    if (response.code == 200) {
                                        String body = response.body;

                                        JsonArray topList = GSON.fromJson(body, JsonArray.class);
                                        int rank = 1;

                                        player.sendSystemMessage(message("🏆", "Top 10 Richest Players:", ChatFormatting.GREEN));
                                        for (JsonElement entryElement: topList){
                                            JsonObject entry = entryElement.getAsJsonObject();
                                            String name = entry.get("name").getAsString();
                                            int balance = entry.get("balance").getAsInt();
                                            String formatted = NumberFormat.getInstance().format(balance);

                                            player.sendSystemMessage(Component.literal(" " + rank + ". " + name + ": $" + formatted));
                                            rank++;
                                        }

                                        if (rank == 1) {
                                            player.sendSystemMessage(message("[ERROR]", "No data found.", ChatFormatting.RED));
                                        }
                                    } else {
                                        player.sendSystemMessage(message("[ERROR]", "Baltop failed: " + response.body, ChatFormatting.RED));
                                    }

                                } catch (Exception e) {
                                    player.sendSystemMessage(message("[ERROR]", "Baltop failed: " + e.getMessage(), ChatFormatting.RED));
                                    LOGGER.error("Exception in /baltop command for {} (UUID: {})", player.getName().getString(), player.getUUID(), e);
                                }
                            });

                            return 1;
                        })
        );
    }
    private static int withdrawFixed(ServerPlayer player, int denomination, int count) {
        String uuid = player.getUUID().toString();
        Item billItemCheck = getBillItem(denomination);

        if (billItemCheck == null) {
            player.sendSystemMessage(message("[ERROR]", "Invalid denomination.", ChatFormatting.RED));
            return 0;
        }

        if (isInventoryFullFor(player, billItemCheck, count)) {
            String formatted = NumberFormat.getInstance().format(count);
            player.sendSystemMessage(message("[ERROR]", "Not enough inventory space for " + formatted + " bills.", ChatFormatting.RED));
            return 0;
        }

        EXECUTOR.submit(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("uuid", uuid);
                payload.put("count", count);
                payload.put("denomination", denomination);

                String json = GSON.toJson(payload);

                HttpResponse response = sendPost(
                        URI.create(safeJoin(Config.apiBaseUrl, Config.apiWithdrawUrl)).toURL(), player, json);


                if (response.code == 200) {
                    Item billItem = getBillItem(denomination);
                    if (billItem == null) {
                        player.sendSystemMessage(message("[ERROR]", "Invalid denomination.", ChatFormatting.RED));
                        return;
                    }

                    ItemStack stack = new ItemStack(billItem, count);
                    player.getInventory().placeItemBackInInventory(stack);

                    final int amount = denomination * count;
                    String formatted = NumberFormat.getInstance().format(amount);
                    player.sendSystemMessage(message("✅", "Successfully withdrew $" + formatted, ChatFormatting.GREEN));
                } else {
                    player.sendSystemMessage(message("❌", "Withdraw failed: " + response.body, ChatFormatting.RED));
                }

            } catch (Exception e) {
                player.sendSystemMessage(message("[ERROR]", "Request failed: " + e.getMessage(), ChatFormatting.RED));
                LOGGER.error("Exception in /withdrawFixed", e);
            }
        });

        return 1;
    }

    private static int withdrawCustomBundle(ServerPlayer player, String input) {
        Map<Integer, Integer> bundle = new LinkedHashMap<>();
        int totalAmount = 0;

        // Phase 1: Parse and validate
        for (String part : input.split(" ")) {
            String[] pair = part.split(":");
            if (pair.length != 2) {
                player.sendSystemMessage(message("[ERROR]", "Invalid format: " + part, ChatFormatting.RED));
                return 0;
            }

            int denom, count;
            try {
                denom = Integer.parseInt(pair[0]);
                count = Integer.parseInt(pair[1]);
            } catch (NumberFormatException e) {
                player.sendSystemMessage(message("[ERROR]", "Invalid number in: " + part, ChatFormatting.RED));
                return 0;
            }

            if (count <= 0 || denom <= 0) {
                player.sendSystemMessage(message("[ERROR]", "Invalid denomination or count: " + part, ChatFormatting.RED));
                return 0;
            }

            Item item = getBillItem(denom);
            if (item == null) {
                player.sendSystemMessage(message("[ERROR]", "Unsupported denomination: $" + denom, ChatFormatting.RED));
                return 0;
            }

            if (isInventoryFullFor(player, item, count)) {
                player.sendSystemMessage(message("[ERROR]", "Not enough inventory space for $" + denom + " x " + count, ChatFormatting.RED));
                return 0;
            }

            bundle.put(denom, count);
            totalAmount += denom * count;
        }

        // Phase 2: Submit all withdrawals in 1 task
        final int finalTotal = totalAmount;
        EXECUTOR.submit(() -> {
            boolean allSucceeded = true;

            for (Map.Entry<Integer, Integer> entry : bundle.entrySet()) {
                int denom = entry.getKey();
                int count = entry.getValue();

                try {
                    String uuid = player.getUUID().toString();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("uuid", uuid);
                    payload.put("count", count);
                    payload.put("denomination", denom);

                    String json = GSON.toJson(payload);

                    HttpResponse response = sendPost(
                            URI.create(safeJoin(Config.apiBaseUrl, Config.apiWithdrawUrl)).toURL(), player,json);

                    if (response.code == 200) {
                        Item billItem = getBillItem(denom);
                        if (billItem != null) {
                            ItemStack stack = new ItemStack(billItem, count);
                            player.getInventory().placeItemBackInInventory(stack);
                        }
                    } else {
                        allSucceeded = false;
                        player.sendSystemMessage(message("[ERROR]", "Withdraw failed for $" + (denom * count) + ": " + response.body, ChatFormatting.RED));
                    }

                } catch (Exception e) {
                    allSucceeded = false;
                    player.sendSystemMessage(message("[ERROR]", "Withdraw failed for $" + (denom * count) + ": " + e.getMessage(), ChatFormatting.RED));
                    LOGGER.error("Error during /withdrawCustomBundle", e);
                }
            }

            if (allSucceeded) {
                String formatted = NumberFormat.getInstance().format(finalTotal);
                player.sendSystemMessage(message("✅", "Successfully withdrew $" + formatted, ChatFormatting.GREEN));
            }
        });

        return 1;
    }

    private static int withdrawOptimized(ServerPlayer player, int totalAmount) {
        int[] denominations = {1000, 500, 100, 50, 20, 10, 5, 1};
        Map<Integer, Integer> result = new LinkedHashMap<>();
        int originalTotal = totalAmount;

        for (int denom : denominations) {
            int count = totalAmount / denom;
            if (count > 0) {
                result.put(denom, count);
                totalAmount -= denom * count;
            }
        }

        if (totalAmount > 0) {
            player.sendSystemMessage(message("[ERROR]", "Cannot make exact change.", ChatFormatting.RED));
            return 0;
        }

        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            Item billItem = getBillItem(entry.getKey());
            if(billItem == null) {
                player.sendSystemMessage(message("[ERROR]", "Invalid denomination: $" + entry.getKey(), ChatFormatting.RED));
                return 0;
            }
            if (isInventoryFullFor(player, billItem, entry.getValue())) {
                String formatted = NumberFormat.getInstance().format(entry.getValue());
                player.sendSystemMessage(message("[ERROR]", "Not enough inventory space for " + formatted + " bills.", ChatFormatting.RED));
                return 0;
            }
        }

        EXECUTOR.submit(() -> {
            boolean allSucceeded = true;

            for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
                try {
                    String uuid = player.getUUID().toString();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("uuid", uuid);
                    payload.put("count", entry.getValue());
                    payload.put("denomination", entry.getKey());

                    String json = GSON.toJson(payload);

                    HttpResponse response = sendPost(
                            URI.create(safeJoin(Config.apiBaseUrl, Config.apiWithdrawUrl)).toURL(), player, json);

                    if (response.code == 200) {
                        Item billItem = getBillItem(entry.getKey());
                        if (billItem != null) {
                            ItemStack stack = new ItemStack(billItem, entry.getValue());
                            player.getInventory().placeItemBackInInventory(stack);
                        }
                    } else {
                        allSucceeded = false;
                        player.sendSystemMessage(message("[ERROR]", "Withdraw failed for $" + (entry.getKey() * entry.getValue()) + ": " + response.body, ChatFormatting.RED));
                    }

                } catch (Exception e) {
                    allSucceeded = false;
                    player.sendSystemMessage(message("[ERROR]", "Request failed:" + e.getMessage(), ChatFormatting.RED));
                    LOGGER.error("Exception in /withdrawOptimized", e);
                }
            }

            if (allSucceeded) {
                String formatted = NumberFormat.getInstance().format(originalTotal);
                player.sendSystemMessage(message("✅", "Successfully withdrew $" + formatted, ChatFormatting.GREEN));
            }

        });

        return 1;
    }

    @SuppressWarnings("unused")
    private static void withdrawFixedSilent(ServerPlayer player, int denomination, int count) {
        String uuid = player.getUUID().toString();
        Item billItemCheck = getBillItem(denomination);
        if (billItemCheck == null) return;

        if (isInventoryFullFor(player, billItemCheck, count)) {
            LOGGER.warn("Silent withdraw skipped due to insufficient inventory space for {}x${}", count, denomination);
            return;
        }

        EXECUTOR.submit(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("uuid", uuid);
                payload.put("count", count);
                payload.put("denomination", denomination);

                String json = GSON.toJson(payload);

                HttpResponse response = sendPost(
                        URI.create(safeJoin(Config.apiBaseUrl, Config.apiWithdrawUrl)).toURL(), player, json);

                if (response.code == 200) {
                    Item billItem = getBillItem(denomination);
                    if (billItem == null) return;
                    ItemStack stack = new ItemStack(billItem, count);
                    player.getInventory().placeItemBackInInventory(stack);
                } else {
                    player.sendSystemMessage(message("[ERROR]", "Withdraw failed: " + response.body, ChatFormatting.RED));
                }

            } catch (Exception e) {
                player.sendSystemMessage(message("[ERROR]", "Request failed during silent withdraw: " + e.getMessage(), ChatFormatting.RED));
                LOGGER.error("Exception in /withdrawFixedSilent", e);
            }
        });
    }

    private static Item getBillItem(int denomination) {
        return switch (denomination) {
            case 1 -> CreateringtonCurrency.BILL_1.get();
            case 5 -> CreateringtonCurrency.BILL_5.get();
            case 10 -> CreateringtonCurrency.BILL_10.get();
            case 20 -> CreateringtonCurrency.BILL_20.get();
            case 50 -> CreateringtonCurrency.BILL_50.get();
            case 100 -> CreateringtonCurrency.BILL_100.get();
            case 500 -> CreateringtonCurrency.BILL_500.get();
            case 1000 -> CreateringtonCurrency.BILL_1000.get();
            default -> null;
        };
    }

    private static HttpResponse sendGet(URL url, ServerPlayer player) throws Exception {
        String token = getOrFetchToken(player);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 401) {
            TOKEN_CACHE.remove(player.getUUID());
            TOKEN_EXPIRATION.remove(player.getUUID());
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()
        ))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            return new HttpResponse(responseCode, response.toString());
        }
    }

    private static class HttpResponse {
        int code;
        String body;

        HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static HttpResponse sendPost(URL url,ServerPlayer player, String json) throws Exception {
        String token = getOrFetchToken(player);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.getOutputStream().write(json.getBytes());

        int responseCode = conn.getResponseCode();
        if (responseCode == 401) {
            TOKEN_CACHE.remove(player.getUUID());
            TOKEN_EXPIRATION.remove(player.getUUID());
        }
        InputStream inputStream = (responseCode == 200)
                ? conn.getInputStream()
                : conn.getErrorStream();

        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return new HttpResponse(responseCode, response.toString());
    }

    private static String getOrFetchToken(ServerPlayer player) throws Exception {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        if (!TOKEN_CACHE.containsKey(uuid) || TOKEN_EXPIRATION.getOrDefault(uuid, 0L) < now) {
            String token = fetchJwtToken(player);
            TOKEN_CACHE.put(uuid, token);
            TOKEN_EXPIRATION.put(uuid, now + TOKEN_TTL_MS);
        }
        return TOKEN_CACHE.get(uuid);
    }

    // Inventory space checker (to overcome overflow)
    private static boolean isInventoryFullFor(ServerPlayer player, Item item, int totalCount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        int remaining = totalCount;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);

            if (slot.isEmpty()) {
                remaining -= maxStackSize;
            } else if (slot.getItem() == item && slot.getCount() < maxStackSize) {
                remaining -= (maxStackSize - slot.getCount());
            }

            if (remaining <= 0) return false; // not enough room
        }

        return true;
    }

    // Cooldowns
    private static boolean isOnCooldown(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();

        if (COOLDOWNS.containsKey(uuid)) {
            long lastUsed = COOLDOWNS.get(uuid);
            if(now - lastUsed < getCooldownMs()) {
                long secondsLeft = (getCooldownMs() - (now - lastUsed)) / 1000;
                player.sendSystemMessage(message("[COOLDOWN]", "Please wait " + secondsLeft + "s before using this command again.", ChatFormatting.RED));
                return true;
            }
        }

        COOLDOWNS.put(uuid, now);
        return false;
    }

    private static String fetchJwtToken(ServerPlayer player) throws Exception {
        String uuid = player.getUUID().toString();
        String name = player.getName().getString();

        URL url = URI.create(safeJoin(Config.apiBaseUrl, Config.apiLoginUrl)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = new Gson().toJson(Map.of("uuid", uuid, "name", name));
        conn.getOutputStream().write(json.getBytes());

        int responseCode = conn.getResponseCode();
        InputStream input = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);

        if(responseCode != 200) throw new Exception("Failed to fetch JWT: " + sb);

        JsonObject obj = JsonParser.parseString(sb.toString()).getAsJsonObject();
        return obj.get("token").getAsString();
    }

    // Safe join for urls
    private static String safeJoin(String base, String path) {
        if (!base.endsWith("/")) base += "/";
        if (path.startsWith("/")) path = path.substring(1);
        return base + path;
    }
}
