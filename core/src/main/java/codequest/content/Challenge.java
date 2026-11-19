package codequest.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * One coding-challenge instance. It is never free-code compilation: the student answers with a token, a typed
 * token, a true/false, a line number or an ordered list of tokens (see {@link AnswerKind}), and the answer is
 * compared with the stored one.
 *
 * <p>What the fields mean depends on the kind:
 * <ul>
 * <li>CHOICE: correctToken is the right choice, distractors are the wrong ones.</li>
 * <li>FILL_BLANK: correctToken is the text to type, accepted lists other spellings that also count.</li>
 * <li>TRUE_FALSE: correctToken is "True" or "False"; the prompt is the statement.</li>
 * <li>ARRANGE: correctToken is the tokens in order, separated by spaces; distractors are extra decoy tokens.</li>
 * <li>FIND_BUG: the prompt is the code, one line per row; correctToken is the number of the buggy line.</li>
 * </ul>
 * hint is optional pre-authored guidance shown before the player answers (content is always prepared ahead of
 * a session, never generated live; see the manuscript's Scope and Limitations).
 */
public record Challenge(
        String id,
        CTopic topic,
        Difficulty difficulty,
        String prompt,
        String correctToken,
        List<String> distractors,
        String hint,
        AnswerKind kind,
        List<String> accepted,
        String chunk,
        String context,
        int slot,
        String explanation,
        String errorText
) {
    public static final String TRUE = "True";
    public static final String FALSE = "False";

    public Challenge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(correctToken, "correctToken");
        Objects.requireNonNull(kind, "kind");
        distractors = List.copyOf(distractors);
        accepted = List.copyOf(accepted);
    }

    /** A challenge with an explanation but no terminal message. */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context,
            int slot, String explanation) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context, slot, explanation, null);
    }

    /** The same question with the compiler message the student sees in the terminal when a wrong answer breaks the code. */
    public Challenge withErrorText(String text) {
        return new Challenge(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context,
                slot, explanation, text);
    }

    /**
     * The code this question puts into the code block for a wrong answer: what the student's answer would have made of
     * the line. A multiple choice or typed answer fills the blank with what was given; an arranged answer is the tokens
     * as put together; a missed find-the-bug leaves the buggy code as it was. Null when it cannot be told (the right
     * chunk is used then).
     */
    public String wrongCode(String answer) {
        return switch (kind) {
            case CHOICE, FILL_BLANK -> {
                int at = prompt.indexOf(QuestionDraft.BLANK);
                yield at < 0 ? null : (prompt.substring(0, at) + (answer == null ? "" : answer.trim())
                        + prompt.substring(at + QuestionDraft.BLANK.length())).trim();
            }
            case ARRANGE -> answer == null ? "" : CodeChunks.joinTokens(AnswerMatcher.splitTokens(answer));
            case FIND_BUG -> prompt;
            case TRUE_FALSE -> null;
        };
    }

    /**
     * The compiler message for a wrong answer, or null when that answer still builds (the terminal then says the output may
     * differ). The text is either one message for every wrong answer, or several lines {@code answer :: message} with
     * {@code * :: message} for any other answer; an empty message means that answer compiles. In a message,
     * {@code {answer}} stands for what the student gave, and a message that uses it only applies to an answer that looks
     * like a name (a number or a sign compiles).
     */
    public String errorFor(String answer) {
        if (errorText == null || errorText.isBlank()) {
            return null;
        }
        String chosen = null;
        if (errorText.contains(" :: ")) {
            String fallback = null;
            for (String line : errorText.split("\n")) {
                int at = line.indexOf(" :: ");
                if (at < 0) {
                    continue;
                }
                String key = line.substring(0, at).trim();
                String message = line.substring(at + 4).trim();
                if (key.equals("*")) {
                    fallback = message;
                } else if (answer != null && key.equals(answer.trim())) {
                    chosen = message;
                    fallback = null;
                    break;
                }
            }
            if (chosen == null) {
                chosen = fallback;
            }
        } else {
            chosen = errorText.trim();
        }
        if (chosen == null || chosen.isBlank()) {
            return null;
        }
        if (chosen.contains("{answer}")) {
            String given = answer == null ? "" : answer.trim();
            if (given.isEmpty() || !(Character.isLetter(given.charAt(0)) || given.charAt(0) == '_')) {
                return null; // a number or a sign is valid there: it builds
            }
            chosen = chosen.replace("{answer}", given);
        }
        if (chosen.matches("(?s).*\\{[A-Za-z_]+\\}.*")) {
            return null; // a placeholder nobody filled in: not a message the student should see
        }
        return chosen;
    }

    /** A challenge with no explanation (the hint is shown instead). */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context,
            int slot) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context, slot, null, null);
    }

    /** The same question with its explanation: why the answer is right, shown after the student answers. */
    public Challenge withExplanation(String text) {
        return new Challenge(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context,
                slot, text, errorText);
    }

    /** What to tell the student after answering: the written explanation, else the hint, else nothing. */
    public String explanationText() {
        if (explanation != null && !explanation.isBlank()) {
            return explanation.trim();
        }
        return hint == null ? "" : hint.trim();
    }

    /** A challenge that is not placed in a slot of a program (slot 0). */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context, 0);
    }

    /** A challenge with no written program chunk (see {@link #codeChunk()}) and no context line. */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, null, null);
    }

    /** A challenge with a written program chunk and no context line. */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, null);
    }

    /** The same question with a context line: what the code is for, so that exactly one answer is right. */
    public Challenge withContext(String text) {
        return new Challenge(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, text, slot, explanation, errorText);
    }

    /**
     * The same question placed in a slot of a program. Several questions with the same slot are the same chunk of the
     * program asked in different ways, one for each difficulty.
     */
    public Challenge withSlot(int number) {
        return new Challenge(id, topic, difficulty, prompt, correctToken, distractors, hint, kind, accepted, chunk, context, number, explanation, errorText);
    }

    /**
     * This question as it is asked at a given difficulty. The code it adds never changes, only how it is asked:
     * <ul>
     * <li>easier than it was written: a multiple choice keeps just one wrong choice, an arrange loses its decoy tokens;</li>
     * <li>harder than it was written: a multiple choice with a context line has to be typed, with no choices to pick from.</li>
     * </ul>
     * A question written for exactly that difficulty is returned as it is.
     */
    public Challenge forDifficulty(Difficulty asked) {
        int delta = asked.ordinal() - difficulty.ordinal();
        if (delta < 0) {
            if (kind == AnswerKind.CHOICE && distractors.size() > 1) {
                return new Challenge(id, topic, difficulty, prompt, correctToken, distractors.subList(0, 1), hint, kind, accepted,
                        chunk, context, slot, explanation, errorText);
            }
            if (kind == AnswerKind.ARRANGE && !distractors.isEmpty()) {
                return new Challenge(id, topic, difficulty, prompt, correctToken, List.of(), hint, kind, accepted, chunk, context,
                        slot, explanation, errorText);
            }
        } else if (delta > 0 && kind == AnswerKind.CHOICE && context != null) {
            // typed from memory; the context line is what keeps the blank from having many possible answers
            return new Challenge(id, topic, difficulty, prompt, correctToken, List.of(), hint, AnswerKind.FILL_BLANK, accepted,
                    chunk, context, slot, explanation, errorText);
        }
        return this;
    }

    /** A multiple-choice challenge (what every question was before there were other kinds). */
    public Challenge(String id, CTopic topic, Difficulty difficulty, String prompt, String correctToken,
            List<String> distractors, String hint) {
        this(id, topic, difficulty, prompt, correctToken, distractors, hint, AnswerKind.CHOICE, List.of());
    }

    /**
     * The lines of the lesson's program this question adds to the code block: the written chunk if there is one,
     * otherwise worked out from the question (see {@link CodeChunks}); null if neither is possible.
     */
    public String codeChunk() {
        return CodeChunks.of(kind, prompt, correctToken, chunk);
    }

    /** Whether this answer is right. A null answer (time ran out) is never right. */
    public boolean isCorrect(String answer) {
        return AnswerMatcher.matches(this, answer);
    }

    /** The right answer as it is shown after a miss, e.g. "line 3" for a find-the-bug question. */
    public String answerLabel() {
        return kind == AnswerKind.FIND_BUG ? "line " + correctToken : correctToken;
    }

    /** The lines of the code shown in a find-the-bug question. */
    public List<String> codeLines() {
        return List.of(prompt.split("\n", -1));
    }

    /** The tokens of an arrange question in the right order. */
    public List<String> correctOrder() {
        return AnswerMatcher.splitTokens(correctToken);
    }

    /**
     * What the student picks from, in the order it is shown. CHOICE is the right token among the wrong ones,
     * shuffled; TRUE_FALSE is always True then False; ARRANGE is the tokens plus the decoys, shuffled (and never
     * already in the right order); FIND_BUG is the line numbers; FILL_BLANK has nothing to pick.
     */
    public List<String> bank(Random rng) {
        List<String> out = new ArrayList<>();
        switch (kind) {
            case CHOICE -> {
                out.add(correctToken);
                out.addAll(distractors);
                Collections.shuffle(out, rng);
            }
            case TRUE_FALSE -> {
                out.add(TRUE);
                out.add(FALSE);
            }
            case ARRANGE -> {
                List<String> right = correctOrder();
                out.addAll(right);
                out.addAll(distractors);
                if (out.size() > 1) {
                    for (int tries = 0; tries < 20; tries++) {
                        Collections.shuffle(out, rng);
                        if (!out.equals(right)) {
                            break;
                        }
                    }
                }
            }
            case FIND_BUG -> {
                for (int i = 1; i <= codeLines().size(); i++) {
                    out.add(String.valueOf(i));
                }
            }
            case FILL_BLANK -> {
            }
        }
        return out;
    }
}
