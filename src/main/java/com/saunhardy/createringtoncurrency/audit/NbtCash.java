package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

public final class NbtCash {
    public static final String BILL_PREFIX = "createringtoncurrency:bill_";

    private static final int MAX_DEPTH = 32;
    private static final Set<String> NOT_PHYSICAL = Set.of("Offers");

    public static int denominationIndex(String id) {
        if (id == null || !id.startsWith(BILL_PREFIX)) return -1;
        try {
            return Bills.indexOfDenomination(Integer.parseInt(id.substring(BILL_PREFIX.length())));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean count(CompoundTag tag, int[] into) {
        return count(tag, into, 0);
    }

    private static boolean count(CompoundTag tag, int[] into, int depth) {
        if (tag == null) return false;
        if (depth > MAX_DEPTH) return true;

        int index = denominationIndex(tag.getString("id"));
        if (index >= 0) {
            int count = tag.contains("count", Tag.TAG_INT) ? tag.getInt("count") : 1;
            if (count > 0) into[index] += count;
        }

        boolean truncated = false;
        for (String key : tag.getAllKeys()) {
            if (NOT_PHYSICAL.contains(key)) continue;

            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) truncated |= count(compound, into, depth + 1);
            else if (child instanceof ListTag list) truncated |= count(list, into, depth + 1);
        }

        return truncated;
    }

    private static boolean count(ListTag list, int[] into, int depth) {
        if (depth > MAX_DEPTH) return true;

        boolean truncated = false;
        for (int i = 0; i < list.size(); i++) {
            Tag entry = list.get(i);
            if (entry instanceof CompoundTag compound) truncated |= count(compound, into, depth + 1);
            else if (entry instanceof ListTag nested) truncated |= count(nested, into, depth + 1);
        }

        return truncated;
    }

    private NbtCash() {}
}
