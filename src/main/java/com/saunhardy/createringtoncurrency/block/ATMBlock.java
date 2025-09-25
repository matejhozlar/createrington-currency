package com.saunhardy.createringtoncurrency.block;

import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;


public class ATMBlock extends Block {
    public ATMBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state,
            Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, ply) -> new ATMMenu(id, inv),
                Component.translatable("menu.createringtoncurrency.atm")
        );
        player.openMenu(provider);

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}
