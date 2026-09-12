package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class NbtCash {
    public static final String BILL_PREFIX = "createringtoncurrency:bill_";

    private static final int MAX_DEPTH = 6;
    private static final String CONTAINER = "minecraft:container";
    private static final String BUNDLE = "minecraft:bundle_contents";
    private static final String BLOCK_ENTITY_DATA = "minecraft:block_entity_data";
    private static final String[] ITEM_LISTS = {"Items", "Inventory", "EnderItems", "HandItems", "ArmorItems"};

    public static int denominationIndex(String id) {
        if (id == null || !id.startsWith(BILL_PREFIX)) return -1;
        try {
            return Bills.indexOfDenomination(Integer.parseInt(id.substring(BILL_PREFIX.length())));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void countHolder(CompoundTag holder, int[] into) {
        countHolder(holder, into, 0);
    }

    public static void countItems(ListTag items, int[] into) {
        countItems(items, into, 0);
    }

    public static void countStack(CompoundTag stack, int[] into) {
        countStack(stack, into, 0);
    }

    private static void countHolder(CompoundTag holder, int[] into, int depth) {
        if (holder == null || depth > MAX_DEPTH) return;

        for (String key : ITEM_LISTS) {
            if (holder.contains(key, Tag.TAG_LIST)) countItems(holder.getList(key, Tag.TAG_COMPOUND), into, depth);
        }
        if (holder.contains("Item", Tag.TAG_COMPOUND)) countStack(holder.getCompound("Item"), into, depth);
        if (holder.contains("Passengers", Tag.TAG_LIST)) {
            ListTag passengers = holder.getList("Passengers", Tag.TAG_COMPOUND);
            for (int i = 0; i < passengers.size(); i++) countHolder(passengers.getCompound(i), into, depth + 1);
        }
    }

    private static void countItems(ListTag items, int[] into, int depth) {
        if (items == null || depth > MAX_DEPTH) return;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof CompoundTag stack) countStack(stack, into, depth);
        }
    }

    private static void countStack(CompoundTag stack, int[] into, int depth) {
        if (stack == null || depth > MAX_DEPTH) return;

        int index = denominationIndex(stack.getString("id"));
        if (index >= 0) {
            int count = stack.contains("count", Tag.TAG_INT) ? stack.getInt("count") : 1;
            if (count > 0) into[index] += count;
        }

        if (!stack.contains("components", Tag.TAG_COMPOUND)) return;
        CompoundTag components = stack.getCompound("components");

        if (components.contains(CONTAINER, Tag.TAG_LIST)) {
            ListTag slots = components.getList(CONTAINER, Tag.TAG_COMPOUND);
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                if (slot.contains("item", Tag.TAG_COMPOUND)) countStack(slot.getCompound("item"), into, depth + 1);
            }
        }

        if (components.contains(BUNDLE, Tag.TAG_LIST)) {
            countItems(components.getList(BUNDLE, Tag.TAG_COMPOUND), into, depth + 1);
        }

        if (components.contains(BLOCK_ENTITY_DATA, Tag.TAG_COMPOUND)) {
            countHolder(components.getCompound(BLOCK_ENTITY_DATA), into, depth + 1);
        }
    }

    private NbtCash() {}
}
