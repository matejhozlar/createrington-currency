package com.saunhardy.createringtoncurrency.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class CapitalistGreedEnchantment extends Enchantment {

    public CapitalistGreedEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 5 + (level - 1) * 7;
    }

    @Override
    public int getMaxCost(int level) {
        return 25 + (level - 1) * 7;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }
}
