package com.saunhardy.createringtoncurrency.util;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendingBillsDataTest {
    private static final UUID PLAYER = UUID.fromString("380df991-f603-344c-a090-369bad2a924a");
    private static final UUID OTHER = UUID.fromString("7c1a2f3e-5b6d-4e8f-9a0b-1c2d3e4f5a6b");

    @Test
    void addMergesAndTakeRemoves() {
        PendingBillsData data = new PendingBillsData();
        data.add(PLAYER, Bills.only(0, 2));
        data.add(PLAYER, Bills.only(7, 5));
        data.add(OTHER, Bills.only(3, 1));

        assertArrayEquals(new int[]{2, 0, 0, 0, 0, 0, 0, 5}, data.take(PLAYER));
        assertNull(data.take(PLAYER));
        assertArrayEquals(Bills.only(3, 1), data.take(OTHER));
    }

    @Test
    void saveAndLoadRoundTrip() {
        PendingBillsData data = new PendingBillsData();
        data.add(PLAYER, Bills.breakdown(1234));
        data.add(OTHER, Bills.only(7, 3));

        PendingBillsData loaded = PendingBillsData.load(data.save(new CompoundTag(), null), null);

        assertArrayEquals(Bills.breakdown(1234), loaded.take(PLAYER));
        assertArrayEquals(Bills.only(7, 3), loaded.take(OTHER));
    }

    @Test
    void loadSkipsInvalidAndEmptyEntriesAndPadsShortOnes() {
        CompoundTag pending = new CompoundTag();
        pending.putIntArray("not-a-uuid", new int[]{1});
        pending.putIntArray(PLAYER.toString(), new int[Bills.DENOMINATIONS.length]);
        pending.putIntArray(OTHER.toString(), new int[]{3});
        CompoundTag tag = new CompoundTag();
        tag.put("Pending", pending);

        PendingBillsData loaded = PendingBillsData.load(tag, null);

        assertNull(loaded.take(PLAYER));
        assertArrayEquals(Bills.only(0, 3), loaded.take(OTHER));
    }

    @Test
    void addSaturatesInsteadOfOverflowing() {
        PendingBillsData data = new PendingBillsData();
        data.add(PLAYER, Bills.only(0, Integer.MAX_VALUE));
        data.add(PLAYER, Bills.only(0, 1));

        assertEquals(Integer.MAX_VALUE, data.take(PLAYER)[0]);
    }
}
