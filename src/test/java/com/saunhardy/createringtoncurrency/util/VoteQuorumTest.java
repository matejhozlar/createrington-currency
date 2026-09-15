package com.saunhardy.createringtoncurrency.util;

import com.saunhardy.createringtoncurrency.util.VoteQuorum.Tally;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteQuorumTest {

    private static Tally uncapped(int eligible, int yes, int no, int percent) {
        return VoteQuorum.tally(eligible, yes, no, percent, Integer.MAX_VALUE);
    }

    @Test
    void halfNeedsHalfRoundedUp() {
        assertEquals(2, VoteQuorum.requiredYes(3, 50));
        assertEquals(2, VoteQuorum.requiredYes(4, 50));
        assertEquals(3, VoteQuorum.requiredYes(5, 50));
        assertEquals(3, VoteQuorum.requiredYes(6, 50));
        assertEquals(4, VoteQuorum.requiredYes(8, 50));
        assertEquals(5, VoteQuorum.requiredYes(10, 50));
    }

    @Test
    void aStrictMajorityIsOnePercentAway() {
        assertEquals(3, VoteQuorum.requiredYes(4, 51));
        assertEquals(4, VoteQuorum.requiredYes(6, 51));
    }

    @Test
    void aLoneVoterCarriesTheirOwnVote() {
        assertEquals(1, VoteQuorum.requiredYes(1, 50));
        assertEquals(1, VoteQuorum.requiredYes(1, 100));
        assertTrue(uncapped(1, 1, 0, 50).passed());
    }

    @Test
    void theInitiatorCannotCarryAVoteAloneOnceSomeoneElseIsEligible() {
        assertEquals(2, VoteQuorum.requiredYes(2, 50));
        assertEquals(2, VoteQuorum.requiredYes(2, 0));
        assertEquals(2, VoteQuorum.requiredYes(10, 0));
    }

    @Test
    void twoPlayersBothGetASay() {
        Tally opened = uncapped(2, 1, 0, 50);
        assertFalse(opened.decided());

        Tally agreed = uncapped(2, 2, 0, 50);
        assertTrue(agreed.passed());

        Tally refused = uncapped(2, 1, 1, 50);
        assertTrue(refused.decided());
        assertFalse(refused.passed());
        assertTrue(VoteQuorum.rejected(refused));
    }

    @Test
    void neverAsksForMoreVotesThanThereArePlayers() {
        assertEquals(4, VoteQuorum.requiredYes(4, 100));
        assertEquals(10, VoteQuorum.requiredYes(10, 100));
    }

    @Test
    void alwaysAsksForAtLeastOneVote() {
        assertEquals(1, VoteQuorum.requiredYes(0, 50));
    }

    @Test
    void passesOnceTheThresholdIsReached() {
        Tally tally = uncapped(4, 3, 1, 50);
        assertTrue(tally.passed());
        assertTrue(tally.decided());
    }

    @Test
    void staysOpenWhileTheUndecidedCouldStillCarryIt() {
        Tally tally = uncapped(10, 2, 1, 50);
        assertFalse(tally.passed());
        assertFalse(tally.decided());
    }

    @Test
    void failsEarlyOnceTheUndecidedCannotCarryIt() {
        assertFalse(uncapped(10, 1, 5, 50).decided());
        Tally tally = uncapped(10, 1, 6, 50);
        assertFalse(tally.passed());
        assertTrue(tally.decided());
    }

    @Test
    void silenceLeavesTheVoteOpenUntilItExpires() {
        Tally tally = uncapped(4, 1, 0, 50);
        assertFalse(tally.passed());
        assertFalse(tally.decided());
    }

    @Test
    void aShrunkenElectorateCanDecideAnOpenVote() {
        assertFalse(uncapped(6, 2, 0, 50).passed());
        assertTrue(uncapped(4, 2, 0, 50).passed());
    }

    @Test
    void playersJoiningAtTheDefaultPercentDoNotRaiseTheThreshold() {
        int announced = VoteQuorum.requiredYes(5, 50);
        assertEquals(3, announced);

        Tally sixth = VoteQuorum.tally(6, 3, 0, 50, announced);
        assertEquals(3, sixth.needed());
        assertTrue(sixth.passed());

        Tally crowd = VoteQuorum.tally(8, 3, 0, 50, announced);
        assertEquals(3, crowd.needed());
        assertTrue(crowd.passed());
    }

    @Test
    void aGrownElectorateCannotRaiseTheAnnouncedThreshold() {
        Tally tally = VoteQuorum.tally(6, 3, 0, 60, 3);
        assertEquals(3, tally.needed());
        assertTrue(tally.passed());
        assertTrue(tally.decided());
    }

    @Test
    void theAnnouncedThresholdStillShrinksWithTheElectorate() {
        Tally tally = VoteQuorum.tally(3, 2, 0, 50, 3);
        assertEquals(2, tally.needed());
        assertTrue(tally.passed());
    }

    @Test
    void tellsTurnoutFailuresApartFromRejections() {
        assertFalse(VoteQuorum.rejected(uncapped(10, 1, 1, 50)));
        assertFalse(VoteQuorum.rejected(uncapped(10, 1, 5, 50)));
        assertTrue(VoteQuorum.rejected(uncapped(10, 1, 6, 50)));
        assertFalse(VoteQuorum.rejected(uncapped(4, 1, 2, 50)));
        assertTrue(VoteQuorum.rejected(uncapped(4, 0, 3, 50)));
    }

    @Test
    void anEmptyElectorateIsNotARejection() {
        assertFalse(VoteQuorum.rejected(uncapped(0, 0, 0, 50)));
    }
}
