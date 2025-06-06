package com.createrington.currency.enchantment;

import com.mojang.serialization.MapCodec;
import com.createrington.currency.CreateringtonCurrency;
import com.createrington.currency.enchantment.custom.CapitalistGreedEnchantmentEffect;
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