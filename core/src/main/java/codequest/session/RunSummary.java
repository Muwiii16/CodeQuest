package codequest.session;

import java.util.List;

/** What a finished class-test run leaves behind for the screens after it. */
public record RunSummary(int score, Rank rank, double accuracy, int correct, int total, int bestStreak,
        List<SectionResult> sections) {

    public static RunSummary of(StageRun run) {
        int right = 0;
        int answered = 0;
        for (SectionResult r : run.sectionResults()) {
            right += r.correct();
            answered += r.total();
        }
        return new RunSummary(run.score(), run.rank(), run.overallAccuracy(), right, answered, run.bestStreak(),
                List.copyOf(run.sectionResults()));
    }
}
