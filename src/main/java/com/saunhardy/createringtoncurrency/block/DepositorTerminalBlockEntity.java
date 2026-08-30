package com.saunhardy.createringtoncurrency.block;

import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DepositorTerminalBlockEntity extends BlockEntity {
    public static final int STORAGE_SLOTS = 9;
    /** Bills stack to 64, so this is the most bills the storage can ever hold and the most a single price may ask for. */
    public static final int MAX_BILLS = STORAGE_SLOTS * 64;

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_PRICE_DENOMINATION = "PriceDenomination";
    private static final String TAG_PRICE_COUNT = "PriceCount";
    private static final String TAG_STORAGE = "Storage";

    @Nullable private UUID owner;
    private String ownerName = "";
    private int priceDenomination = 0;
    private int priceCount = 0;

    /** Game time at which the current redstone pulse ends. Not persisted: a pulse interrupted by a chunk unload just ends on reload. */
    private long poweredUntil;
    /** A payment landed while a pulse was still running: the signal was dropped for one tick and must be raised again. */
    private boolean raisePending;
    private boolean lightDirty = true;

    private final ItemStackHandler storage = new ItemStackHandler(STORAGE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return Bills.isBill(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            lightDirty = true;
        }
    };
    private final IItemHandler extractOnly = new ExtractOnly(storage);

    public DepositorTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public int getPriceDenomination() { return priceDenomination; }
    public int getPriceCount() { return priceCount; }
    public ItemStackHandler getStorage() { return storage; }

    public int getPrice() {
        return hasPrice() ? priceDenomination * priceCount : 0;
    }

    public boolean hasPrice() {
        return priceCount > 0 && Bills.indexOfDenomination(priceDenomination) >= 0;
    }

    public IItemHandler getExtractOnlyStorage() { return extractOnly; }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public boolean canConfigure(Player player) {
        return isOwner(player) || player.hasPermissions(2);
    }

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        sync();
    }

    public void setPrice(int denomination, int count) {
        this.priceDenomination = denomination;
        this.priceCount = Math.max(0, count);
        lightDirty = true;
        sync();
    }

    /** Drops every stored bill on the ground and empties the storage, so a menu still bound to it cannot hand the bills out again. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            storage.setStackInSlot(slot, ItemStack.EMPTY);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    /**
     * Fires the redstone pulse for a completed payment. If a pulse is already running the signal is dropped for one tick
     * and raised again by {@link #serverTick}, so every payment produces its own rising edge.
     */
    public void pulse() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof DepositorTerminalBlock block)) return;

        poweredUntil = serverLevel.getGameTime() + Config.DEPOSITOR_PULSE_TICKS.get();
        if (state.getValue(DepositorTerminalBlock.POWERED)) {
            block.setPowered(serverLevel, worldPosition, state, false);
            raisePending = true;
        } else {
            block.setPowered(serverLevel, worldPosition, state, true);
        }
    }

    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof DepositorTerminalBlock block)) return;
        boolean powered = state.getValue(DepositorTerminalBlock.POWERED);
        if (raisePending) {
            raisePending = false;
            if (!powered) block.setPowered(level, pos, state, true);
        } else if (powered && level.getGameTime() >= poweredUntil) {
            block.setPowered(level, pos, state, false);
        }

        if (lightDirty) {
            lightDirty = false;
            BlockState current = level.getBlockState(pos);
            if (current.is(block)) block.setLight(level, pos, current, currentLight());
        }
    }

    private DepositorTerminalBlock.Light currentLight() {
        if (!hasPrice()) return DepositorTerminalBlock.Light.OFF;
        int[] payment = Bills.only(Bills.indexOfDenomination(priceDenomination), priceCount);
        return Bills.fits(storage, payment) ? DepositorTerminalBlock.Light.READY : DepositorTerminalBlock.Light.FULL;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void saveSyncedData(CompoundTag tag) {
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putInt(TAG_PRICE_DENOMINATION, priceDenomination);
        tag.putInt(TAG_PRICE_COUNT, priceCount);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        saveSyncedData(tag);
        tag.put(TAG_STORAGE, storage.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.getString(TAG_OWNER_NAME);
        priceDenomination = tag.getInt(TAG_PRICE_DENOMINATION);
        priceCount = tag.getInt(TAG_PRICE_COUNT);
        if (tag.contains(TAG_STORAGE)) storage.deserializeNBT(registries, tag.getCompound(TAG_STORAGE));
    }

    /** Clients only need the owner and the price (overlay, menu validation); the takings stay on the server. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveSyncedData(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static final class ExtractOnly implements IItemHandler {
        private final IItemHandler inner;

        ExtractOnly(IItemHandler inner) {
            this.inner = inner;
        }

        @Override public int getSlots() { return inner.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return inner.getStackInSlot(slot); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return inner.extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return inner.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
    }
}
