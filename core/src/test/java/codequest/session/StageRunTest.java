package codequest.session;

import codequest.content.AnswerKind;
import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.ChallengeProvider;
import codequest.content.Difficulty;
import codequest.content.Section;
import codequest.content.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StageRunTest {

    /** Every challenge has correct token "ok"; records which difficulty it was asked for. */
    private static class FakeProvider implements ChallengeProvider {
        final java.util.ArrayList<Difficulty> asked = new java.util.ArrayList<>();

        @Override
        public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
            asked.add(difficulty);
            return Optional.of(new Challenge("t-" + asked.size(), topic, difficulty,
                    "prompt", "ok", List.of("a", "b", "c"), null));
        }
    }

    private static Stage stage(int sections, int towersEach) {
        List<Section> list = new java.util.ArrayList<>();
        for (int i = 1; i <= sections; i++) {
            list.add(new Section(i, java.util.Collections.nCopies(towersEach, CTopic.SYNTAX)));
        }
        return new Stage("test", "map1", list);
    }

    private static void answerAll(StageRun run, String token) {
        while (run.nextChallenge().isPresent()) {
            run.answer(token);
            if (run.isFailed()) {
                return;
            }
        }
    }

    @Test
    void adaptiveOffKeepsDifficultyFixedEvenInCampaign() {
        FakeProvider provider = new FakeProvider();
        StageRun run = new StageRun(stage(4, 2), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, provider, false);
        answerAll(run, "ok");
        assertTrue(provider.asked.stream().allMatch(d -> d == Difficulty.NORMAL));
    }

    @Test
    void perfectRunFinishesWithRankS() {
        StageRun run = new StageRun(stage(4, 2), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        answerAll(run, "ok");
        assertTrue(run.isFinished());
        assertFalse(run.isFailed());
        assertEquals(Rank.S, run.rank());
        assertEquals(8 * StageRun.POINTS_PER_CORRECT, run.score());
        assertEquals(StageRun.MAX_HP, run.hp());
        assertEquals(4, run.sectionResults().size());
    }

    @Test
    void campaignRaisesDifficultyAfterAStrongSection() {
        FakeProvider provider = new FakeProvider();
        StageRun run = new StageRun(stage(2, 2), Difficulty.EASY, StageRun.Mode.CAMPAIGN, provider);
        answerAll(run, "ok");
        assertEquals(List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.NORMAL, Difficulty.NORMAL), provider.asked);
        assertEquals(Difficulty.NORMAL, run.sectionResults().get(0).nextDifficulty());
    }

    @Test
    void classTestKeepsDifficultyFixed() {
        FakeProvider provider = new FakeProvider();
        StageRun run = new StageRun(stage(2, 2), Difficulty.EASY, StageRun.Mode.CLASS_TEST, provider);
        answerAll(run, "ok");
        assertEquals(List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.EASY, Difficulty.EASY), provider.asked);
    }

    @Test
    void weakSectionLowersDifficulty() {
        FakeProvider provider = new FakeProvider();
        StageRun run = new StageRun(stage(2, 2), Difficulty.HARD, StageRun.Mode.CAMPAIGN, provider);
        answerAll(run, "wrong");
        assertEquals(Difficulty.NORMAL, run.sectionResults().get(0).nextDifficulty());
    }

    @Test
    void wrongAnswerAppendsRedTokenAndCostsHealth() {
        StageRun run = new StageRun(stage(1, 3), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        run.nextChallenge();
        StageRun.Outcome o = run.answer("a");
        assertFalse(o.correct());
        assertEquals("ok", o.correctToken());
        assertEquals(StageRun.DAMAGE_PER_MISS, o.damage());
        assertEquals(StageRun.MAX_HP - StageRun.DAMAGE_PER_MISS, run.hp());
        assertEquals(1, run.codeBlock().tokens().size());
        assertFalse(run.codeBlock().tokens().get(0).correct());
    }

    @Test
    void timeoutCountsAsMissWithNoTokenAppended() {
        StageRun run = new StageRun(stage(1, 3), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        run.nextChallenge();
        StageRun.Outcome o = run.answer(null);
        assertFalse(o.correct());
        assertEquals(StageRun.DAMAGE_PER_MISS, o.damage());
        assertTrue(run.codeBlock().tokens().isEmpty());
    }

    @Test
    void runningOutOfHealthFailsTheRunWithRankF() {
        StageRun run = new StageRun(stage(4, 3), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        answerAll(run, "wrong");
        assertTrue(run.isFailed());
        assertFalse(run.isFinished());
        assertEquals(0, run.hp());
        assertEquals(Rank.F, run.rank());
        assertTrue(run.nextChallenge().isEmpty());
    }

    @Test
    void nextChallengeIsStableUntilAnswered() {
        StageRun run = new StageRun(stage(1, 2), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        Challenge first = run.nextChallenge().orElseThrow();
        assertSame(first, run.nextChallenge().orElseThrow());
        run.answer("ok");
        assertNotSame(first, run.nextChallenge().orElseThrow());
    }

    @Test
    void answeringWithNoActiveChallengeIsAnError() {
        StageRun run = new StageRun(stage(1, 1), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, new FakeProvider());
        assertThrows(IllegalStateException.class, () -> run.answer("ok"));
    }

    @Test
    void towersWithoutAChallengeAreSkipped() {
        ChallengeProvider empty = (topic, difficulty) -> Optional.empty();
        StageRun run = new StageRun(stage(2, 2), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, empty);
        assertTrue(run.nextChallenge().isEmpty());
        assertTrue(run.isFinished());
    }

    @Test
    void classTestPointsDependOnSpeedAndStreak() {
        StageRun run = new StageRun(stage(1, 4), Difficulty.NORMAL, StageRun.Mode.CLASS_TEST, new FakeProvider());
        run.nextChallenge();
        StageRun.Outcome first = run.answer("ok", 1f);
        assertEquals(1000, first.points());
        assertEquals(1, first.streak());
        run.nextChallenge();
        StageRun.Outcome second = run.answer("ok", 0f);
        assertEquals(StageRun.SPEED_POINTS_MIN + StageRun.STREAK_BONUS_STEP, second.points());
        assertEquals(2, second.streak());
        assertEquals(first.points() + second.points(), run.score());
    }

    @Test
    void classTestMissResetsStreakGivesNoPointsAndCostsNoHealth() {
        StageRun run = new StageRun(stage(1, 3), Difficulty.NORMAL, StageRun.Mode.CLASS_TEST, new FakeProvider());
        run.nextChallenge();
        run.answer("ok", 1f);
        run.nextChallenge();
        StageRun.Outcome miss = run.answer("wrong", 1f);
        assertFalse(miss.correct());
        assertEquals(0, miss.points());
        assertEquals(0, miss.damage());
        assertEquals(0, miss.streak());
        assertEquals(StageRun.MAX_HP, run.hp());
        assertEquals(1, run.bestStreak());
    }

    @Test
    void moreTimeForHarderToAnswerKindsAndForLongerCode() {
        Challenge oneLine = new Challenge("a", CTopic.SYNTAX, Difficulty.NORMAL, "x ___", "y", List.of("z"), null);
        Challenge fourLines = new Challenge("b", CTopic.SYNTAX, Difficulty.NORMAL, "a\nb\nc\nd ___", "y", List.of("z"), null);
        Challenge typed = new Challenge("c", CTopic.SYNTAX, Difficulty.NORMAL, "x ___", "y", List.of(), null,
                AnswerKind.FILL_BLANK, List.of());
        float base = StageRun.flightSeconds(Difficulty.NORMAL);
        assertEquals(base, StageRun.flightSeconds(oneLine, Difficulty.NORMAL), 0.001f);
        assertEquals(base + 3 * StageRun.SECONDS_PER_EXTRA_LINE, StageRun.flightSeconds(fourLines, Difficulty.NORMAL), 0.001f);
        assertTrue(StageRun.flightSeconds(typed, Difficulty.NORMAL) > base);
        assertTrue(StageRun.flightSeconds(Difficulty.EASY) > StageRun.flightSeconds(Difficulty.NORMAL));
        assertTrue(StageRun.flightSeconds(Difficulty.NORMAL) > StageRun.flightSeconds(Difficulty.HARD));
        assertTrue(StageRun.flightSeconds(Difficulty.HARD) >= 8f); // nobody gets less than 8 seconds to read a line
    }

    @Test
    void aTypedAnswerCountsAndTheCodeBlockKeepsTheRightSpelling() {
        Challenge typed = new Challenge("f", CTopic.SYNTAX, Difficulty.EASY, "x___", "i++", List.of(), null,
                AnswerKind.FILL_BLANK, List.of());
        StageRun run = new StageRun(stage(1, 2), Difficulty.EASY, StageRun.Mode.CAMPAIGN,
                (topic, difficulty) -> Optional.of(typed));
        run.nextChallenge();
        assertTrue(run.answer("i ++").correct());
        assertEquals("i++", run.codeBlock().tokens().get(0).text());
    }

    @Test
    void findTheBugAndTrueFalseDoNotAddToTheCodeBlock() {
        Challenge bug = new Challenge("b", CTopic.SYNTAX, Difficulty.EASY, "a\nb", "2", List.of(), null,
                AnswerKind.FIND_BUG, List.of());
        StageRun run = new StageRun(stage(1, 2), Difficulty.EASY, StageRun.Mode.CAMPAIGN,
                (topic, difficulty) -> Optional.of(bug));
        run.nextChallenge();
        StageRun.Outcome miss = run.answer("1");
        assertFalse(miss.correct());
        assertEquals("line 2", miss.correctToken());
        assertTrue(run.codeBlock().tokens().isEmpty());
        run.nextChallenge();
        assertTrue(run.answer("2").correct());
        assertTrue(run.codeBlock().tokens().isEmpty());
    }

    @Test
    void classTestNeverEndsEarlyEvenIfEverythingIsWrong() {
        StageRun run = new StageRun(stage(4, 3), Difficulty.NORMAL, StageRun.Mode.CLASS_TEST, new FakeProvider());
        answerAll(run, "wrong");
        assertFalse(run.isFailed());
        assertTrue(run.isFinished());
        assertEquals(0, run.score());
    }

    @Test
    void streakBonusGrowsThenCaps() {
        assertEquals(0, StageRun.streakBonus(1));
        assertEquals(100, StageRun.streakBonus(2));
        assertEquals(400, StageRun.streakBonus(5));
        assertEquals(500, StageRun.streakBonus(6));
        assertEquals(500, StageRun.streakBonus(20));
    }

    @Test
    void speedPointsAreClampedToTheirRange() {
        assertEquals(StageRun.SPEED_POINTS_MAX, StageRun.speedPoints(2f));
        assertEquals(StageRun.SPEED_POINTS_MIN, StageRun.speedPoints(-1f));
        assertEquals(750, StageRun.speedPoints(0.5f));
    }
}
