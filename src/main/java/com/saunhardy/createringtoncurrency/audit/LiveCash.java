package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class LiveCash {
    private static final int MAX_DEPTH = 8;

    public static boolean count(Container container, int[] into) {
        boolean truncated = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            truncated |= count(container.getItem(slot), into, 0);
        }
        return truncated;
    }

    public static boolean count(ItemStack stack, int[] into) {
        return count(stack, into, 0);
    }

    private static boolean count(ItemStack stack, int[] into, int depth) {
        if (stack.isEmpty()) return false;
        if (depth > MAX_DEPTH) return true;

        int index = Bills.indexOf(stack);
        if (index >= 0) into[index] += stack.getCount();

        boolean truncated = false;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack nested : contents.nonEmptyItems()) truncated |= count(nested, into, depth + 1);
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack nested : bundle.items()) truncated |= count(nested, into, depth + 1);
        }

        return truncated;
    }

    private LiveCash() {}
}
