package codequest.content;

import java.util.*;

/**
 * Serves challenges from an in-memory pool by topic and difficulty. If the
 * requested difficulty has none for that topic, the nearest available
 * difficulty is used; a topic with no challenges at all returns empty.
 */
public class PooledChallengeProvider implements ChallengeProvider {

    private final Map<CTopic, Map<Difficulty, List<Challenge>>> pool = new EnumMap<>(CTopic.class);
    private final Random random;

    public PooledChallengeProvider() {
        this.random = new Random();
    }

    public PooledChallengeProvider(Collection<Challenge> challenges) {
        this(challenges, new Random());
    }

    /** A seeded pool serves the same challenges in the same order for everyone using the same seed (class tests). */
    public PooledChallengeProvider(Collection<Challenge> challenges, long seed) {
        this(challenges, new Random(seed));
    }

    private PooledChallengeProvider(Collection<Challenge> challenges, Random random) {
        this.random = random;
        challenges.forEach(this::add);
    }

    protected final void add(Challenge challenge) {
        pool.computeIfAbsent(challenge.topic(), t -> new EnumMap<>(Difficulty.class))
                .computeIfAbsent(challenge.difficulty(), d -> new ArrayList<>())
                .add(challenge);
    }

    public boolean isEmpty() {
        return pool.isEmpty();
    }

    @Override
    public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
        Map<Difficulty, List<Challenge>> byDifficulty = pool.getOrDefault(topic, Map.of());
        List<Difficulty> tryOrder = new ArrayList<>(List.of(Difficulty.values()));
        tryOrder.sort(Comparator.comparingInt(d -> Math.abs(d.ordinal() - difficulty.ordinal())));
        for (Difficulty d : tryOrder) {
            List<Challenge> candidates = byDifficulty.getOrDefault(d, List.of());
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.get(random.nextInt(candidates.size())));
            }
        }
        return Optional.empty();
    }
}
