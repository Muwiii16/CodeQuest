package codequest.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves a lesson's questions in program order: the first call gives the question for the first chunk, the next
 * call the second chunk, and so on. The requested topic is ignored, because the program decides what comes next.
 *
 * <p>A chunk (a slot) can have a question for each difficulty: the same lines of the program asked in different ways.
 * The one nearest to the requested difficulty is served, and when none was written for exactly that difficulty the
 * question is re-asked at it ({@link Challenge#forDifficulty}), so adaptive difficulty changes how a chunk is asked
 * and never what it adds. A run asks exactly once per tower, in order, so a replayed run lands on the same questions.
 */
public class ProgramChallengeProvider implements ChallengeProvider {

    /** The most questions (towers) a program lesson can have: the maps have room for this many towers. */
    public static final int MAX_SLOTS = 10;

    private final List<List<Challenge>> slots = new ArrayList<>();
    private int next;

    /** Questions with the same slot number are variants of one chunk; a question with slot 0 is a chunk of its own. */
    public ProgramChallengeProvider(List<Challenge> questions) {
        Map<Integer, List<Challenge>> grouped = new LinkedHashMap<>();
        int alone = -1;
        for (Challenge c : questions) {
            int key = c.slot() > 0 ? c.slot() : alone--;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        for (List<Challenge> variants : grouped.values()) {
            if (slots.size() < MAX_SLOTS) {
                slots.add(variants);
            }
        }
    }

    /** One question per chunk (the first variant of each), in program order: what the stage and the coverage are built from. */
    public List<Challenge> slots() {
        List<Challenge> out = new ArrayList<>();
        for (List<Challenge> variants : slots) {
            out.add(variants.get(0));
        }
        return out;
    }

    @Override
    public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
        if (next >= slots.size()) {
            return Optional.empty();
        }
        return Optional.of(pick(slots.get(next++), difficulty));
    }

    /** The variant nearest to the asked difficulty (the easier one on a tie), re-asked at that difficulty. */
    static Challenge pick(List<Challenge> variants, Difficulty asked) {
        Challenge best = variants.get(0);
        for (Challenge c : variants) {
            int d = Math.abs(c.difficulty().ordinal() - asked.ordinal());
            int bestD = Math.abs(best.difficulty().ordinal() - asked.ordinal());
            if (d < bestD || (d == bestD && c.difficulty().ordinal() < best.difficulty().ordinal())) {
                best = c;
            }
        }
        return best.forDifficulty(asked);
    }

    @Override
    public boolean buildsProgram() {
        return true;
    }

    /** A stage with one tower per question, spread over up to four map sections, each tower on its question's topic. */
    public static Stage stageFor(String title, String background, List<Challenge> slots) {
        int n = Math.min(MAX_SLOTS, slots.size());
        int sections = Math.max(1, Math.min(4, n));
        List<Section> out = new ArrayList<>();
        int at = 0;
        for (int s = 0; s < sections; s++) {
            int count = n / sections + (s < n % sections ? 1 : 0);
            List<CTopic> topics = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                topics.add(slots.get(at++).topic());
            }
            out.add(new Section(s + 1, topics));
        }
        return new Stage(title, background, out);
    }
}
