package codequest.session;

import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.Difficulty;
import codequest.content.PooledChallengeProvider;
import codequest.content.Section;
import codequest.content.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunReplayTest {

    private static List<Challenge> pool() {
        List<Challenge> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(new Challenge("c" + i, CTopic.SYNTAX, Difficulty.NORMAL, "prompt " + i, "ok" + i,
                    List.of("a", "b", "c"), null));
        }
        return out;
    }

    private static Stage stage() {
        return new Stage("t", "map1", List.of(new Section(1, List.of(CTopic.SYNTAX, CTopic.SYNTAX, CTopic.SYNTAX)),
                new Section(2, List.of(CTopic.SYNTAX, CTopic.SYNTAX))));
    }

    private static StageRun fresh(long seed) {
        return new StageRun(stage(), Difficulty.NORMAL, StageRun.Mode.CLASS_TEST, new PooledChallengeProvider(pool(), seed));
    }

    @Test
    void replayingTheAnswersReachesTheSameState() {
        StageRun original = fresh(7L);
        List<RunReplay.Answer> given = new ArrayList<>();
        float[] fractions = { 0.9f, 0.4f, 0.7f };
        for (int i = 0; i < 3; i++) {
            Challenge c = original.nextChallenge().orElseThrow();
            String token = i == 1 ? "wrong" : c.correctToken();
            original.answer(token, fractions[i]);
            given.add(new RunReplay.Answer(token, fractions[i]));
        }
        String nextOriginal = original.nextChallenge().orElseThrow().id();

        StageRun resumed = fresh(7L);
        assertTrue(RunReplay.replay(resumed, given));

        assertEquals(original.score(), resumed.score());
        assertEquals(original.streak(), resumed.streak());
        assertEquals(original.bestStreak(), resumed.bestStreak());
        assertEquals(original.sectionResults().size(), resumed.sectionResults().size());
        assertEquals(nextOriginal, resumed.nextChallenge().orElseThrow().id());
    }

    @Test
    void aSaveLongerThanTheRunIsReportedAsStale() {
        List<RunReplay.Answer> tooMany = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            tooMany.add(new RunReplay.Answer("x", 0.5f));
        }
        assertFalse(RunReplay.replay(fresh(1L), tooMany));
    }

    @Test
    void aSeededCampaignRunResumesWithTheSameDifficultyHealthAndNextQuestion() {
        List<Challenge> mixed = new ArrayList<>();
        for (Difficulty d : Difficulty.values()) {
            for (int i = 0; i < 3; i++) {
                mixed.add(new Challenge(d + "-" + i, CTopic.SYNTAX, d, "prompt", "ok" + d + i, List.of("a", "b"), null));
            }
        }
        StageRun original = new StageRun(stage(), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN,
                new PooledChallengeProvider(mixed, 99L));
        List<RunReplay.Answer> given = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Challenge c = original.nextChallenge().orElseThrow();
            String token = i < 3 ? c.correctToken() : "wrong";
            original.answer(token, 0f);
            given.add(new RunReplay.Answer(token, 0f));
        }
        String nextOriginal = original.nextChallenge().orElseThrow().id();

        StageRun resumed = new StageRun(stage(), Difficulty.NORMAL, StageRun.Mode.CAMPAIGN,
                new PooledChallengeProvider(mixed, 99L));
        assertTrue(RunReplay.replay(resumed, given));
        assertEquals(original.difficulty(), resumed.difficulty());
        assertEquals(original.hp(), resumed.hp());
        assertEquals(original.score(), resumed.score());
        assertEquals(nextOriginal, resumed.nextChallenge().orElseThrow().id());
    }
}
