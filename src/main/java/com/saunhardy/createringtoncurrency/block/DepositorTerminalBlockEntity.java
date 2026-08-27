package com.saunhardy.createringtoncurrency.block;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_PRICE_DENOMINATION = "PriceDenomination";
    private static final String TAG_PRICE_COUNT = "PriceCount";
    private static final String TAG_STORAGE = "Storage";

    @Nullable private UUID owner;
    private String ownerName = "";
    private int priceDenomination = 0;
    private int priceCount = 0;

    private final ItemStackHandler storage = new ItemStackHandler(STORAGE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return Bills.isBill(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
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
        sync();
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putInt(TAG_PRICE_DENOMINATION, priceDenomination);
        tag.putInt(TAG_PRICE_COUNT, priceCount);
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

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
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
