package com.saunhardy.createringtoncurrency.mobdrops;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyPeriodTest {

    @Test
    void midnightResetKeysOnTheUtcDate() {
        assertEquals(LocalDate.of(2026, 10, 24), DailyPeriod.key(Instant.parse("2026-10-24T00:00:00Z"), 0));
        assertEquals(LocalDate.of(2026, 10, 24), DailyPeriod.key(Instant.parse("2026-10-24T23:59:59Z"), 0));
        assertEquals(LocalDate.of(2026, 10, 25), DailyPeriod.key(Instant.parse("2026-10-25T00:00:00Z"), 0));
    }

    @Test
    void beforeTheResetHourStillBelongsToThePreviousPeriod() {
        assertEquals(LocalDate.of(2026, 10, 23), DailyPeriod.key(Instant.parse("2026-10-24T05:59:59Z"), 6));
        assertEquals(LocalDate.of(2026, 10, 24), DailyPeriod.key(Instant.parse("2026-10-24T06:00:00Z"), 6));
        assertEquals(LocalDate.of(2026, 10, 24), DailyPeriod.key(Instant.parse("2026-10-25T05:59:59Z"), 6));
    }

    @Test
    void firstOfMonthBeforeResetRollsBackIntoThePreviousMonth() {
        assertEquals(LocalDate.of(2026, 9, 30), DailyPeriod.key(Instant.parse("2026-10-01T02:30:00Z"), 3));
    }

    @Test
    void untilResetCountsDownToTheNextResetHour() {
        assertEquals(Duration.ofHours(24), DailyPeriod.untilReset(Instant.parse("2026-10-24T00:00:00Z"), 0));
        assertEquals(Duration.ofSeconds(1), DailyPeriod.untilReset(Instant.parse("2026-10-24T23:59:59Z"), 0));
        assertEquals(Duration.ofMinutes(30), DailyPeriod.untilReset(Instant.parse("2026-10-24T05:30:00Z"), 6));
        assertEquals(Duration.ofHours(23).plusMinutes(30), DailyPeriod.untilReset(Instant.parse("2026-10-24T06:30:00Z"), 6));
    }

    @Test
    void formatsRemainingTimeRoundedUpToTheMinute() {
        assertEquals("1m", DailyPeriod.formatRemaining(Duration.ofSeconds(1)));
        assertEquals("1m", DailyPeriod.formatRemaining(Duration.ofSeconds(60)));
        assertEquals("2m", DailyPeriod.formatRemaining(Duration.ofSeconds(61)));
        assertEquals("1h", DailyPeriod.formatRemaining(Duration.ofHours(1)));
        assertEquals("5h 12m", DailyPeriod.formatRemaining(Duration.ofHours(5).plusMinutes(12)));
        assertEquals("24h", DailyPeriod.formatRemaining(Duration.ofHours(24)));
    }
}
