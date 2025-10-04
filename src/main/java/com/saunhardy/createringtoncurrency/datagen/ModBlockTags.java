package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
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
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CreateringtonCurrency.ATM_BLOCK.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(CreateringtonCurrency.ATM_BLOCK.get());
    }

    @Override
    public @NotNull String getName() { return "CreateringtonCurrency Block Tags"; }
}
