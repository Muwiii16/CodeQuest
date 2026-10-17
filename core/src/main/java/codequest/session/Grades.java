package codequest.session;

/** Turns raw points into the percentage grade shown to professors and in class-test results. */
public final class Grades {

    private Grades() {
    }

    /** Points / possible points as 0-100, rounded; 0 if nothing was possible. */
    public static int percent(int score, int maxScore) {
        if (maxScore <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(score * 100f / maxScore)));
    }

    /** Most points a full run can earn: every tower answered correctly. */
    public static int maxScore(int towerCount) {
        return towerCount * StageRun.POINTS_PER_CORRECT;
    }
}
