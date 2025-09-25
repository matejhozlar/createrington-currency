package com.saunhardy.createringtoncurrency.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ATMMenu extends AbstractContainerMenu {

    public ATMMenu(int id, Inventory playerInv) {
        super(com.saunhardy.createringtoncurrency.CreateringtonCurrency.ATM_MENU.get(), id);

        // --- Player inventory (3 rows) ---
        int startX = 8;
        int startY = 84;
        int slotSize = 18;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        startX + col * slotSize,
                        startY + row * slotSize));
            }
        }

        // --- Hotbar ---
        int hotbarY = 142;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, startX + col * slotSize, hotbarY));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }
}
