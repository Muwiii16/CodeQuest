package codequest.content;

import java.util.List;
import java.util.Objects;

/**
 * One coding-challenge instance: a token-selection question, not free-code
 * compilation. distractors are wrong-answer tokens shown alongside
 * correctToken; hint is optional pre-authored/AI-generated guidance shown
 * before the player answers (AI content is always prepared ahead of a
 * session, never generated live — see the manuscript's Scope and Limitations).
 */
public record Challenge(
        String id,
        CTopic topic,
        Difficulty difficulty,
        String prompt,
        String correctToken,
        List<String> distractors,
        String hint
) {
    public Challenge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(correctToken, "correctToken");
        distractors = List.copyOf(distractors);
    }
}
