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
}
