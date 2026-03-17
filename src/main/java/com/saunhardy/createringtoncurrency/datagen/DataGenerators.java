package com.saunhardy.createringtoncurrency.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

public class DataGenerators {
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        var efh = event.getExistingFileHelper();

        generator.addProvider(event.includeServer(), new ModDatapackProvider(packOutput, lookupProvider));

        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));

        generator.addProvider(event.includeServer(), new ModBlockTags(packOutput, lookupProvider, efh));

        generator.addProvider(event.includeServer(), new ModLootProvider(packOutput, lookupProvider));
    }
}