package codequest.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A question as a professor types it in the editor, with the rules a question must meet
 * before it can be saved. Pure logic, so it is unit tested without the game running.
 */
public record QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
        List<String> distractors, String hint) {

    public static final int MAX_PROMPT = 600;
    public static final int MAX_TOKEN = 40;
    public static final int MAX_HINT = 200;
    /** The correct answer plus up to this many wrong ones fit on the answer row. */
    public static final int MAX_DISTRACTORS = 3;

    /** Trims, drops blanks and removes repeated wrong answers (keeping their order). */
    public static List<String> cleanDistractors(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        for (String d : raw) {
            if (d != null && !d.trim().isEmpty()) {
                seen.add(d.trim());
            }
        }
        return new ArrayList<>(seen);
    }

    /** A normalised copy: texts trimmed, blank hint turned into null, wrong answers cleaned. */
    public QuestionDraft cleaned() {
        String h = hint == null || hint.trim().isEmpty() ? null : hint.trim();
        return new QuestionDraft(topic, difficulty, prompt == null ? "" : prompt.trim(),
                correct == null ? "" : correct.trim(), cleanDistractors(distractors), h);
    }

    /** What is wrong with the question, in words for the professor; empty when it can be saved. */
    public Optional<String> problem() {
        QuestionDraft q = cleaned();
        if (q.prompt().isEmpty()) {
            return Optional.of("Write the question first.");
        }
        if (q.prompt().length() > MAX_PROMPT) {
            return Optional.of("The question is too long (max " + MAX_PROMPT + " characters).");
        }
        if (q.correct().isEmpty()) {
            return Optional.of("Type the correct answer.");
        }
        if (q.correct().length() > MAX_TOKEN) {
            return Optional.of("The correct answer is too long (max " + MAX_TOKEN + " characters).");
        }
        if (q.distractors().isEmpty()) {
            return Optional.of("Add at least one wrong answer.");
        }
        if (q.distractors().size() > MAX_DISTRACTORS) {
            return Optional.of("Use at most " + MAX_DISTRACTORS + " wrong answers.");
        }
        for (String d : q.distractors()) {
            if (d.equals(q.correct())) {
                return Optional.of("A wrong answer is the same as the correct answer.");
            }
            if (d.length() > MAX_TOKEN) {
                return Optional.of("A wrong answer is too long (max " + MAX_TOKEN + " characters).");
            }
        }
        if (q.hint() != null && q.hint().length() > MAX_HINT) {
            return Optional.of("The hint is too long (max " + MAX_HINT + " characters).");
        }
        return Optional.empty();
    }
}
