package codequest.data;

import codequest.content.DefaultStages;
import codequest.data.CampaignData.Progress;
import codequest.session.Grades;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The numbers of a whole class, from every student's progress on every lesson: how much of the work is completed, the average
 * grade, how each grading period went, which lessons were completed least or graded lowest, and which students need
 * attention. Pure arithmetic (no screen, no network), so it can be tested.
 */
public record ClassStats(int students, int lessons, int completionPercent, int averageGrade, int noActivity,
        List<StudentRow> rows, List<StudentRow> attention, List<PeriodStat> periods, List<LessonStat> lessonStats) {

    /** One student with their summary over all the lessons. */
    public record StudentRow(ProfessorData.Student student, PeriodSummary summary) {
    }

    /** One grading period of the class. completionPercent and averageGrade are -1 when there is nothing to measure. */
    public record PeriodStat(Period period, int lessons, int completionPercent, int averageGrade) {
    }

    /** One lesson of the class: how many of its students completed it and their average grade on it (-1 if nobody did). */
    public record LessonStat(Lesson lesson, int completed, int students, int averageGrade) {
        public int completionPercent() {
            return students == 0 ? 0 : Math.round(completed * 100f / students);
        }
    }

    public static ClassStats of(List<Lesson> lessons, List<ProfessorData.Student> roster,
            Map<Long, Map<Long, Progress>> progress) {
        List<StudentRow> rows = new ArrayList<>();
        for (ProfessorData.Student s : roster) {
            rows.add(new StudentRow(s, PeriodSummary.of(lessons, progress.getOrDefault(s.id(), Map.of()))));
        }
        int possible = lessons.size() * roster.size();
        int done = rows.stream().mapToInt(r -> r.summary().done()).sum();
        int completion = possible == 0 ? 0 : Math.round(done * 100f / possible);
        int average = PeriodSummary.classAverage(rows.stream().map(StudentRow::summary).toList());
        int idle = (int) rows.stream().filter(r -> r.summary().attempts() == 0).count();

        // who needs attention: nothing completed yet, or an average grade under the line (lowest first, then nothing completed)
        List<StudentRow> attention = new ArrayList<>(rows.stream()
                .filter(r -> lessons.size() > 0 && (r.summary().done() == 0
                        || r.summary().averageGrade() < PeriodSummary.LOW_GRADE)).toList());
        attention.sort(Comparator.comparingInt((StudentRow r) -> r.summary().averageGrade() < 0 ? 1 : 0)
                .thenComparingInt(r -> r.summary().averageGrade()));

        List<PeriodStat> periods = new ArrayList<>();
        for (Period p : Period.values()) {
            List<Lesson> inPeriod = Period.filter(lessons, p);
            if (inPeriod.isEmpty()) {
                continue;
            }
            List<PeriodSummary> sums = new ArrayList<>();
            for (ProfessorData.Student s : roster) {
                sums.add(PeriodSummary.of(inPeriod, progress.getOrDefault(s.id(), Map.of())));
            }
            int periodPossible = inPeriod.size() * roster.size();
            int periodDone = sums.stream().mapToInt(PeriodSummary::done).sum();
            periods.add(new PeriodStat(p, inPeriod.size(), periodPossible == 0 ? -1 : Math.round(periodDone * 100f / periodPossible),
                    PeriodSummary.classAverage(sums)));
        }

        List<LessonStat> perLesson = new ArrayList<>();
        for (Lesson l : lessons) {
            int completed = 0;
            int sum = 0;
            int max = Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(l.number())).towerCount());
            for (ProfessorData.Student s : roster) {
                Progress p = progress.getOrDefault(s.id(), Map.of()).get(l.stageId());
                if (p != null && p.completed()) {
                    completed++;
                    sum += Grades.percent(p.score(), max);
                }
            }
            perLesson.add(new LessonStat(l, completed, roster.size(), completed == 0 ? -1 : Math.round(sum / (float) completed)));
        }
        return new ClassStats(roster.size(), lessons.size(), completion, average, idle, rows, attention, periods, perLesson);
    }

    /** The lessons the fewest students completed (ties: the earlier lesson first), at most n. */
    public List<LessonStat> leastCompleted(int n) {
        return lessonStats.stream().sorted(Comparator.comparingInt(LessonStat::completionPercent)
                .thenComparingInt(l -> l.lesson().number())).limit(n).toList();
    }

    /** The lessons with the lowest average grade among those someone completed, at most n. */
    public List<LessonStat> lowestGrade(int n) {
        return lessonStats.stream().filter(l -> l.averageGrade() >= 0)
                .sorted(Comparator.comparingInt(LessonStat::averageGrade).thenComparingInt(l -> l.lesson().number())).limit(n)
                .toList();
    }
}
