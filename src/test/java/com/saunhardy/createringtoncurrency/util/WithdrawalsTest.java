package com.saunhardy.createringtoncurrency.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WithdrawalsTest {

    @Test
    void rejectsAnArrayOfTheWrongLength() {
        assertEquals(Withdrawals.MALFORMED, Withdrawals.validate(new int[3]));
        assertEquals(Withdrawals.MALFORMED, Withdrawals.validate(new int[0]));
    }

    @Test
    void rejectsNegativeCounts() {
        int[] counts = Bills.only(0, 1);
        counts[2] = -1;
        assertEquals(Withdrawals.MALFORMED, Withdrawals.validate(counts));
    }

    @Test
    void rejectsAnEmptyRequest() {
        assertEquals(Withdrawals.NOTHING, Withdrawals.validate(Bills.none()));
    }

    @Test
    void rejectsMoreBillsThanAnInventoryHolds() {
        assertEquals(Withdrawals.noRoom(Withdrawals.MAX_BILLS + 1L),
                Withdrawals.validate(Bills.only(7, Withdrawals.MAX_BILLS + 1)));

        int[] huge = Bills.only(0, Integer.MAX_VALUE);
        huge[1] = Integer.MAX_VALUE;
        assertEquals(Withdrawals.noRoom(2L * Integer.MAX_VALUE), Withdrawals.validate(huge));
    }

    @Test
    void acceptsRequestsThatFitAnInventory() {
        assertNull(Withdrawals.validate(Bills.only(0, Withdrawals.MAX_BILLS)));
        assertNull(Withdrawals.validate(Bills.breakdown(1234)));
        assertNull(Withdrawals.validate(Bills.only(7, 1)));
    }
}
