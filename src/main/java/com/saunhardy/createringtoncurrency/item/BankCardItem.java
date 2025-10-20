package com.saunhardy.createringtoncurrency.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class BankCardItem extends Item {
    
    public BankCardItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        
        // Add basic tooltip
        tooltip.add(Component.literal("Required for Stock Ticker shopping list integration").withStyle(ChatFormatting.GRAY));
        
        // Check if detailed tooltip should be shown (advanced tooltip flag)
        if (flag.isAdvanced()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Usage Instructions:").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("1. Hold shopping list in main hand").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("2. Hold bank card in offhand").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("3. Right-click Stock Ticker entity or Blaze Burner").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("4. Bills will be withdrawn automatically").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Requirements:").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.literal("• Shopping list must contain currency bills").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Sufficient account balance required").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Enough inventory space for bills").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Press F3+H for detailed usage instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}