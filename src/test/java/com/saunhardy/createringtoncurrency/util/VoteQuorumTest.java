package com.saunhardy.createringtoncurrency.util;

import com.saunhardy.createringtoncurrency.util.VoteQuorum.Tally;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteQuorumTest {

    @Test
    void halfNeedsHalfRoundedUp() {
        assertEquals(1, VoteQuorum.requiredYes(2, 50));
        assertEquals(2, VoteQuorum.requiredYes(3, 50));
        assertEquals(2, VoteQuorum.requiredYes(4, 50));
        assertEquals(3, VoteQuorum.requiredYes(5, 50));
        assertEquals(3, VoteQuorum.requiredYes(6, 50));
        assertEquals(4, VoteQuorum.requiredYes(8, 50));
        assertEquals(5, VoteQuorum.requiredYes(10, 50));
    }

    @Test
    void aStrictMajorityIsOnePercentAway() {
        assertEquals(2, VoteQuorum.requiredYes(2, 51));
        assertEquals(3, VoteQuorum.requiredYes(4, 51));
        assertEquals(4, VoteQuorum.requiredYes(6, 51));
    }

    @Test
    void aLoneVoterCarriesTheirOwnVote() {
        assertEquals(1, VoteQuorum.requiredYes(1, 50));
        assertEquals(1, VoteQuorum.requiredYes(1, 100));
    }

    @Test
    void neverAsksForMoreVotesThanThereArePlayers() {
        assertEquals(4, VoteQuorum.requiredYes(4, 100));
        assertEquals(10, VoteQuorum.requiredYes(10, 100));
    }

    @Test
    void alwaysAsksForAtLeastOneVote() {
        assertEquals(1, VoteQuorum.requiredYes(10, 0));
        assertEquals(1, VoteQuorum.requiredYes(0, 50));
    }

    @Test
    void passesOnceTheThresholdIsReached() {
        Tally tally = VoteQuorum.tally(4, 3, 1, 50);
        assertTrue(tally.passed());
        assertTrue(tally.decided());
    }

    @Test
    void staysOpenWhileTheUndecidedCouldStillCarryIt() {
        Tally tally = VoteQuorum.tally(10, 2, 1, 50);
        assertFalse(tally.passed());
        assertFalse(tally.decided());
    }

    @Test
    void failsEarlyOnceTheUndecidedCannotCarryIt() {
        assertFalse(VoteQuorum.tally(10, 1, 5, 50).decided());
        Tally tally = VoteQuorum.tally(10, 1, 6, 50);
        assertFalse(tally.passed());
        assertTrue(tally.decided());
    }

    @Test
    void silenceLeavesTheVoteOpenUntilItExpires() {
        Tally tally = VoteQuorum.tally(4, 1, 0, 50);
        assertFalse(tally.passed());
        assertFalse(tally.decided());
    }

    @Test
    void aShrunkenElectorateCanDecideAnOpenVote() {
        assertFalse(VoteQuorum.tally(6, 2, 0, 50).passed());
        assertTrue(VoteQuorum.tally(4, 2, 0, 50).passed());
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
        Tally tally = VoteQuorum.tally(2, 1, 0, 50, 3);
        assertEquals(1, tally.needed());
        assertTrue(tally.passed());
    }

    @Test
    void tellsTurnoutFailuresApartFromRejections() {
        assertFalse(VoteQuorum.rejected(VoteQuorum.tally(10, 1, 1, 50)));
        assertFalse(VoteQuorum.rejected(VoteQuorum.tally(10, 1, 5, 50)));
        assertTrue(VoteQuorum.rejected(VoteQuorum.tally(10, 1, 6, 50)));
        assertFalse(VoteQuorum.rejected(VoteQuorum.tally(4, 0, 2, 50)));
        assertTrue(VoteQuorum.rejected(VoteQuorum.tally(4, 0, 3, 50)));
    }

    @Test
    void anEmptyElectorateIsNotARejection() {
        assertFalse(VoteQuorum.rejected(VoteQuorum.tally(0, 0, 0, 50)));
    }
}
