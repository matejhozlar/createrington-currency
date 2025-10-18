package com.saunhardy.createringtoncurrency.events;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.BlockPos;

import static com.saunhardy.createringtoncurrency.CreateringtonCurrency.BANK_CARD;
import static com.saunhardy.createringtoncurrency.CreateringtonCurrency.*;

import java.util.Map;
import java.util.HashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.logistics.tableCloth.ShoppingListItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

// Import MoneyCommands for using its withdrawal logic
import com.saunhardy.createringtoncurrency.MoneyCommands;
import com.saunhardy.createringtoncurrency.Config;
import com.google.gson.Gson;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.lang.reflect.Method;


public class StockTickerIntegration {
    
    private static void getBills(Player player, ItemStack shoppingListItem) {
        // Get the shopping list from the item
        var shoppingList = ShoppingListItem.getList(shoppingListItem);
        if (shoppingList == null) {
            return;
        }
        
        // Use reflection to call bakeEntries and get payment requirements
        // We use reflection to avoid compile-time dependency on Couple class
        try {
            Method bakeEntriesMethod = shoppingList.getClass().getMethod("bakeEntries", 
                net.minecraft.world.level.LevelAccessor.class, net.minecraft.core.BlockPos.class);
            Object bakedEntries = bakeEntriesMethod.invoke(shoppingList, player.level(), null);
            
            if (bakedEntries == null) {
                return;
            }
            
            // Get the second element (payment requirements) using reflection
            Method getSecondMethod = bakedEntries.getClass().getMethod("getSecond");
            Object paymentSummary = getSecondMethod.invoke(bakedEntries);
            
            // Get payment stacks using reflection
            Method getStacksByCountMethod = paymentSummary.getClass().getMethod("getStacksByCount");
            @SuppressWarnings("unchecked")
            var paymentStacks = (java.util.List<Object>) getStacksByCountMethod.invoke(paymentSummary);
            
            // Create bill denomination mapping for checking
            Map<Item, Integer> billValues = new HashMap<>();
            billValues.put(BILL_1.get(), 1);
            billValues.put(BILL_5.get(), 5);
            billValues.put(BILL_10.get(), 10);
            billValues.put(BILL_20.get(), 20);
            billValues.put(BILL_50.get(), 50);
            billValues.put(BILL_100.get(), 100);
            billValues.put(BILL_500.get(), 500);
            billValues.put(BILL_1000.get(), 1000);
            
            // Check payment requirements for our bills
            int totalSlotsNeeded = 0;
            Map<Item, Integer> billsToDispense = new HashMap<>();
            
            for (Object bigItemStackObj : paymentStacks) {
                // Use reflection to access BigItemStack fields
                var stackField = bigItemStackObj.getClass().getField("stack");
                var countField = bigItemStackObj.getClass().getField("count");
                
                ItemStack paymentItem = (ItemStack) stackField.get(bigItemStackObj);
                int requiredAmount = countField.getInt(bigItemStackObj);
                
                // Check if this payment item is one of our bills
                if (billValues.containsKey(paymentItem.getItem())) {
                    billsToDispense.put(paymentItem.getItem(), requiredAmount);
                    
                    // Calculate slots needed for this bill type (max stack size 64)
                    int slotsForThisBill = (int) Math.ceil((double) requiredAmount / 64);
                    totalSlotsNeeded += slotsForThisBill;
                }
            }
            
            if (totalSlotsNeeded > 0 && player instanceof ServerPlayer serverPlayer) {
                // Count free inventory slots
                int freeSlots = 0;
                for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack slot = serverPlayer.getInventory().getItem(i);
                    if (slot.isEmpty()) {
                        freeSlots++;
                    }
                }
                
                if (freeSlots >= totalSlotsNeeded) {
                    // Player has enough space - proceed with bill dispensing logic
                    for (Map.Entry<Item, Integer> entry : billsToDispense.entrySet()) {
                        Item billItem = entry.getKey();
                        int count = entry.getValue();
                        int denomination = billValues.get(billItem);
                        
                        // Use MoneyCommands' withdrawal logic - submit to the executor
                        MoneyCommands.EXECUTOR.submit(() -> {
                            try {
                                String uuid = serverPlayer.getUUID().toString();
                                
                                // Create the payload for withdrawal API
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("uuid", uuid);
                                payload.put("count", count);
                                payload.put("denomination", denomination);
                                
                                String json = new Gson().toJson(payload);
                                
                                // Make HTTP request to withdraw endpoint
                                URL url = URI.create(MoneyCommands.safeJoin(Config.API_BASE_URL.get(), Config.API_WITHDRAW_URL.get())).toURL();
                                String token = MoneyCommands.getOrFetchToken(serverPlayer);
                                
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setRequestProperty("Authorization", "Bearer " + token);
                                conn.setDoOutput(true);
                                conn.setConnectTimeout(5000);
                                conn.setReadTimeout(5000);
                                conn.getOutputStream().write(json.getBytes());
                                
                                int responseCode = conn.getResponseCode();
                                
                                if (responseCode == 200) {
                                    // Success - give the player the bills
                                    ItemStack billStack = new ItemStack(billItem, count);
                                    serverPlayer.getInventory().add(billStack);
                                } else {
                                    // Failed withdrawal
                                    InputStream errorStream = conn.getErrorStream();
                                    if (errorStream != null) {
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                                        StringBuilder errorResponse = new StringBuilder();
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            errorResponse.append(line);
                                        }
                                        reader.close();
                                        serverPlayer.displayClientMessage(Component.translatable("createringtoncurrency.message.withdrawal_failed"), true);
                                    }
                                }
                                
                            } catch (Exception e) {
                                serverPlayer.displayClientMessage(Component.translatable("createringtoncurrency.message.withdrawal_error"), true);
                                e.printStackTrace();
                            }
                        });
                    }
                } else {
                    player.displayClientMessage(Component.translatable("createringtoncurrency.message.insufficient_inventory_space", totalSlotsNeeded, freeSlots), true);
                }
            }
            
        } catch (Exception e) {

        }
    }


    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity target = event.getTarget();
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);

        if (player == null || target == null || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos stockTickerPos = StockTickerInteractionHandler.getStockTickerPosition(target);
        if (stockTickerPos != null) {
            if ((heldItem.getItem() instanceof ShoppingListItem) && (player.getOffhandItem().is(BANK_CARD.get()))) {
                // Player is holding a shopping list and has a bank card in offhand
                getBills(player, heldItem);
            }
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onRightClickBlock(RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        BlockPos pos = event.getPos();
        ItemStack heldItem = player.getItemInHand(hand);

        if (player == null || player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // Check if the block is a Blaze Burner
        if (event.getLevel().getBlockState(pos).getBlock() instanceof BlazeBurnerBlock) {
            if ((heldItem.getItem() instanceof ShoppingListItem) && (player.getOffhandItem().is(BANK_CARD.get()))) {
                // Player is holding a shopping list and has a bank card in offhand
                getBills(player, heldItem);
            }
        }
    }
}