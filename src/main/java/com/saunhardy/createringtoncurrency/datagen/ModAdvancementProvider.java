package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.advancement.EconomyTrigger;
import com.saunhardy.createringtoncurrency.advancement.EconomyTrigger.Event;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantments;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemEnchantmentsPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicates;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ModAdvancementProvider extends AdvancementProvider {
    public static final int HIGH_ROLLER_AMOUNT = 10_000;

    public ModAdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                                  ExistingFileHelper existingFileHelper) {
        super(output, registries, existingFileHelper, List.of(new Economy()));
    }

    private static class Economy implements AdvancementProvider.AdvancementGenerator {
        private Consumer<AdvancementHolder> saver;

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver,
                             ExistingFileHelper existingFileHelper) {
            this.saver = saver;

            AdvancementHolder root = Advancement.Builder.advancement()
                    .display(CreateringtonCurrency.BILL_100.get(), title("root"), description("root"),
                            ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png"),
                            AdvancementType.TASK, false, false, false)
                    .addCriterion("has_bill", InventoryChangeTrigger.TriggerInstance.hasItems(
                            ItemPredicate.Builder.item().of(
                                    CreateringtonCurrency.BILL_1.get(), CreateringtonCurrency.BILL_5.get(),
                                    CreateringtonCurrency.BILL_10.get(), CreateringtonCurrency.BILL_20.get(),
                                    CreateringtonCurrency.BILL_50.get(), CreateringtonCurrency.BILL_100.get(),
                                    CreateringtonCurrency.BILL_500.get(), CreateringtonCurrency.BILL_1000.get())))
                    .save(saver, id("root"));

            AdvancementHolder grand = task(root, "grand", CreateringtonCurrency.BILL_1000.get(), AdvancementType.TASK,
                    "has_bill_1000", InventoryChangeTrigger.TriggerInstance.hasItems(CreateringtonCurrency.BILL_1000.get()));
            task(grand, "high_roller", CreateringtonCurrency.BILL_500.get(), AdvancementType.CHALLENGE,
                    "carried", EconomyTrigger.Instance.atLeast(Event.CARRY, HIGH_ROLLER_AMOUNT));

            AdvancementHolder card = task(root, "bank_card", CreateringtonCurrency.BANK_CARD.get(), AdvancementType.TASK,
                    "has_bank_card", InventoryChangeTrigger.TriggerInstance.hasItems(CreateringtonCurrency.BANK_CARD.get()));
            task(card, "deposit", CreateringtonCurrency.ATM_BLUE_ITEM.get(), AdvancementType.TASK,
                    "deposited", EconomyTrigger.Instance.of(Event.DEPOSIT));
            task(card, "withdraw", CreateringtonCurrency.ATM_GREEN_ITEM.get(), AdvancementType.TASK,
                    "withdrew", EconomyTrigger.Instance.of(Event.WITHDRAW));

            task(root, "pay", CreateringtonCurrency.BILL_20.get(), AdvancementType.TASK,
                    "paid", EconomyTrigger.Instance.of(Event.PAY));
            AdvancementHolder shop = task(root, "set_price", CreateringtonCurrency.DEPOSITOR_TERMINAL_ITEM.get(),
                    AdvancementType.TASK, "set_price", EconomyTrigger.Instance.of(Event.SET_PRICE));
            task(shop, "sale", CreateringtonCurrency.BILL_50.get(), AdvancementType.GOAL,
                    "sold", EconomyTrigger.Instance.of(Event.SALE));

            Holder<Enchantment> greed = registries.lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ModEnchantments.CAPITALIST_GREED);
            List<EnchantmentPredicate> greedy = List.of(new EnchantmentPredicate(greed, MinMaxBounds.Ints.atLeast(1)));
            AdvancementHolder greedIsGood = Advancement.Builder.advancement()
                    .parent(root)
                    .display(Items.GOLDEN_SWORD, title("capitalist_greed"), description("capitalist_greed"),
                            null, AdvancementType.TASK, true, true, false)
                    .addCriterion("enchanted", InventoryChangeTrigger.TriggerInstance.hasItems(
                            ItemPredicate.Builder.item().withSubPredicate(ItemSubPredicates.ENCHANTMENTS,
                                    ItemEnchantmentsPredicate.enchantments(greedy))))
                    .addCriterion("book", InventoryChangeTrigger.TriggerInstance.hasItems(
                            ItemPredicate.Builder.item().withSubPredicate(ItemSubPredicates.STORED_ENCHANTMENTS,
                                    ItemEnchantmentsPredicate.storedEnchantments(greedy))))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .save(saver, id("capitalist_greed"));
            task(greedIsGood, "mob_cap", Items.ZOMBIE_HEAD, AdvancementType.GOAL,
                    "hit_cap", EconomyTrigger.Instance.of(Event.MOB_CAP));
        }

        private AdvancementHolder task(AdvancementHolder parent, String name, ItemLike icon, AdvancementType type,
                                       String criterionName, Criterion<?> criterion) {
            return Advancement.Builder.advancement()
                    .parent(parent)
                    .display(icon, title(name), description(name), null, type, true, true, false)
                    .addCriterion(criterionName, criterion)
                    .save(saver, id(name));
        }

        private static String id(String name) {
            return CreateringtonCurrency.MODID + ":economy/" + name;
        }

        private static Component title(String name) {
            return Component.translatable("advancements." + CreateringtonCurrency.MODID + ".economy." + name + ".title");
        }

        private static Component description(String name) {
            return Component.translatable("advancements." + CreateringtonCurrency.MODID + ".economy." + name + ".description");
        }
    }
}
