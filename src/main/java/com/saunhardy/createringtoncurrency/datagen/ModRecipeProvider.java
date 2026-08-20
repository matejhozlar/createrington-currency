package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider{

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output,lookup);
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
    }
}
