package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class LiveCash {
    private static final int MAX_DEPTH = 6;

    public static void count(Container container, int[] into) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count(container.getItem(slot), into, 0);
        }
    }

    public static void count(ItemStack stack, int[] into) {
        count(stack, into, 0);
    }

    private static void count(ItemStack stack, int[] into, int depth) {
        if (stack.isEmpty() || depth > MAX_DEPTH) return;

        int index = Bills.indexOf(stack);
        if (index >= 0) into[index] += stack.getCount();

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack nested : contents.nonEmptyItems()) count(nested, into, depth + 1);
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack nested : bundle.items()) count(nested, into, depth + 1);
        }
    }

    private LiveCash() {}
}
