package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KindRotationTest {

    private static Challenge c(String id, AnswerKind kind) {
        String correct = kind == AnswerKind.TRUE_FALSE ? "True" : kind == AnswerKind.ARRANGE ? "a b" : "ok";
        return new Challenge(id, CTopic.SYNTAX, Difficulty.EASY, "p", correct, List.of("x"), null, kind, List.of());
    }

    private static List<Challenge> pool() {
        List<Challenge> out = new ArrayList<>();
        for (AnswerKind k : List.of(AnswerKind.CHOICE, AnswerKind.FILL_BLANK, AnswerKind.TRUE_FALSE)) {
            for (int i = 0; i < 3; i++) {
                out.add(c(k + "-" + i, k));
            }
        }
        return out;
    }

    private static List<Challenge> serve(PooledChallengeProvider p, int n) {
        List<Challenge> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).orElseThrow());
        }
        return out;
    }

    @Test
    void neverTheSameKindTwiceInARowWhenThereIsAChoice() {
        for (long seed = 0; seed < 25; seed++) {
            List<Challenge> got = serve(new PooledChallengeProvider(pool(), seed), 9);
            for (int i = 1; i < got.size(); i++) {
                assertNotEquals(got.get(i - 1).kind(), got.get(i).kind(), "seed " + seed + " repeated a kind at " + i);
            }
        }
    }

    @Test
    void everyKindComesUpEvenly() {
        for (long seed = 0; seed < 25; seed++) {
            Map<AnswerKind, Integer> counts = new EnumMap<>(AnswerKind.class);
            serve(new PooledChallengeProvider(pool(), seed), 9).forEach(x -> counts.merge(x.kind(), 1, Integer::sum));
            for (int n : counts.values()) {
                assertEquals(3, n, "seed " + seed + " " + counts);
            }
        }
    }

    @Test
    void aQuestionIsNotRepeatedUntilTheWholePoolHasBeenUsed() {
        for (long seed = 0; seed < 25; seed++) {
            Set<String> ids = new HashSet<>();
            serve(new PooledChallengeProvider(pool(), seed), 9).forEach(x -> ids.add(x.id()));
            assertEquals(9, ids.size(), "seed " + seed);
        }
    }

    @Test
    void whenThePoolRunsOutItStartsOverInsteadOfFailing() {
        assertEquals(20, serve(new PooledChallengeProvider(pool(), 1L), 20).size());
    }

    @Test
    void aPoolWithOneKindStillWorks() {
        PooledChallengeProvider p = new PooledChallengeProvider(
                List.of(c("a", AnswerKind.CHOICE), c("b", AnswerKind.CHOICE)), 3L);
        assertEquals(4, serve(p, 4).size());
    }

    @Test
    void theSameSeedGivesTheSameQuestionsInTheSameOrder() {
        List<String> first = serve(new PooledChallengeProvider(pool(), 42L), 12).stream().map(Challenge::id).toList();
        List<String> second = serve(new PooledChallengeProvider(pool(), 42L), 12).stream().map(Challenge::id).toList();
        assertEquals(first, second);
    }

    @Test
    void twoSourcesSharingARotationKeepOneMix() {
        for (long seed = 0; seed < 15; seed++) {
            KindRotation rotation = new KindRotation();
            PooledChallengeProvider own = new PooledChallengeProvider(List.of(c("own", AnswerKind.CHOICE)), seed, rotation);
            StaticChallengeProvider builtIn = new StaticChallengeProvider(seed + 1, rotation);
            FallbackChallengeProvider provider = new FallbackChallengeProvider(own, builtIn);
            // the lesson only has its own question for SYNTAX; the other topics come from the built-in set
            List<AnswerKind> kinds = new ArrayList<>();
            for (CTopic t : CTopic.values()) {
                kinds.add(provider.getChallenge(t, Difficulty.NORMAL).orElseThrow().kind());
            }
            long distinct = kinds.stream().distinct().count();
            assertTrue(distinct >= 3, "seed " + seed + " gave a poor mix: " + kinds);
        }
    }

    @Test
    void theBuiltInSetGivesAMixAtEveryDifficulty() {
        for (Difficulty d : Difficulty.values()) {
            for (long seed = 0; seed < 10; seed++) {
                KindRotation rotation = new KindRotation();
                StaticChallengeProvider p = new StaticChallengeProvider(seed, rotation);
                Set<AnswerKind> kinds = new HashSet<>();
                for (CTopic t : CTopic.values()) {
                    kinds.add(p.getChallenge(t, d).orElseThrow().kind());
                }
                assertTrue(kinds.size() >= 3, d + " seed " + seed + ": " + kinds);
            }
        }
    }

    @Test
    void theBuiltInSetIsSeededSoClassTestsMatch() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        StaticChallengeProvider a = new StaticChallengeProvider(7L, new KindRotation());
        StaticChallengeProvider b = new StaticChallengeProvider(7L, new KindRotation());
        for (CTopic t : CTopic.values()) {
            first.add(a.getChallenge(t, Difficulty.NORMAL).orElseThrow().id());
            second.add(b.getChallenge(t, Difficulty.NORMAL).orElseThrow().id());
        }
        assertEquals(first, second);
    }
}
