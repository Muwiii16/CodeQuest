package codequest.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankCalculatorTest {

    @Test
    void cutoffsMapToExpectedRanks() {
        assertEquals(Rank.S, RankCalculator.rankFor(0.95));
        assertEquals(Rank.A, RankCalculator.rankFor(0.85));
        assertEquals(Rank.B, RankCalculator.rankFor(0.65));
        assertEquals(Rank.C, RankCalculator.rankFor(0.50));
        assertEquals(Rank.D, RankCalculator.rankFor(0.35));
        assertEquals(Rank.F, RankCalculator.rankFor(0.34));
    }

    @Test
    void onlyBOrBetterPasses() {
        assertTrue(Rank.S.passes());
        assertTrue(Rank.B.passes());
        assertFalse(Rank.C.passes());
        assertFalse(Rank.F.passes());
    }
}
