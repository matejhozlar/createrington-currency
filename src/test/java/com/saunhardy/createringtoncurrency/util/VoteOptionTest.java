package com.saunhardy.createringtoncurrency.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteOptionTest {

    @Test
    void parsesTheFourFields() {
        VoteOption option = VoteOption.parse("sunset | time | a sunset | time set 12000");

        assertNotNull(option);
        assertEquals("sunset", option.id());
        assertEquals("time", option.group());
        assertEquals("a sunset", option.label());
        assertEquals("time set 12000", option.command());
    }

    @Test
    void toleratesMissingWhitespaceCaseAndALeadingSlash() {
        VoteOption option = VoteOption.parse("Sunset|TIME|Sunset Sky|/time set 12000");

        assertNotNull(option);
        assertEquals("sunset", option.id());
        assertEquals("time", option.group());
        assertEquals("Sunset Sky", option.label());
        assertEquals("time set 12000", option.command());
    }

    @Test
    void keepsPipesInsideTheCommand() {
        VoteOption option = VoteOption.parse("party | fun | a party | tellraw @a \"a | b\"");

        assertNotNull(option);
        assertEquals("tellraw @a \"a | b\"", option.command());
    }

    @Test
    void rejectsMalformedEntries() {
        List<String> garbage = List.of(
                "",
                "sunset",
                "sunset | time | sunset",
                " | time | sunset | time set 12000",
                "sunset |  | sunset | time set 12000",
                "sunset | time |  | time set 12000",
                "sunset | time | sunset | ",
                "sunset | time | sunset | /",
                "sun set | time | sunset | time set 12000",
                "sunset | day time | sunset | time set 12000",
                "sunset! | time | sunset | time set 12000"
        );
        for (String raw : garbage) {
            assertNull(VoteOption.parse(raw), "'" + raw + "' should be rejected");
        }
    }

    @Test
    void rejectsTheIdsTheCastSubcommandsUse() {
        assertNull(VoteOption.parse("yes | time | daytime | time set day"));
        assertNull(VoteOption.parse("No | time | daytime | time set day"));
    }

    @Test
    void aCommandWithoutThePlaceholderTakesNoDays() {
        VoteOption option = VoteOption.parse("day | time | daytime | time set day");

        assertNotNull(option);
        assertFalse(option.takesDays());
        assertEquals("time set day", option.commandFor(0));
        assertEquals("time set day", option.commandFor(3));
    }

    @Test
    void substitutesTheChosenDays() {
        VoteOption option = VoteOption.parse("rain | weather | rain | weather rain {days:0.25}d");

        assertNotNull(option);
        assertTrue(option.takesDays());
        assertEquals("weather rain 3d", option.commandFor(3));
    }

    @Test
    void fallsBackToTheDefaultWhenNoDaysAreGiven() {
        VoteOption withDefault = VoteOption.parse("rain | weather | rain | weather rain {days:0.25}d");
        VoteOption bare = VoteOption.parse("rain | weather | rain | weather rain {days}d");

        assertNotNull(withDefault);
        assertNotNull(bare);
        assertEquals("weather rain 0.25d", withDefault.commandFor(0));
        assertEquals("weather rain 0.25d", withDefault.commandFor(-1));
        assertTrue(bare.takesDays());
        assertEquals("weather rain 1d", bare.commandFor(0));
    }

    @Test
    void replacesEveryPlaceholderAndLeavesOtherBracesAlone() {
        VoteOption option = VoteOption.parse(
                "storm | weather | a storm | execute if predicate {condition:\"x\"} run weather thunder {days:2}d {days}");

        assertNotNull(option);
        assertEquals("execute if predicate {condition:\"x\"} run weather thunder 5d 5", option.commandFor(5));
        assertEquals("execute if predicate {condition:\"x\"} run weather thunder 2d 1", option.commandFor(0));
    }

    @Test
    void aMalformedDefaultIsNotAPlaceholder() {
        VoteOption option = VoteOption.parse("rain | weather | rain | weather rain {days:abc}d");

        assertNotNull(option);
        assertFalse(option.takesDays());
        assertEquals("weather rain {days:abc}d", option.commandFor(3));
    }
}
