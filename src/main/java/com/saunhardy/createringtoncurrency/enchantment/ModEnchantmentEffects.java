package com.saunhardy.createringtoncurrency.enchantment;

import com.mojang.serialization.MapCodec;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.enchantment.custom.CapitalistGreedEnchantmentEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEnchantmentEffects {
    public static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ENTITY_ENCHANTMENT_EFFECTS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, CreateringtonCurrency.MODID);

    public static final Supplier<MapCodec<? extends EnchantmentEntityEffect>> CAPITAL_GREED =
            ENTITY_ENCHANTMENT_EFFECTS.register("capitalist_greed", () -> CapitalistGreedEnchantmentEffect.CODEC);

    public static void register(IEventBus eventBus) {
        ENTITY_ENCHANTMENT_EFFECTS.register(eventBus);
    }
}