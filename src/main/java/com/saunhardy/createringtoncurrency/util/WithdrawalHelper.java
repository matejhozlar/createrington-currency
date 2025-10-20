package com.saunhardy.createringtoncurrency.util;

import com.google.gson.Gson;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.MoneyCommands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class WithdrawalHelper {
    private static final Gson GSON = new Gson();
    
    /**
     * Result of a withdrawal operation
     */
    public enum WithdrawalResult {
        SUCCESS,           // Bills successfully withdrawn and added to inventory
        FAILED_API,        // API call failed (insufficient funds, server error, etc.)
        FAILED_CONNECTION, // Network/connection error
        FAILED_INVENTORY,  // Not enough inventory space
        FAILED_DEV_MODE    // Dev mode bypass (always succeeds)
    }
    
    /**
     * Response from a withdrawal attempt
     */
    public static class WithdrawalResponse {
        public final WithdrawalResult result;
        public final boolean success;
        
        private WithdrawalResponse(WithdrawalResult result) {
            this.result = result;
            this.success = result == WithdrawalResult.SUCCESS || result == WithdrawalResult.FAILED_DEV_MODE;
        }
        
        public static WithdrawalResponse success() {
            return new WithdrawalResponse(WithdrawalResult.SUCCESS);
        }
        
        public static WithdrawalResponse failed(WithdrawalResult result) {
            return new WithdrawalResponse(result);
        }
        
        public static WithdrawalResponse devMode() {
            return new WithdrawalResponse(WithdrawalResult.FAILED_DEV_MODE);
        }
    }
    
    /**
     * Syncron Withdraws bills for a player using the API or dev mode bypass
     * 
     * @param player The player to withdraw bills for
     * @param billItem The type of bill to withdraw
     * @param count The number of bills to withdraw
     * @param denomination The denomination value of the bills
     * @return WithdrawalResponse indicating the result
     */
    public static WithdrawalResponse withdrawBills(ServerPlayer player, Item billItem, int count, int denomination) {
        // Dev mode bypass - check if player name is "Dev"
        if (player.getName().getString().equals("Dev")) {
            ItemStack billStack = new ItemStack(billItem, count);
            player.getInventory().add(billStack);

            return WithdrawalResponse.devMode();
        }
        
        try {
            String uuid = player.getUUID().toString();
            
            // Create the payload for withdrawal API
            Map<String, Object> payload = new HashMap<>();
            payload.put("uuid", uuid);
            payload.put("count", count);
            payload.put("denomination", denomination);
            
            String json = GSON.toJson(payload);
            
            // Make HTTP request to withdraw endpoint synchronously
            URL url = URI.create(MoneyCommands.safeJoin(Config.API_BASE_URL.get(), Config.API_WITHDRAW_URL.get())).toURL();
            String token = MoneyCommands.getOrFetchToken(player);
            
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
                player.getInventory().add(billStack);
                
                return WithdrawalResponse.success();
            } else {
                // Failed withdrawal - read error response
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    reader.close();
                }
                
                return WithdrawalResponse.failed(WithdrawalResult.FAILED_API);
            }
            
        } catch (Exception e) {
            
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_CONNECTION);
        }
    }
    
   
    /**
     * Calculates the number of inventory slots needed for a given number of items
     * 
     * @param count Number of items
     * @param maxStackSize Maximum stack size for the item (usually 64 for bills)
     * @return Number of slots needed
     */
    public static int calculateSlotsNeeded(int count, int maxStackSize) {
        return (int) Math.ceil((double) count / maxStackSize);
    }


    /**
     * Checks if the player has enough inventory space for the given number of bills
     * Only counts main inventory (slots 9-35) and hotbar (slots 0-8)
     * 
     * @param player The player to check
     * @param slotsNeeded Number of inventory slots needed
     * @return true if player has enough space, false otherwise
     */
    public static boolean hasInventorySpace(ServerPlayer player, int slotsNeeded) {
        int freeSlots = 0;
        // Count hotbar slots (0-8) and main inventory slots (9-35)
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) {
                freeSlots++;
            }
        }
        return freeSlots >= slotsNeeded;
    }

    
    /**
     * Convenience method to withdraw bills with inventory space checking
     * 
     * @param player The player to withdraw bills for
     * @param billItem The type of bill to withdraw
     * @param count The number of bills to withdraw
     * @param denomination The denomination value of the bills
     * @return WithdrawalResponse indicating the result
     */
    public static WithdrawalResponse withdrawBillsWithSpaceCheck(ServerPlayer player, Item billItem, int count, int denomination) {
        // Check inventory space first
        int slotsNeeded = calculateSlotsNeeded(count, 64); // Bills stack to 64
        if (!hasInventorySpace(player, slotsNeeded)) {
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_INVENTORY);
        }
        
        // Proceed with withdrawal
        return withdrawBills(player, billItem, count, denomination);
    }
}