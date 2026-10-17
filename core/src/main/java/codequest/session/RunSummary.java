package codequest.session;

import java.util.List;

/** What a finished run leaves behind for the screens after it (results, standings). */
public record RunSummary(int score, int maxScore, Rank rank, double accuracy, int hintsUsed, boolean failed,
        List<SectionResult> sections) {

    public int gradePercent() {
        return Grades.percent(score, maxScore);
    }

    public static RunSummary of(StageRun run, int towerCount) {
        return new RunSummary(run.score(), Grades.maxScore(towerCount), run.rank(), run.overallAccuracy(),
                run.hintsUsed(), run.isFailed(), List.copyOf(run.sectionResults()));
    }
}
