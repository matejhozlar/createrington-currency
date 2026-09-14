package com.saunhardy.createringtoncurrency.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillsTest {

    private static int[] held(int... denominationCountPairs) {
        int[] counts = Bills.none();
        for (int i = 0; i < denominationCountPairs.length; i += 2) {
            counts[Bills.indexOfDenomination(denominationCountPairs[i])] = denominationCountPairs[i + 1];
        }
        return counts;
    }

    @Test
    void wholeDollarsRoundsToTheCentBeforeFlooring() {
        assertEquals(150L, Bills.wholeDollars(150.0));
        assertEquals(150L, Bills.wholeDollars(149.999999));
        assertEquals(149L, Bills.wholeDollars(149.994));
        assertEquals(149L, Bills.wholeDollars(149.5));
        assertEquals(0L, Bills.wholeDollars(0.004));
    }

    @Test
    void exactChangePrefersTheLargestBillsWhenTheyFit() {
        int[] held = held(100, 1, 50, 2, 20, 5, 1, 3);
        assertArrayEquals(held(100, 1), Bills.exactChange(held, 100));
        assertArrayEquals(held(100, 1, 50, 1), Bills.exactChange(held, 150));
        assertArrayEquals(held(100, 1, 50, 2, 20, 5, 1, 3), Bills.exactChange(held, 303));
    }

    @Test
    void exactChangeBacktracksWhereGreedyWouldGetStuck() {
        int[] held = held(50, 1, 20, 3);
        assertArrayEquals(held(20, 3), Bills.exactChange(held, 60));
        assertArrayEquals(held(50, 1, 20, 1), Bills.exactChange(held, 70));
        assertArrayEquals(held(50, 1, 20, 3), Bills.exactChange(held(100, 1, 50, 1, 20, 3), 110));
    }

    @Test
    void exactChangeIsNullWhenTheBillsCannotMakeTheAmount() {
        assertNull(Bills.exactChange(held(20, 3), 30));
        assertNull(Bills.exactChange(held(20, 3), 80));
        assertNull(Bills.exactChange(held(5, 1, 1, 3), 9));
        assertNull(Bills.exactChange(Bills.none(), 1));
        assertNull(Bills.exactChange(held(1, 5), -1));
    }

    @Test
    void exactChangeOfZeroTakesNothing() {
        int[] pick = Bills.exactChange(held(100, 2, 1, 1), 0);
        assertNotNull(pick);
        assertTrue(Bills.isEmpty(pick));
    }

    @Test
    void exactChangeNeverExceedsWhatIsHeldAndAlwaysSumsToTheAmount() {
        int[] held = held(1000, 3, 500, 1, 100, 7, 50, 2, 20, 9, 10, 4, 5, 1, 1, 12);
        for (int amount = 0; amount <= Bills.value(held); amount++) {
            int[] pick = Bills.exactChange(held, amount);
            if (pick == null) continue;
            assertEquals(amount, Bills.value(pick));
            assertTrue(Bills.isEmpty(Bills.missing(pick, held)));
        }
    }

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
    void missingIsTheShortfallPerDenominationAndNeverNegative() {
        int[] required = Bills.breakdown(1234);
        int[] available = Bills.only(2, 5);
        available[7] = 1;

        int[] missing = Bills.missing(required, available);

        assertArrayEquals(new int[]{1, 0, 0, 0, 1, 1, 0, 3}, missing);
        assertTrue(Bills.isEmpty(Bills.missing(required, required)));
        assertTrue(Bills.isEmpty(Bills.missing(Bills.none(), available)));
    }

    @Test
    void onlyPlacesTheCountAtTheDenominationIndex() {
        int[] counts = Bills.only(Bills.indexOfDenomination(50), 3);
        assertEquals(150, Bills.value(counts));
        assertEquals(3, Bills.pieces(counts));
        assertEquals(-1, Bills.indexOfDenomination(7));
    }
}
