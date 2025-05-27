package com.createrington.currency;

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

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.NumberFormat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateringtonCurrency.MODID)
public class MoneyCommands {
    private static Component message(String emoji, String text, ChatFormatting color) {
        return Component.literal(emoji + " " + text).withStyle(color);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    // Reusing threads instead of creating new ones
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);
    // Shutdown EXECUTOR on server stop
    public static void shutdownExecutor() {
        EXECUTOR.shutdown();
    }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server is stopping, shutting down MoneyCommands executor.");
        shutdownExecutor();
    }
    // JSON parser
    private static final Gson GSON = new GsonBuilder().create();


    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("money")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String uuid = player.getUUID().toString();

                            EXECUTOR.submit(() -> {
                                try {
                                    URL url = URI.create("http://127.0.0.1:5000/api/currency/balance?uuid=" + uuid).toURL();
                                    HttpResponse response = sendGet(url);

                                    String body = response.body;

                                    // Parse balance from JSON
                                    Pattern pattern = Pattern.compile("\"balance\"\\s*:\\s*(\\d+)");
                                    Matcher matcher = pattern.matcher(body);
                                    if (matcher.find()) {
                                        int balance = Integer.parseInt(matcher.group(1));
                                        String formatted = NumberFormat.getInstance().format(balance);
                                        player.sendSystemMessage(message("💰", "Balance: $" + formatted, ChatFormatting.GREEN));
                                    } else {
                                        player.sendSystemMessage(message("[ERROR]", "Failed to parse balance: " + body, ChatFormatting.RED));
                                    }

                                } catch (Exception e) {
                                    player.sendSystemMessage(message("[ERROR]", "Request failed: " + e.getMessage(), ChatFormatting.RED));
                                    LOGGER.error("Exception in /money command", e);
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
                                            String toName = StringArgumentType.getString(context, "target");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            String fromUuid = sender.getUUID().toString();
                                            String toUuid;

                                            // Dev-only UUID map
                                            if (toName.equalsIgnoreCase("self")) {
                                                toUuid = fromUuid;
                                            } else if (toName.equalsIgnoreCase("dummy")) {
                                                toUuid = "091b900c-4174-478c-900c-a0fe5a31a329"; // must exist in DB
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
                                                    URL url = URI.create("http://127.0.0.1:5000/api/currency/pay").toURL();
                                                    sendPost(url, json);
                                                    String formatted = NumberFormat.getInstance().format(amount);

                                                    sender.sendSystemMessage(message("✅", "Sent $" + formatted + " to " + toName, ChatFormatting.GREEN));

                                                } catch (Exception e) {
                                                    sender.sendSystemMessage(message("[ERROR]", "Request failed: " + e.getMessage(), ChatFormatting.RED));
                                                    LOGGER.error("Exception in /pay command", e);
                                                }
                                            });


                                            return 1;
                                        })))
        );
        event.getDispatcher().register(
                Commands.literal("deposit")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
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
                                    URL url = URI.create("http://127.0.0.1:5000/api/currency/deposit").toURL();
                                    HttpResponse response = sendPost(url, json);

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
                                    LOGGER.error("Exception in /deposit command", e);
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

                            EXECUTOR.submit(() -> {
                                try {
                                    URL url = URI.create("http://127.0.0.1:5000/api/currency/top").toURL();
                                    HttpResponse response = sendGet(url);

                                    if (response.code == 200) {
                                        String body = response.body;

                                        Pattern entryPattern = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\",\\s*\"balance\"\\s*:\\s*(\\d+)\\s*}");
                                        Matcher matcher = entryPattern.matcher(body);
                                        int rank = 1;

                                        player.sendSystemMessage(message("🏆", "Top 10 Richest Players:", ChatFormatting.GREEN));
                                        while (matcher.find()) {
                                            String name = matcher.group(1);
                                            int balance = Integer.parseInt(matcher.group(2));
                                            String formattedBalance = NumberFormat.getInstance().format(balance);
                                            player.sendSystemMessage(Component.literal(" " + rank + ". " + name + ": $" + formattedBalance));
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
                                    LOGGER.error("Exception in /baltop command", e);
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
                        URI.create("http://127.0.0.1:5000/api/currency/withdraw").toURL(), json);

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
                            URI.create("http://127.0.0.1:5000/api/currency/withdraw").toURL(), json);

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
                            URI.create("http://127.0.0.1:5000/api/currency/withdraw").toURL(), json);

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
                        URI.create("http://127.0.0.1:5000/api/currency/withdraw").toURL(), json);

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

    private static HttpResponse sendGet(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
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

    private static HttpResponse sendPost(URL url, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.getOutputStream().write(json.getBytes());

        int responseCode = conn.getResponseCode();
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
}
