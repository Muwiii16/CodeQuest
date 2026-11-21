package codequest.data;

import codequest.data.CampaignData.Progress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignDataTest {

    private static Lesson lesson(long id, int number) {
        return new Lesson(id, 1, number, "Lesson " + number, "normal", true, null);
    }

    private static final List<Lesson> LESSONS = List.of(lesson(10, 1), lesson(11, 2), lesson(12, 3));

    @Test
    void theFirstLessonIsAlwaysUnlocked() {
        assertTrue(CampaignData.isUnlocked(LESSONS, Map.of(), 0));
    }

    @Test
    void aLessonUnlocksOnlyOnceThePreviousOneIsCompleted() {
        Map<Long, Progress> progress = Map.of(10L, new Progress("completed", 80, "S", 1, null));
        assertTrue(CampaignData.isUnlocked(LESSONS, progress, 1));
        assertFalse(CampaignData.isUnlocked(LESSONS, progress, 2));
    }

    @Test
    void anUnfinishedPreviousLessonKeepsTheNextLocked() {
        Map<Long, Progress> progress = Map.of(10L, new Progress("in_progress", 0, null, 1, null));
        assertFalse(CampaignData.isUnlocked(LESSONS, progress, 1));
    }

    @Test
    void inAManualClassOnlyTheLessonsTheProfessorUnlockedAreOpen() {
        List<Lesson> lessons = List.of(lesson(10, 1), lesson(11, 2).withUnlocked(true), lesson(12, 3));
        // even the first lesson stays locked until the professor opens it; the order does not matter
        assertFalse(CampaignData.isUnlocked(true, lessons, Map.of(), 0));
        assertTrue(CampaignData.isUnlocked(true, lessons, Map.of(), 1));
        assertFalse(CampaignData.isUnlocked(true, lessons, Map.of(), 2));
        // finishing a lesson does not open the next one in a manual class
        Map<Long, Progress> progress = Map.of(11L, new Progress("completed", 80, "S", 1, null));
        assertFalse(CampaignData.isUnlocked(true, lessons, progress, 2));
    }

    @Test
    void inASelfPacedClassTheUnlockFlagIsIgnored() {
        List<Lesson> lessons = List.of(lesson(10, 1), lesson(11, 2).withUnlocked(true), lesson(12, 3));
        assertTrue(CampaignData.isUnlocked(false, lessons, Map.of(), 0));
        assertFalse(CampaignData.isUnlocked(false, lessons, Map.of(), 1)); // lesson 1 not finished: flag does not matter
    }

    @Test
    void aClassIsSelfPacedUnlessMadeManual() {
        CampaignData.ClassInfo c = new CampaignData.ClassInfo(1, "CC102", "A", "C", 0, 0);
        assertFalse(c.manual());
        assertTrue(c.withPacing(CampaignData.ClassInfo.MANUAL).manual());
        assertFalse(c.withPacing(CampaignData.ClassInfo.MANUAL).withPacing(CampaignData.ClassInfo.SELF_PACED).manual());
    }

    @Test
    void lessonDifficultyFallsBackToNormalWhenUnknown() {
        assertTrue(new Lesson(1, 1, 1, "x", "weird", true, null).difficulty() == codequest.content.Difficulty.NORMAL);
    }

    @Test
    void aLessonIsInPrelimsUntilMoved() {
        Lesson l = lesson(10, 1);
        assertTrue(l.periodEnum() == Period.PRELIMS);
        assertTrue(l.withPeriod(Period.FINALS).periodEnum() == Period.FINALS);
        // other edits keep the period
        assertTrue(l.withPeriod(Period.SEMIS).withUnlocked(true).withDetails("t", "easy", true, "p").periodEnum() == Period.SEMIS);
    }

    @Test
    void anUnknownPeriodKeyFallsBackToPrelims() {
        assertTrue(Period.fromKey(null) == Period.PRELIMS);
        assertTrue(Period.fromKey("whatever") == Period.PRELIMS);
        assertTrue(Period.fromKey(" Midterms ") == Period.MIDTERMS);
    }

    @Test
    void filteringByPeriodKeepsTheOrder() {
        List<Lesson> all = List.of(lesson(1, 1), lesson(2, 2).withPeriod(Period.MIDTERMS), lesson(3, 3),
                lesson(4, 4).withPeriod(Period.MIDTERMS));
        assertTrue(Period.filter(all, null).size() == 4);
        List<Lesson> mid = Period.filter(all, Period.MIDTERMS);
        assertTrue(mid.size() == 2 && mid.get(0).number() == 2 && mid.get(1).number() == 4);
        assertTrue(Period.filter(all, Period.FINALS).isEmpty());
    }

    @Test
    void aPeriodSummaryCountsCompletedLessonsAndAveragesTheirGrades() {
        List<Lesson> period = List.of(lesson(10, 1), lesson(11, 2), lesson(12, 3));
        Map<Long, Progress> mine = Map.of(
                10L, new Progress("completed", 0, "F", 2, "2026-11-01T10:00:00Z"),
                11L, new Progress("in_progress", 0, null, 1, "2026-11-05T10:00:00Z"));
        PeriodSummary s = PeriodSummary.of(period, mine);
        assertTrue(s.total() == 3 && s.done() == 1);
        assertTrue(s.attempts() == 3);
        assertTrue(s.lastPlayed().startsWith("2026-11-05"));
        assertTrue(s.averageGrade() == 0);
        assertTrue("Needs attention".equals(s.note()));
    }

    @Test
    void aPeriodSummaryNotesWhoHasNotStartedAndWhoIsDone() {
        List<Lesson> period = List.of(lesson(10, 1));
        assertTrue("Not started".equals(PeriodSummary.of(period, Map.of()).note()));
        assertTrue(PeriodSummary.of(period, Map.of()).averageGrade() == -1);
        assertTrue("In progress".equals(PeriodSummary.of(period, Map.of(10L, new Progress("in_progress", 0, null, 1, null))).note()));
        // a lesson the student never touched, in an empty period
        assertTrue("-".equals(PeriodSummary.of(List.of(), Map.of()).note()));
    }

    @Test
    void theClassAverageSkipsStudentsWhoCompletedNothing() {
        List<PeriodSummary> students = List.of(new PeriodSummary(3, 3, 80, 4, null), new PeriodSummary(3, 1, 60, 2, null),
                new PeriodSummary(3, 0, -1, 0, null));
        assertTrue(PeriodSummary.classAverage(students) == 70);
        assertTrue(PeriodSummary.classAverage(List.of(new PeriodSummary(3, 0, -1, 0, null))) == -1);
        assertTrue(PeriodSummary.classAverage(List.of()) == -1);
    }

    @Test
    void theClassNumbersAddUpAcrossStudentsPeriodsAndLessons() {
        List<Lesson> lessons = List.of(lesson(10, 1), lesson(11, 2).withPeriod(Period.MIDTERMS));
        ProfessorData.Student a = new ProfessorData.Student(1, "a", null);
        ProfessorData.Student b = new ProfessorData.Student(2, "b", null);
        ProfessorData.Student c = new ProfessorData.Student(3, "c", null);
        // a completed both (grade 100 on each: 100 points at most 100), b completed only lesson 1 with 0 points, c played nothing
        Map<Long, Map<Long, Progress>> progress = Map.of(
                1L, Map.of(10L, new Progress("completed", 10000, "S", 1, null), 11L, new Progress("completed", 10000, "S", 1, null)),
                2L, Map.of(10L, new Progress("completed", 0, "F", 3, null)));
        ClassStats st = ClassStats.of(lessons, List.of(a, b, c), progress);
        assertTrue(st.students() == 3 && st.lessons() == 2);
        assertTrue(st.completionPercent() == 50); // 3 of 6 lesson results
        assertTrue(st.noActivity() == 1);
        // b (0%) and c (nothing completed) need attention, b first
        assertTrue(st.attention().size() == 2 && st.attention().get(0).student().id() == 2);
        assertTrue(st.periods().size() == 2 && st.periods().get(0).completionPercent() == 67);
        assertTrue(st.leastCompleted(1).get(0).lesson().number() == 2);
        assertTrue(st.lowestGrade(1).get(0).lesson().number() == 1); // lesson 1 averages (100 + 0) / 2
    }

    @Test
    void questionStatsGroupTheAnswerLogByQuestionWeakestFirst() {
        com.badlogic.gdx.utils.JsonValue rows = new com.badlogic.gdx.utils.JsonReader().parse(
                "[{\"challenge_id\":1,\"is_correct\":true,\"challenge\":{\"sequence_index\":1,\"prompt\":\"What does  printf do?\"}},"
                + "{\"challenge_id\":1,\"is_correct\":true,\"challenge\":{\"sequence_index\":1,\"prompt\":\"What does  printf do?\"}},"
                + "{\"challenge_id\":2,\"is_correct\":false,\"challenge\":{\"sequence_index\":2,\"prompt\":\"Fix the loop\"}},"
                + "{\"challenge_id\":2,\"is_correct\":true,\"challenge\":{\"sequence_index\":2,\"prompt\":\"Fix the loop\"}},"
                + "{\"challenge_id\":null,\"challenge_key\":\"builtin-7\",\"is_correct\":false},"
                + "{\"is_correct\":true}]");
        List<ProfessorData.QuestionStat> stats = ProfessorData.questionStats(rows);
        assertTrue(stats.size() == 3); // the row with no question at all is skipped
        assertTrue(stats.get(0).label().equals("builtin-7") && stats.get(0).rate() == 0);
        assertTrue(stats.get(1).number() == 2 && stats.get(1).right() == 1 && stats.get(1).total() == 2);
        assertTrue(stats.get(2).label().equals("What does printf do?") && stats.get(2).rate() == 1.0);
        assertTrue(ProfessorData.questionStats(null).isEmpty());
    }

    @Test
    void aClassTestAnalysisAddsUpScoresAccuracyAndSpeed() {
        List<ClassTestData.Participant> ranked = List.of(
                new ClassTestData.Participant(1, "a", true, 2000, true),
                new ClassTestData.Participant(2, "b", true, 600, false),
                new ClassTestData.Participant(3, "c", true, 0, true));
        com.badlogic.gdx.utils.JsonValue rows = new com.badlogic.gdx.utils.JsonReader().parse(
                "[{\"student_id\":1,\"topic\":\"POINTERS\",\"is_correct\":true,\"time_taken_ms\":4000,\"challenge_id\":9},"
                + "{\"student_id\":1,\"topic\":\"LOOPS\",\"is_correct\":true,\"time_taken_ms\":2000,\"challenge_id\":10},"
                + "{\"student_id\":2,\"topic\":\"POINTERS\",\"is_correct\":false,\"time_taken_ms\":6000,\"challenge_id\":9},"
                + "{\"student_id\":2,\"topic\":\"LOOPS\",\"is_correct\":true,\"time_taken_ms\":8000,\"challenge_id\":10}]");
        TestAnalysis t = TestAnalysis.of(ranked, 5, rows);
        assertTrue(t.joined() == 3 && t.finished() == 2 && t.leftEarly() == 1 && t.absent() == 2);
        assertTrue(t.average() == 867 && t.median() == 600 && t.highest() == 2000 && t.lowest() == 0);
        assertTrue(t.totalAnswers() == 4 && t.accuracyPercent() == 75 && t.averageSeconds() == 5);
        assertTrue(t.students().get(0).rank() == 1 && t.students().get(0).correct() == 2 && t.students().get(0).averageSeconds() == 3);
        assertTrue(t.students().get(1).accuracyPercent() == 50);
        assertTrue(t.students().get(2).answered() == 0 && t.students().get(2).averageSeconds() == 0); // joined, answered nothing
        assertTrue(t.topics().get("POINTERS")[0] == 1 && t.topics().get("POINTERS")[1] == 2);
        assertTrue(t.questions().size() == 2 && t.questions().get(0).rate() == 0.5); // question 9 was missed by one of two
    }

    @Test
    void aClassTestNobodyTookHasNoNumbers() {
        TestAnalysis t = TestAnalysis.of(List.of(), 4, null);
        assertTrue(t.joined() == 0 && t.average() == 0 && t.students().isEmpty() && t.absent() == 4);
    }
}
