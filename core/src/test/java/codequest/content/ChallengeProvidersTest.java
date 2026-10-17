package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeProvidersTest {

    private static Challenge c(String id, CTopic topic, Difficulty d) {
        return new Challenge(id, topic, d, "prompt", "ok", List.of("a", "b", "c"), null);
    }

    @Test
    void pooledPrefersTheRequestedDifficulty() {
        PooledChallengeProvider p = new PooledChallengeProvider(List.of(
                c("easy", CTopic.ARRAYS, Difficulty.EASY), c("hard", CTopic.ARRAYS, Difficulty.HARD)));
        assertEquals("hard", p.getChallenge(CTopic.ARRAYS, Difficulty.HARD).orElseThrow().id());
        assertEquals("easy", p.getChallenge(CTopic.ARRAYS, Difficulty.EASY).orElseThrow().id());
    }

    @Test
    void pooledFallsBackToTheNearestDifficulty() {
        PooledChallengeProvider p = new PooledChallengeProvider(List.of(c("hard", CTopic.ARRAYS, Difficulty.HARD)));
        assertEquals("hard", p.getChallenge(CTopic.ARRAYS, Difficulty.NORMAL).orElseThrow().id());
        assertEquals("hard", p.getChallenge(CTopic.ARRAYS, Difficulty.EASY).orElseThrow().id());
    }

    @Test
    void pooledIsEmptyForATopicItDoesNotHave() {
        PooledChallengeProvider p = new PooledChallengeProvider(List.of(c("a", CTopic.ARRAYS, Difficulty.EASY)));
        assertTrue(p.getChallenge(CTopic.POINTERS, Difficulty.EASY).isEmpty());
        assertTrue(new PooledChallengeProvider(List.of()).isEmpty());
    }

    @Test
    void fallbackUsesPrimaryWhenItHasTheTopic() {
        ChallengeProvider primary = new PooledChallengeProvider(List.of(c("db", CTopic.ARRAYS, Difficulty.EASY)));
        ChallengeProvider fallback = new PooledChallengeProvider(List.of(c("static", CTopic.ARRAYS, Difficulty.EASY)));
        Optional<Challenge> got = new FallbackChallengeProvider(primary, fallback)
                .getChallenge(CTopic.ARRAYS, Difficulty.EASY);
        assertEquals("db", got.orElseThrow().id());
    }

    @Test
    void fallbackCoversWhatThePrimaryLacks() {
        ChallengeProvider primary = new PooledChallengeProvider(List.of(c("db", CTopic.ARRAYS, Difficulty.EASY)));
        ChallengeProvider fallback = new StaticChallengeProvider();
        assertTrue(new FallbackChallengeProvider(primary, fallback)
                .getChallenge(CTopic.POINTERS, Difficulty.NORMAL).isPresent());
    }

    @Test
    void staticProviderCoversEveryTopicAtEveryDifficulty() {
        StaticChallengeProvider p = new StaticChallengeProvider();
        for (CTopic t : CTopic.values()) {
            for (Difficulty d : Difficulty.values()) {
                Challenge got = p.getChallenge(t, d).orElseThrow();
                assertEquals(t, got.topic());
                assertEquals(d, got.difficulty());
                assertFalse(got.distractors().contains(got.correctToken()),
                        got.id() + " lists its correct token as a wrong answer");
            }
        }
    }

    @Test
    void sameSeedServesTheSameChallengesInTheSameOrder() {
        List<Challenge> pool = List.of(c("a", CTopic.ARRAYS, Difficulty.EASY), c("b", CTopic.ARRAYS, Difficulty.EASY),
                c("c", CTopic.ARRAYS, Difficulty.EASY), c("d", CTopic.ARRAYS, Difficulty.EASY));
        PooledChallengeProvider first = new PooledChallengeProvider(pool, 42L);
        PooledChallengeProvider second = new PooledChallengeProvider(pool, 42L);
        for (int i = 0; i < 10; i++) {
            assertEquals(first.getChallenge(CTopic.ARRAYS, Difficulty.EASY).orElseThrow().id(),
                    second.getChallenge(CTopic.ARRAYS, Difficulty.EASY).orElseThrow().id());
        }
    }
}
