package com.saunhardy.createringtoncurrency.datagen;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.loot.BlockLootSubProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class ModBlockLoot extends BlockLootSubProvider {

    public ModBlockLoot(HolderLookup.Provider lookup) {
        // empty set = no custom explosion-resistant items
        // FeatureFlags.VANILLA_SET = default features
        super(Set.of(), FeatureFlags.VANILLA_SET, lookup);
    }

    @Override
    protected void generate() {
        // Tell the game that breaking the ATM drops itself
        this.dropSelf(CreateringtonCurrency.ATM_BLOCK.get());
    }

    @Override
    protected @NotNull Iterable<Block> getKnownBlocks() {
        // All mod blocks that need loot tables
        return java.util.List.of(CreateringtonCurrency.ATM_BLOCK.get());
    }
}
