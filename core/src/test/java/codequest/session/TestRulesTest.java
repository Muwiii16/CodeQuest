package codequest.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRulesTest {

    private static final Instant START = Instant.parse("2026-10-18T10:00:00Z");

    @Test
    void aTestEndsWhenEveryoneWhoJoinedHasFinished() {
        assertTrue(TestRules.shouldEnd("active", 3, 3, START, START.plusSeconds(60)));
        assertFalse(TestRules.shouldEnd("active", 3, 2, START, START.plusSeconds(60)));
    }

    @Test
    void aTestEndsAtTheTimeLimit() {
        assertFalse(TestRules.shouldEnd("active", 3, 1, START, START.plus(TestRules.MAX_DURATION).minusSeconds(1)));
        assertTrue(TestRules.shouldEnd("active", 3, 1, START, START.plus(TestRules.MAX_DURATION)));
    }

    @Test
    void aLobbyOrAnEndedTestNeverEndsOnItsOwn() {
        assertFalse(TestRules.shouldEnd("scheduled", 3, 3, null, START.plusSeconds(99999)));
        assertFalse(TestRules.shouldEnd("completed", 3, 3, START, START.plusSeconds(99999)));
    }

    @Test
    void anEmptyTestWithNoStartTimeKeepsRunning() {
        assertFalse(TestRules.shouldEnd("active", 0, 0, null, START));
    }
}
