package codequest.content;

import java.util.*;

/**
 * Fixed, hand-authored challenge pool, one per (topic, difficulty) for now —
 * placeholder sample content, not real CC102-aligned authoring. Real content
 * comes later from professor authoring tools or the AI-backed provider
 * (Phase 5), both implementing the same ChallengeProvider interface.
 */
public class StaticChallengeProvider implements ChallengeProvider {

    private final Map<CTopic, Map<Difficulty, List<Challenge>>> pool = new EnumMap<>(CTopic.class);
    private final Random random = new Random();

    public StaticChallengeProvider() {
        seed();
    }

    @Override
    public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
        List<Challenge> candidates = pool
                .getOrDefault(topic, Map.of())
                .getOrDefault(difficulty, List.of());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    private void add(Challenge challenge) {
        pool.computeIfAbsent(challenge.topic(), t -> new EnumMap<>(Difficulty.class))
                .computeIfAbsent(challenge.difficulty(), d -> new ArrayList<>())
                .add(challenge);
    }

    private void seed() {
        add(new Challenge("syntax-normal-1", CTopic.SYNTAX, Difficulty.NORMAL,
                "int main() {\n    printf(\"Hi\")___\n    return 0;\n}",
                ";", List.of(":", ",", "."),
                "Every C statement ends with a semicolon."));

        add(new Challenge("variables-normal-1", CTopic.VARIABLES, Difficulty.NORMAL,
                "___ score = 0;",
                "int", List.of("integer", "Int", "num"),
                "C's whole-number type keyword is lowercase int, not integer or num."));

        add(new Challenge("control-normal-1", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "if (x > 0) ___\n    printf(\"positive\");",
                "{", List.of("then", "do", "begin"),
                "C blocks open with a curly brace, not a keyword like then or do."));

        add(new Challenge("functions-normal-1", CTopic.FUNCTIONS, Difficulty.NORMAL,
                "___ add(int a, int b) {\n    return a + b;\n}",
                "int", List.of("void", "function", "def"),
                "The return type comes before the function name; this function returns an int."));

        add(new Challenge("arrays-normal-1", CTopic.ARRAYS, Difficulty.NORMAL,
                "int scores___ = {90, 85, 77};",
                "[3]", List.of("(3)", "<3>", "{3}"),
                "Array size in C uses square brackets after the variable name."));

        add(new Challenge("pointers-normal-1", CTopic.POINTERS, Difficulty.NORMAL,
                "int x = 5;\nint ___px = &x;",
                "*", List.of("&", "#", "@"),
                "A pointer variable is declared with an asterisk before its name."));
    }
}
