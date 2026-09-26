package com.saunhardy.createringtoncurrency.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

public class EconomyTrigger extends SimpleCriterionTrigger<EconomyTrigger.Instance> {
    public enum Event implements StringRepresentable {
        DEPOSIT("deposit"),
        WITHDRAW("withdraw"),
        PAY("pay"),
        SALE("sale"),
        SET_PRICE("set_price"),
        MOB_CAP("mob_cap"),
        CARRY("carry");

        public static final Codec<Event> CODEC = StringRepresentable.fromEnum(Event::values);

        private final String name;

        Event(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Event event, long amount) {
        int clamped = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, amount));
        trigger(player, instance -> instance.matches(event, clamped));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Event event, MinMaxBounds.Ints amount)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Event.CODEC.fieldOf("event").forGetter(Instance::event),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("amount", MinMaxBounds.Ints.ANY).forGetter(Instance::amount)
        ).apply(i, Instance::new));

        public static Criterion<Instance> of(Event event) {
            return ModTriggers.ECONOMY.get().createCriterion(new Instance(Optional.empty(), event, MinMaxBounds.Ints.ANY));
        }

        public static Criterion<Instance> atLeast(Event event, int amount) {
            return ModTriggers.ECONOMY.get().createCriterion(
                    new Instance(Optional.empty(), event, MinMaxBounds.Ints.atLeast(amount)));
        }

        boolean matches(Event event, int amount) {
            return this.event == event && this.amount.matches(amount);
        }
    }
}
