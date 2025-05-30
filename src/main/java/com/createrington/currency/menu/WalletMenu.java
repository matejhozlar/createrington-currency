package com.createrington.currency.menu;

import com.createrington.currency.CreateringtonCurrency;

import com.createrington.currency.items.WalletItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class WalletMenu extends AbstractContainerMenu {
    private final IItemHandler walletInventory;

    // Server-side constructor: uses the actual wallet item handler
    public WalletMenu(int id, Inventory playerInv, IItemHandler walletHandler) {
        super(CreateringtonCurrency.WALLET_MENU_TYPE.get(), id);
        this.walletInventory = walletHandler;

        // Wallet inventory slots (8 slots, arranged in 2 rows of 4 for example)
        int index = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                int x = 44 + col * 18;  // example positioning
                int y = 20 + row * 18;
                this.addSlot(new SlotItemHandler(walletInventory, index++, x, y));
            }
        }

        // Player inventory slots (3 rows of 9)
        int startY = 60;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = 8 + col * 18;
                int y = startY + row * 18;
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, x, y));
            }
        }
        // Player hotbar slots (9 slots)
        for (int col = 0; col < 9; ++col) {
            int x = 8 + col * 18;
            int y = startY + 58;
            this.addSlot(new Slot(playerInv, col, x, y));
        }
    }

    public WalletMenu(int id, Inventory playerInv) {
        // just forward to your “real” ctor with a fresh 8-slot handler
        this(id, playerInv, new ItemStackHandler(8));
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        // Optionally, ensure the player still has the wallet open
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        // Implement shift-click move logic:
        // If the clicked slot is in wallet (index 0-7), transfer to player inv (index 8+).
        // If the slot is in player inventory, and it’s a bill, move to wallet.
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            if (index < 8) {
                // from wallet to player
                if (!this.moveItemStackTo(stack, 8, this.slots.size(), true))
                    return ItemStack.EMPTY;
            } else {
                // from player to wallet (only if valid bill)
                if (stack.getItem() instanceof WalletItem) {
                    return ItemStack.EMPTY; // don't allow moving the wallet itself
                }
                if (walletInventory instanceof ItemStackHandler handler && handler.isItemValid(0, stack)) {
                    // move into wallet slots 0-8
                    if (!this.moveItemStackTo(stack, 0, 8, false))
                        return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, stack);
        }
        return original;
    }
}
