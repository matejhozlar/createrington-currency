package com.saunhardy.createringtoncurrency.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillsTest {

    @Test
    void breakdownUsesTheLargestDenominationsFirst() {
        assertArrayEquals(new int[]{1, 0, 2, 0, 1, 1, 0, 4}, Bills.breakdown(1234));
        assertArrayEquals(new int[]{0, 1, 4, 1, 2, 0, 1, 4}, Bills.breakdown(999));
    }

    @Test
    void breakdownRoundTripsThroughValue() {
        for (int total : new int[]{1, 4, 5, 19, 20, 999, 1000, 1234, 65535, Integer.MAX_VALUE}) {
            assertEquals(total, Bills.value(Bills.breakdown(total)));
        }
    }

    @Test
    void breakdownOfZeroIsEmpty() {
        assertTrue(Bills.isEmpty(Bills.breakdown(0)));
    }

    @Test
    void valueDoesNotOverflowInt() {
        assertEquals(1000L * Integer.MAX_VALUE, Bills.value(Bills.only(0, Integer.MAX_VALUE)));
    }

    @Test
    void onlyPlacesTheCountAtTheDenominationIndex() {
        int[] counts = Bills.only(Bills.indexOfDenomination(50), 3);
        assertEquals(150, Bills.value(counts));
        assertEquals(3, Bills.pieces(counts));
        assertEquals(-1, Bills.indexOfDenomination(7));
    }
}
