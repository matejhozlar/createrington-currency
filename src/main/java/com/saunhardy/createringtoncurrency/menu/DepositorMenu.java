package com.saunhardy.createringtoncurrency.menu;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlockEntity;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** The owner's view of a depositor terminal. Customers never open a menu; they pay by right-clicking the block. */
public class DepositorMenu extends AbstractContainerMenu {
    public static final int STORAGE_SLOTS = DepositorTerminalBlockEntity.STORAGE_SLOTS;

    public static final int STORAGE_SLOT_X = 27, STORAGE_SLOT_Y = 71;
    public static final int INV_SLOT_X = 27, INV_SLOT_Y = 97, HOTBAR_SLOT_Y = 155;

    private final BlockPos pos;
    private final int priceDenomination;
    private final int priceCount;
    private final String ownerName;
    private final IItemHandler storage;

    public DepositorMenu(int id, Inventory inv, DepositorTerminalBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.getPriceDenomination(), be.getPriceCount(), be.getOwnerName(), be.getStorage());
    }

    public static DepositorMenu fromBuf(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int priceDenomination = buf.readVarInt();
        int priceCount = buf.readVarInt();
        String ownerName = buf.readUtf();
        IItemHandler storage = inv.player.level().getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be
                ? be.getStorage()
                : new ItemStackHandler(STORAGE_SLOTS);
        return new DepositorMenu(id, inv, pos, priceDenomination, priceCount, ownerName, storage);
    }

    private DepositorMenu(int id, Inventory inv, BlockPos pos, int priceDenomination, int priceCount,
                          String ownerName, IItemHandler storage) {
        super(CreateringtonCurrency.DEPOSITOR_MENU.get(), id);
        this.pos = pos;
        this.priceDenomination = priceDenomination;
        this.priceCount = priceCount;
        this.ownerName = ownerName;
        this.storage = storage;

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            addSlot(new SlotItemHandler(storage, i, STORAGE_SLOT_X + i * 18, STORAGE_SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, INV_SLOT_X + col * 18, INV_SLOT_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_SLOT_X + col * 18, HOTBAR_SLOT_Y));
        }
    }

    public BlockPos getPos() { return pos; }
    public int getPriceDenomination() { return priceDenomination; }
    public int getPriceCount() { return priceCount; }
    public String getOwnerName() { return ownerName; }
    public IItemHandler getStorage() { return storage; }

    public int getPrice() {
        return priceCount > 0 && Bills.indexOfDenomination(priceDenomination) >= 0 ? priceDenomination * priceCount : 0;
    }

    /**
     * The terminal must still exist and still be configurable by this player. Without the block check a broken terminal
     * would leave the menu bound to an orphaned item handler, letting the bills that were just dropped be taken out again.
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().getBlockEntity(pos) instanceof DepositorTerminalBlockEntity be
                && be.canConfigure(player)
                && player.distanceToSqr(pos.getCenter()) <= DepositorTerminalBlock.MAX_USE_DISTANCE_SQ;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < STORAGE_SLOTS) {
            if (!moveItemStackTo(stack, STORAGE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!Bills.isBill(stack) || !moveItemStackTo(stack, 0, STORAGE_SLOTS, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
