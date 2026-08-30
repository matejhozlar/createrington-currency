package com.saunhardy.createringtoncurrency.util;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.text.NumberFormat;

public final class Bills {
    public static final int[] DENOMINATIONS = {1000, 500, 100, 50, 20, 10, 5, 1};

    private Bills() {}

    public static int[] none() {
        return new int[DENOMINATIONS.length];
    }

    public static Item itemFor(int denomination) {
        return switch (denomination) {
            case 1 -> CreateringtonCurrency.BILL_1.get();
            case 5 -> CreateringtonCurrency.BILL_5.get();
            case 10 -> CreateringtonCurrency.BILL_10.get();
            case 20 -> CreateringtonCurrency.BILL_20.get();
            case 50 -> CreateringtonCurrency.BILL_50.get();
            case 100 -> CreateringtonCurrency.BILL_100.get();
            case 500 -> CreateringtonCurrency.BILL_500.get();
            case 1000 -> CreateringtonCurrency.BILL_1000.get();
            default -> throw new IllegalArgumentException("Not a denomination: " + denomination);
        };
    }

    public static int indexOfDenomination(int denomination) {
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (DENOMINATIONS[i] == denomination) return i;
        }
        return -1;
    }

    public static int indexOf(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (stack.is(itemFor(DENOMINATIONS[i]))) return i;
        }
        return -1;
    }

    public static boolean isBill(ItemStack stack) {
        return indexOf(stack) >= 0;
    }

    public static int[] only(int index, int count) {
        int[] counts = none();
        counts[index] = count;
        return counts;
    }

    public static int[] breakdown(int total) {
        int[] counts = none();
        int remaining = total;
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            counts[i] = remaining / DENOMINATIONS[i];
            remaining %= DENOMINATIONS[i];
        }
        return counts;
    }

    public static int[] count(IItemHandler handler) {
        int[] counts = none();
        for (int slot = 0; slot < handler.getSlots(); slot++) add(counts, handler.getStackInSlot(slot));
        return counts;
    }

    public static int[] count(Container container) {
        int[] counts = none();
        for (int slot = 0; slot < container.getContainerSize(); slot++) add(counts, container.getItem(slot));
        return counts;
    }

    private static void add(int[] counts, ItemStack stack) {
        int i = indexOf(stack);
        if (i >= 0) counts[i] += stack.getCount();
    }

    public static long value(int[] counts) {
        long total = 0;
        for (int i = 0; i < DENOMINATIONS.length; i++) total += (long) counts[i] * DENOMINATIONS[i];
        return total;
    }

    public static long pieces(int[] counts) {
        long total = 0;
        for (int c : counts) total += c;
        return total;
    }

    public static int[] missing(int[] required, int[] available) {
        int[] missing = none();
        for (int i = 0; i < DENOMINATIONS.length; i++) missing[i] = Math.max(0, required[i] - available[i]);
        return missing;
    }

    public static boolean isEmpty(int[] counts) {
        for (int c : counts) if (c != 0) return false;
        return true;
    }

    public static int[] insert(IItemHandler handler, int[] counts) {
        int[] leftover = none();
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (counts[i] <= 0) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(itemFor(DENOMINATIONS[i]), counts[i]), false);
            leftover[i] = rest.getCount();
        }
        return leftover;
    }

    public static int[] extract(IItemHandler handler, int[] counts) {
        int[] missing = counts.clone();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            int i = indexOf(handler.getStackInSlot(slot));
            if (i < 0 || missing[i] <= 0) continue;
            missing[i] -= handler.extractItem(slot, missing[i], false).getCount();
        }
        return missing;
    }

    public static int[] extract(Container container, int[] counts) {
        int[] missing = counts.clone();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            int i = indexOf(stack);
            if (i < 0 || missing[i] <= 0) continue;
            int take = Math.min(missing[i], stack.getCount());
            container.removeItem(slot, take);
            missing[i] -= take;
        }
        return missing;
    }

    public static boolean fits(IItemHandler handler, int[] counts) {
        ItemStackHandler sim = new ItemStackHandler(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) sim.setStackInSlot(slot, handler.getStackInSlot(slot).copy());
        return isEmpty(insert(sim, counts));
    }

    public static boolean fitsInventory(Player player, int[] counts) {
        return fits(new PlayerMainInvWrapper(player.getInventory()), counts);
    }

    public static String fmt(long amount) {
        return NumberFormat.getInstance().format(amount);
    }

    public static String fmt(double amount) {
        NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
