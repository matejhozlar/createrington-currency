package com.saunhardy.createringtoncurrency.block;

import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DecorativeATMBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final Map<BlockPos, AtomicInteger> USER_COUNTS = new ConcurrentHashMap<>();

    public DecorativeATMBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos above = ctx.getClickedPos().above();
        Level level = ctx.getLevel();
        if (!level.getBlockState(above).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(above)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(POWERED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);

        if (otherState.is(this) && otherState.getValue(HALF) != half) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }

        // Clean up user count
        BlockPos lowerPos = half == DoubleBlockHalf.LOWER ? pos : otherPos;
        USER_COUNTS.remove(lowerPos);

        return super.playerWillDestroy(level, pos, state, player);
    }

    private BlockPos getLowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
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

        BlockPos lowerPos = getLowerPos(pos, state);
        AtomicInteger count = USER_COUNTS.computeIfAbsent(lowerPos, k -> new AtomicInteger(0));
        count.incrementAndGet();
        setPowered(level, lowerPos, true);

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, ply) -> new ATMMenu(id, inv) {
                    @Override
                    public void removed(Player p) {
                        super.removed(p);
                        int remaining = count.decrementAndGet();
                        if (remaining <= 0) {
                            USER_COUNTS.remove(lowerPos);
                            setPowered(level, lowerPos, false);
                        }
                    }
                },
                Component.translatable("menu.createringtoncurrency.atm")
        );
        player.openMenu(provider);

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void setPowered(Level level, BlockPos lowerPos, boolean powered) {
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockPos upperPos = lowerPos.above();
        BlockState upperState = level.getBlockState(upperPos);

        if (lowerState.is(this)) {
            level.setBlock(lowerPos, lowerState.setValue(POWERED, powered), 3);
            level.updateNeighborsAt(lowerPos, this);
        }
        if (upperState.is(this)) {
            level.setBlock(upperPos, upperState.setValue(POWERED, powered), 3);
            level.updateNeighborsAt(upperPos, this);
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        Rotation rot = mirror.getRotation(facing);
        return state.setValue(FACING, rot.rotate(facing));
    }
}
