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
    private final KindRotation rotation;

    public PooledChallengeProvider() {
        this(new Random(), new KindRotation());
    }

    public PooledChallengeProvider(Collection<Challenge> challenges) {
        this(challenges, new Random(), new KindRotation());
    }

    /** A seeded pool serves the same challenges in the same order for everyone using the same seed (class tests). */
    public PooledChallengeProvider(Collection<Challenge> challenges, long seed) {
        this(challenges, new Random(seed), new KindRotation());
    }

    /**
     * Pools that feed one play-through share a rotation, so the mix of answer kinds is kept across all of them
     * (a lesson's own questions and the built-in ones that fill the gaps).
     */
    public PooledChallengeProvider(Collection<Challenge> challenges, long seed, KindRotation rotation) {
        this(challenges, new Random(seed), rotation);
    }

    protected PooledChallengeProvider(Random random, KindRotation rotation) {
        this.random = random;
        this.rotation = rotation;
    }

    private PooledChallengeProvider(Collection<Challenge> challenges, Random random, KindRotation rotation) {
        this(random, rotation);
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

    /** Every challenge in the pool, in topic and difficulty order. */
    public List<Challenge> all() {
        List<Challenge> out = new ArrayList<>();
        for (Map<Difficulty, List<Challenge>> byDifficulty : pool.values()) {
            for (List<Challenge> list : byDifficulty.values()) {
                out.addAll(list);
            }
        }
        return out;
    }

    @Override
    public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
        Map<Difficulty, List<Challenge>> byDifficulty = pool.getOrDefault(topic, Map.of());
        List<Difficulty> tryOrder = new ArrayList<>(List.of(Difficulty.values()));
        tryOrder.sort(Comparator.comparingInt(d -> Math.abs(d.ordinal() - difficulty.ordinal())));
        for (Difficulty d : tryOrder) {
            List<Challenge> candidates = byDifficulty.getOrDefault(d, List.of());
            if (!candidates.isEmpty()) {
                return Optional.of(rotation.pick(candidates, random));
            }
        }
        return Optional.empty();
    }
}
