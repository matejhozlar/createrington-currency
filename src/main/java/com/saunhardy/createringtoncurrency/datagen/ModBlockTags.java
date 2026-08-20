package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModBlockTags extends BlockTagsProvider {
    public ModBlockTags(PackOutput output,
                        CompletableFuture<HolderLookup.Provider> lookup,
                        ExistingFileHelper efh) {
        super(output, lookup, CreateringtonCurrency.MODID, efh);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        Block[] atms = {
                CreateringtonCurrency.ATM_BLUE_BLOCK.get(),
                CreateringtonCurrency.ATM_GREEN_BLOCK.get(),
                CreateringtonCurrency.ATM_PURPLE_BLOCK.get(),
                CreateringtonCurrency.ATM_BLACK_BLOCK.get(),
                CreateringtonCurrency.ATM_BRASS_BLOCK.get(),
                CreateringtonCurrency.ATM_ANDESITE_BLOCK.get(),
                CreateringtonCurrency.ATM_RED_BLOCK.get(),
                CreateringtonCurrency.ATM_PINK_BLOCK.get()
        };
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(atms);
        tag(BlockTags.NEEDS_IRON_TOOL).add(atms);
    }

    @Override
    public @NotNull String getName() { return "CreateringtonCurrency Block Tags"; }
}
