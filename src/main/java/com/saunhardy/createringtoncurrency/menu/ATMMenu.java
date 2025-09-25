package com.saunhardy.createringtoncurrency.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ATMMenu extends AbstractContainerMenu {
    public ATMMenu(int id, Inventory ignoredPlayerInv) {
        super(com.saunhardy.createringtoncurrency.CreateringtonCurrency.ATM_MENU.get(), id);
    }

    @Override public boolean stillValid(@NotNull Player player) { return true; }
    @Override public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) { return ItemStack.EMPTY; }
}
