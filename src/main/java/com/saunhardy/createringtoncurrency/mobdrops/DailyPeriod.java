package com.saunhardy.createringtoncurrency.mobdrops;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class DailyPeriod {
    private DailyPeriod() {}

    public static LocalDate key(Instant now, int resetHourUtc) {
        return start(now, resetHourUtc).toLocalDate();
    }

    public static Duration untilReset(Instant now, int resetHourUtc) {
        return Duration.between(now, start(now, resetHourUtc).plusDays(1));
    }

    public static String formatRemaining(Duration remaining) {
        long minutes = Math.max(1, (remaining.getSeconds() + 59) / 60);
        long hours = minutes / 60;
        minutes %= 60;
        if (hours == 0) return minutes + "m";
        if (minutes == 0) return hours + "h";
        return hours + "h " + minutes + "m";
    }

    private static ZonedDateTime start(Instant now, int resetHourUtc) {
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        ZonedDateTime start = utc.truncatedTo(ChronoUnit.DAYS).withHour(resetHourUtc);
        return start.isAfter(utc) ? start.minusDays(1) : start;
    }
}
