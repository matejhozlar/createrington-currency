package com.saunhardy.createringtoncurrency.advancement;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModTriggers {
    private static final int CARRY_CHECK_TICKS = 20;

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, CreateringtonCurrency.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, EconomyTrigger> ECONOMY =
            TRIGGERS.register("economy", EconomyTrigger::new);

    public static void register(IEventBus eventBus) {
        TRIGGERS.register(eventBus);
    }

    public static void economy(ServerPlayer player, EconomyTrigger.Event event, long amount) {
        if (player instanceof FakePlayer) return;
        ECONOMY.get().trigger(player, event, amount);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()) return;
        if (player.tickCount % CARRY_CHECK_TICKS != 0) return;
        economy(player, EconomyTrigger.Event.CARRY, Bills.value(Bills.count(player.getInventory())));
    }
}
