package com.saunhardy.createringtoncurrency.audit;

import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class NbtCash {
    public static final String BILL_PREFIX = "createringtoncurrency:bill_";

    private static final int MAX_DEPTH = 32;
    private static final Set<String> NOT_PHYSICAL = Set.of("Offers");
    private static final byte[] BILL_MARKER = BILL_PREFIX.getBytes(StandardCharsets.US_ASCII);

    public static boolean mightHoldBills(byte[] serialized) {
        return serialized != null && contains(serialized, BILL_MARKER);
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        if (haystack == null || needle.length == 0 || haystack.length < needle.length) return false;

        byte first = needle[0];
        int last = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= last; i++) {
            if (haystack[i] != first) continue;
            for (int j = 1; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

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
