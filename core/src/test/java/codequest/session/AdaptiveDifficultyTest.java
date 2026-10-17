package codequest.session;

import codequest.content.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveDifficultyTest {

    @Test
    void eightyPercentOrMoreRaisesDifficulty() {
        assertEquals(Difficulty.HARD, AdaptiveDifficulty.next(Difficulty.NORMAL, 0.80));
        assertEquals(Difficulty.NORMAL, AdaptiveDifficulty.next(Difficulty.EASY, 1.0));
    }

    @Test
    void fiftyToSeventyNineLeavesDifficultyUnchanged() {
        assertEquals(Difficulty.NORMAL, AdaptiveDifficulty.next(Difficulty.NORMAL, 0.79));
        assertEquals(Difficulty.NORMAL, AdaptiveDifficulty.next(Difficulty.NORMAL, 0.50));
    }

    @Test
    void belowFiftyLowersDifficulty() {
        assertEquals(Difficulty.EASY, AdaptiveDifficulty.next(Difficulty.NORMAL, 0.49));
        assertEquals(Difficulty.NORMAL, AdaptiveDifficulty.next(Difficulty.HARD, 0.0));
    }

    @Test
    void difficultyNeverLeavesTheEasyToHardRange() {
        assertEquals(Difficulty.HARD, AdaptiveDifficulty.next(Difficulty.HARD, 1.0));
        assertEquals(Difficulty.EASY, AdaptiveDifficulty.next(Difficulty.EASY, 0.0));
    }
}
