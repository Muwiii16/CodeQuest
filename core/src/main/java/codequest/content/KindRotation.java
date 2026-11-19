package codequest.content;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Keeps a lesson's questions varied. One rotation is shared by every question source of a play-through, and it
 * remembers what has been served: it picks the answer kind that has come up the least, never the same kind twice in a
 * row when another is available, and prefers a question the student has not had yet in this run.
 *
 * <p>It is deterministic: the same seed and the same sequence of requests give the same questions, so every student
 * in a class test gets the same ones and a resumed lesson lands exactly where it left off.
 */
public final class KindRotation {

    private final Map<AnswerKind, Integer> served = new EnumMap<>(AnswerKind.class);
    private final Set<String> seen = new HashSet<>();
    private AnswerKind last;

    /** Picks one of the candidates (which must not be empty) and remembers it. */
    public Challenge pick(List<Challenge> candidates, Random rng) {
        List<Challenge> pool = unseen(candidates);

        List<AnswerKind> kinds = new ArrayList<>();
        for (Challenge c : pool) {
            if (!kinds.contains(c.kind())) {
                kinds.add(c.kind());
            }
        }
        if (kinds.size() > 1) {
            kinds.remove(last); // not the same kind twice in a row
        }
        int fewest = Integer.MAX_VALUE;
        for (AnswerKind k : kinds) {
            fewest = Math.min(fewest, served.getOrDefault(k, 0));
        }
        List<AnswerKind> leastServed = new ArrayList<>();
        for (AnswerKind k : kinds) {
            if (served.getOrDefault(k, 0) == fewest) {
                leastServed.add(k);
            }
        }
        AnswerKind kind = leastServed.get(rng.nextInt(leastServed.size()));

        List<Challenge> ofKind = new ArrayList<>();
        for (Challenge c : pool) {
            if (c.kind() == kind) {
                ofKind.add(c);
            }
        }
        Challenge chosen = ofKind.get(rng.nextInt(ofKind.size()));
        served.merge(kind, 1, Integer::sum);
        seen.add(chosen.id());
        last = kind;
        return chosen;
    }

    /** The candidates the student has not had yet; all of them once every one has been served. */
    private List<Challenge> unseen(List<Challenge> candidates) {
        List<Challenge> fresh = new ArrayList<>();
        for (Challenge c : candidates) {
            if (!seen.contains(c.id())) {
                fresh.add(c);
            }
        }
        return fresh.isEmpty() ? candidates : fresh;
    }
}
