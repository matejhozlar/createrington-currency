package com.createrington.currency.enchantment.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public record CapitalistGreedEnchantmentEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<CapitalistGreedEnchantmentEffect> CODEC = MapCodec.unit(CapitalistGreedEnchantmentEffect::new);

    @Override
    public void apply(@NotNull ServerLevel serverLevel, int enchantmentLevel, @NotNull EnchantedItemInUse enchantedItemInUse, @NotNull Entity entity, @NotNull Vec3 vec3) {}

    @Override
    public @NotNull MapCodec<? extends EnchantmentEntityEffect> codec() {
        return CODEC;
    }
}