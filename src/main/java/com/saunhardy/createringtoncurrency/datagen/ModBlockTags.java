package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
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
        Block[] atms = CreateringtonCurrency.DECORATIVE_ATMS.stream()
                .map(DeferredBlock::get)
                .toArray(Block[]::new);
        Block depositor = CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK.get();
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(atms).add(depositor);
        tag(BlockTags.NEEDS_IRON_TOOL).add(atms).add(depositor);
    }

    @Override
    public @NotNull String getName() { return "CreateringtonCurrency Block Tags"; }
}
