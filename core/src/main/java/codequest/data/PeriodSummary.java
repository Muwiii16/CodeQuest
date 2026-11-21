package codequest.data;

import codequest.content.DefaultStages;
import codequest.data.CampaignData.Progress;
import codequest.session.Grades;

import java.util.List;
import java.util.Map;

/**
 * One student's results over the lessons of one grading period: how many lessons they completed, their average grade on
 * those, how many attempts they spent and when they last played. {@code averageGrade} is -1 when nothing is completed.
 */
public record PeriodSummary(int total, int done, int averageGrade, int attempts, String lastPlayed) {

    /** A student with a grade under this on average needs attention. */
    public static final int LOW_GRADE = 50;

    public static PeriodSummary of(List<Lesson> lessons, Map<Long, Progress> mine) {
        int done = 0;
        int sum = 0;
        int attempts = 0;
        String last = null;
        for (Lesson l : lessons) {
            Progress p = mine.get(l.stageId());
            if (p == null) {
                continue;
            }
            attempts += p.attempts();
            if (p.lastPlayed() != null && (last == null || p.lastPlayed().compareTo(last) > 0)) {
                last = p.lastPlayed();
            }
            if (p.completed()) {
                sum += Grades.percent(p.score(), p.max(l.number()));
                done++;
            }
        }
        return new PeriodSummary(lessons.size(), done, done == 0 ? -1 : Math.round(sum / (float) done), attempts, last);
    }

    /** The average of the students' average grades (those who completed something), or -1 when nobody did. */
    public static int classAverage(List<PeriodSummary> students) {
        return (int) Math.round(students.stream().filter(s -> s.averageGrade() >= 0).mapToInt(PeriodSummary::averageGrade)
                .average().orElse(-1));
    }

    /** A short note for the professor: who has not started, who is behind, who is doing badly. */
    public String note() {
        if (total == 0) {
            return "-";
        }
        if (done == 0) {
            return attempts == 0 ? "Not started" : "In progress";
        }
        if (averageGrade < LOW_GRADE) {
            return "Needs attention";
        }
        return done < total ? (total - done) + " left" : "Done";
    }
}
