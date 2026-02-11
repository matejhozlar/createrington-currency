package com.saunhardy.createringtoncurrency.events;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.BlockPos;

import static com.saunhardy.createringtoncurrency.CreateringtonCurrency.*;

import java.util.Map;
import java.util.HashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.logistics.tableCloth.ShoppingListItem;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.saunhardy.createringtoncurrency.MoneyCommands;
// Import our withdrawal helper
import com.saunhardy.createringtoncurrency.util.WithdrawalHelper;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;


public class StockTickerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static boolean getBills(Player player, ItemStack shoppingListItem) {
        // Get the shopping list from the item
        var shoppingList = ShoppingListItem.getList(shoppingListItem);
        if (shoppingList == null) {
            return false;
        }
        
        // Use reflection to call bakeEntries and get payment requirements
        // We use reflection to avoid compile-time dependency on Couple class
        try {
            Method bakeEntriesMethod = shoppingList.getClass().getMethod("bakeEntries", 
                net.minecraft.world.level.LevelAccessor.class, net.minecraft.core.BlockPos.class);
            Object bakedEntries = bakeEntriesMethod.invoke(shoppingList, player.level(), null);
            
            if (bakedEntries == null) {
                return false;
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
                // Check if player has enough inventory space
                if (!WithdrawalHelper.hasInventorySpace(serverPlayer, totalSlotsNeeded)) {
                    player.sendSystemMessage(Component.translatable("message.createringtoncurrency.insufficient_inventory_space").withStyle(ChatFormatting.RED));
                    return false;
                }
                // Player has enough space - proceed with bill dispensing using helper
                boolean allWithdrawalsSucceeded = true;
                
                for (Map.Entry<Item, Integer> entry : billsToDispense.entrySet()) {
                    Item billItem = entry.getKey();
                    int count = entry.getValue();
                    int denomination = billValues.get(billItem);
                    
                    // Use the withdrawal helper
                    WithdrawalHelper.WithdrawalResponse response = WithdrawalHelper.withdrawBills(
                        serverPlayer, billItem, count, denomination);
                    
                    if (!response.success) {
                        allWithdrawalsSucceeded = false;
                        player.sendSystemMessage(Component.translatable("message.createringtoncurrency.withdrawal_failed").withStyle(ChatFormatting.RED));
                        break; // Stop processing further withdrawals if one fails
                    }
                    player.sendSystemMessage(Component.translatable("message.createringtoncurrency.withdrawal_success", count, "$" + denomination).withStyle(ChatFormatting.GREEN));
                }
                
                // Return true if all withdrawals succeeded, indicating bills are now in inventory
                return allWithdrawalsSucceeded;
            }
            
        } catch (Exception e) {
            LOGGER.error("StockTicker bill dispensing failed: {}", e.getMessage());
            return false;
        }

        return false; // No bills to dispense
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

        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos stockTickerPos = StockTickerInteractionHandler.getStockTickerPosition(target);
        if (stockTickerPos != null) {
            if ((heldItem.getItem() instanceof ShoppingListItem) && (player.getOffhandItem().is(BANK_CARD.get()))) {
                // Player is holding a shopping list and has a bank card in offhand
                // Run our bill dispensing logic first, but don't cancel the event
                getBills(player, heldItem);
                // Allow other handlers to continue processing after we're done
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

        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // Check if the block is a Blaze Burner
        if (event.getLevel().getBlockState(pos).getBlock() instanceof BlazeBurnerBlock) {
            if ((heldItem.getItem() instanceof ShoppingListItem) && (player.getOffhandItem().is(BANK_CARD.get()))) {
                // Player is holding a shopping list and has a bank card in offhand
                // Run our bill dispensing logic first, but don't cancel the event
                getBills(player, heldItem);
                // Allow other handlers to continue processing after we're done
            }
        }
    }
}