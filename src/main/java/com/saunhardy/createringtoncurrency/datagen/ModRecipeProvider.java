package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ModRecipeProvider extends RecipeProvider {
    private record Merge(int output, int... inputs) {}

    private record Split(int input, int output, int count) {}

    private static final List<Merge> MERGES = List.of(
            new Merge(5, 1, 1, 1, 1, 1),
            new Merge(10, 5, 5),
            new Merge(20, 10, 10),
            new Merge(50, 10, 10, 10, 10, 10),
            new Merge(50, 20, 20, 10),
            new Merge(100, 50, 50),
            new Merge(100, 20, 20, 20, 20, 20),
            new Merge(500, 100, 100, 100, 100, 100),
            new Merge(1000, 500, 500)
    );

    private static final List<Split> SPLITS = List.of(
            new Split(5, 1, 5),
            new Split(10, 5, 2),
            new Split(20, 10, 2),
            new Split(50, 10, 5),
            new Split(100, 50, 2),
            new Split(500, 100, 5),
            new Split(1000, 500, 2)
    );

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput out) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, CreateringtonCurrency.CIRCUIT_BOARD.get())
                .pattern("PG")
                .pattern("RI")
                .define('P', Items.COPPER_INGOT)
                .define('G', Items.GOLD_INGOT)
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(out);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, CreateringtonCurrency.KEYPAD.get())
                .pattern("BBB")
                .pattern(" I ")
                .define('B', Items.STONE_BUTTON)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_button", has(Items.STONE_BUTTON))
                .save(out);

        for (Merge merge : MERGES) {
            Map<Integer, Integer> counts = new LinkedHashMap<>();
            for (int input : merge.inputs()) counts.merge(input, 1, Integer::sum);

            ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Bills.itemFor(merge.output()));
            counts.forEach((denomination, count) -> builder.requires(Bills.itemFor(denomination), count));

            int first = merge.inputs()[0];
            String suffix = counts.entrySet().stream()
                    .map(e -> e.getValue() + "x" + e.getKey())
                    .collect(Collectors.joining("_"));
            builder.unlockedBy("has_bill_" + first, has(Bills.itemFor(first)))
                    .save(out, id("bill_" + merge.output() + "_from_" + suffix));
        }

        for (Split split : SPLITS) {
            Item input = Bills.itemFor(split.input());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Bills.itemFor(split.output()), split.count())
                    .requires(input)
                    .unlockedBy("has_bill_" + split.input(), has(input))
                    .save(out, id("bill_" + split.input() + "_to_" + split.count() + "x" + split.output()));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateringtonCurrency.MODID, path);
    }
}
