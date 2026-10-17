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
    void lessonDifficultyFallsBackToNormalWhenUnknown() {
        assertTrue(new Lesson(1, 1, 1, "x", "weird", true, null).difficulty() == codequest.content.Difficulty.NORMAL);
    }
}
