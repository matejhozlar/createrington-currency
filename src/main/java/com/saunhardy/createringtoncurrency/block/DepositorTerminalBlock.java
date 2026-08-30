package com.saunhardy.createringtoncurrency.block;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.menu.DepositorMenu;
import com.saunhardy.createringtoncurrency.network.DepositorNetworking;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DepositorTerminalBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Light> LIGHT = EnumProperty.create("light", Light.class);

    public static final int LED_TINT_INDEX = 0;
    public static final int LED_FLASH_COLOR = 0xDCB05A;

    public static final double MAX_USE_DISTANCE_SQ = 64.0;

    public DepositorTerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(LIGHT, Light.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be) {
            be.setOwner(player);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositorTerminalBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || type != CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DepositorTerminalBlockEntity>)
                (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    protected void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, Level level,
                                                       @NotNull BlockPos pos, @NotNull Player player,
                                                       @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (be.getOwner() == null || be.canConfigure(player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(CreateringtonCurrency.BANK_CARD.get())) {
            DepositorNetworking.pay(serverPlayer, be, true);
            return ItemInteractionResult.SUCCESS;
        }
        if (Bills.isBill(stack)) {
            DepositorNetworking.pay(serverPlayer, be, false);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be)) {
            return InteractionResult.PASS;
        }

        if (be.getOwner() == null) {
            be.setOwner(player);
            player.sendSystemMessage(Component.literal("You are now the owner of this depositor terminal.")
                    .withStyle(ChatFormatting.GOLD));
        }

        if (be.canConfigure(player)) {
            if (!be.isOwner(player)) {
                player.sendSystemMessage(Component.literal("Admin mode: opened " + be.getOwnerName() + "'s terminal.")
                        .withStyle(ChatFormatting.GOLD));
            }
            openOwnerMenu(serverPlayer, be);
        } else {
            DepositorNetworking.hint(serverPlayer, be);
        }
        return InteractionResult.CONSUME;
    }

    private void openOwnerMenu(ServerPlayer player, DepositorTerminalBlockEntity be) {
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> new DepositorMenu(id, inv, be),
                        Component.translatable("menu.createringtoncurrency.depositor_terminal")),
                buf -> {
                    buf.writeBlockPos(be.getBlockPos());
                    buf.writeVarInt(be.getPriceDenomination());
                    buf.writeVarInt(be.getPriceCount());
                    buf.writeUtf(be.getOwnerName());
                });
    }

    /** Pulse timing lives in {@link DepositorTerminalBlockEntity#pulse()}; this only flips the state and notifies neighbours. */
    void setPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        BlockState next = state.setValue(POWERED, powered);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        updateNeighbours(level, pos, next);
    }

    void setLight(ServerLevel level, BlockPos pos, BlockState state, Light light) {
        if (state.getValue(LIGHT) == light) return;
        level.setBlock(pos, state.setValue(LIGHT, light), Block.UPDATE_CLIENTS);
    }

    private void updateNeighbours(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
    }

    @Override
    protected boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    /** Like an observer: only the block behind the terminal is powered (strongly), never the sides or the front. */
    @Override
    protected int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return state.getValue(POWERED) && state.getValue(FACING) == direction ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be
                ? ItemHandlerHelper.calcRedstoneFromInventory(be.getStorage())
                : 0;
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        return state.setValue(FACING, mirror.getRotation(facing).rotate(facing));
    }

    public enum Light implements StringRepresentable {
        OFF("off", 0x141414),
        READY("ready", 0x1E9C24),
        FULL("full", 0xE03030);

        private final String name;
        private final int color;

        Light(String name, int color) {
            this.name = name;
            this.color = color;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        public int color() {
            return color;
        }
    }
}
