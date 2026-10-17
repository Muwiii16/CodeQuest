package codequest.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradesTest {

    @Test
    void percentIsPointsOverPossiblePoints() {
        assertEquals(75, Grades.percent(60, 80));
        assertEquals(100, Grades.percent(80, 80));
        assertEquals(0, Grades.percent(0, 80));
    }

    @Test
    void percentHandlesNothingPossibleAndOutOfRangeScores() {
        assertEquals(0, Grades.percent(10, 0));
        assertEquals(100, Grades.percent(200, 80));
        assertEquals(0, Grades.percent(-5, 80));
    }

    @Test
    void maxScoreIsEveryTowerAnsweredCorrectly() {
        assertEquals(80, Grades.maxScore(8));
    }
}
