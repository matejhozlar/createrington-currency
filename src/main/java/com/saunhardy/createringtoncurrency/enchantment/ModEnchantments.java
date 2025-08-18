package com.saunhardy.createringtoncurrency.enchantment;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, CreateringtonCurrency.MODID);

    public static final RegistryObject<Enchantment> CAPITALIST_GREED =
            ENCHANTMENTS.register("capitalist_greed", CapitalistGreedEnchantment::new);

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
