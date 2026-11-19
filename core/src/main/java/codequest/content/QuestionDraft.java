package codequest.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A question as a professor types it in the editor, with the rules a question must meet
 * before it can be saved. Pure logic, so it is unit tested without the game running.
 *
 * <p>What {@code correct}, {@code distractors} and {@code accepted} mean depends on the {@link AnswerKind}
 * (see {@link Challenge}): wrong choices for CHOICE, decoy tokens for ARRANGE, other accepted spellings
 * (in {@code accepted}) for FILL_BLANK.
 */
public record QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
        List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context,
        String explanation, String errorText) {

    public static final int MAX_PROMPT = 600;
    /** The compiler message shown in the terminal when a wrong answer breaks the code. */
    public static final int MAX_ERROR = 200;
    /** The explanation shown after the student answers: why the answer is right. */
    public static final int MAX_EXPLANATION = 300;
    /** The context line: what the code is for, in one sentence shown above the question. */
    public static final int MAX_CONTEXT = 120;
    public static final int MAX_TOKEN = 40;
    public static final int MAX_HINT = 200;
    /** The correct answer plus up to this many wrong ones fit on the answer row. */
    public static final int MAX_DISTRACTORS = 3;
    /** Other spellings a typed answer may have. */
    public static final int MAX_ACCEPTED = 3;
    /** An arranged line has between 2 and this many tokens (the chips still fit on one row). */
    public static final int MAX_ARRANGE_TOKENS = 12;
    /** A find-the-bug snippet has between 2 and this many lines. */
    public static final int MAX_BUG_LINES = 8;
    /** The most characters of program code one question may add. */
    public static final int MAX_CHUNK = 400;
    /** The text that marks the blank in a fill-in-the-blank question. */
    public static final String BLANK = "___";

    /** A question with no written program chunk and no context line. */
    public QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted) {
        this(topic, difficulty, prompt, correct, distractors, hint, kind, accepted, null, null);
    }

    /** A question with an explanation but no terminal message. */
    public QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context,
            String explanation) {
        this(topic, difficulty, prompt, correct, distractors, hint, kind, accepted, chunk, context, explanation, null);
    }

    /** A question with a chunk and a context line but no explanation. */
    public QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk, String context) {
        this(topic, difficulty, prompt, correct, distractors, hint, kind, accepted, chunk, context, null, null);
    }

    /** A question with a written program chunk and no context line. */
    public QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
            List<String> distractors, String hint, AnswerKind kind, List<String> accepted, String chunk) {
        this(topic, difficulty, prompt, correct, distractors, hint, kind, accepted, chunk, null, null);
    }

    /** A multiple-choice question (the original kind). */
    public QuestionDraft(CTopic topic, Difficulty difficulty, String prompt, String correct,
            List<String> distractors, String hint) {
        this(topic, difficulty, prompt, correct, distractors, hint, AnswerKind.CHOICE, List.of());
    }

    public QuestionDraft {
        kind = kind == null ? AnswerKind.CHOICE : kind;
        distractors = distractors == null ? List.of() : distractors;
        accepted = accepted == null ? List.of() : accepted;
    }

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

    /** The same text with the same rules as the wrong answers; used for the other accepted spellings. */
    public static List<String> cleanAccepted(List<String> raw) {
        return cleanDistractors(raw);
    }

    /**
     * A normalised copy: texts trimmed, blank hint turned into null, and only the lists the kind uses are kept
     * (a true/false question has no wrong answers, an arranged line has its tokens separated by single spaces).
     */
    public QuestionDraft cleaned() {
        String h = hint == null || hint.trim().isEmpty() ? null : hint.trim();
        String p = prompt == null ? "" : prompt.trim();
        String c = correct == null ? "" : correct.trim();
        List<String> wrong = cleanDistractors(distractors);
        List<String> other = List.of();
        switch (kind) {
            case TRUE_FALSE -> {
                wrong = List.of();
                if (c.equalsIgnoreCase(Challenge.TRUE)) {
                    c = Challenge.TRUE;
                } else if (c.equalsIgnoreCase(Challenge.FALSE)) {
                    c = Challenge.FALSE;
                }
            }
            case FIND_BUG -> wrong = List.of();
            case FILL_BLANK -> {
                wrong = List.of();
                other = new ArrayList<>(cleanAccepted(accepted));
                other.remove(c);
            }
            case ARRANGE -> {
                List<String> tokens = AnswerMatcher.splitTokens(c);
                c = String.join(" ", tokens);
                // decoys are optional extras: one that has a space in it, or is a real token, is simply dropped
                List<String> kept = new ArrayList<>();
                for (String d : wrong) {
                    if (!d.contains(" ") && !tokens.contains(d) && kept.size() < MAX_DISTRACTORS) {
                        kept.add(d);
                    }
                }
                wrong = kept;
            }
            case CHOICE -> {
            }
        }
        // only true/false and find-the-bug questions have a written chunk; the others work it out from the question
        String ch = chunk == null || chunk.isBlank() ? null : chunk.trim();
        if (kind != AnswerKind.TRUE_FALSE && kind != AnswerKind.FIND_BUG) {
            ch = null;
        }
        String cx = context == null || context.isBlank() ? null : context.trim();
        String ex = explanation == null || explanation.isBlank() ? null : explanation.trim();
        String er = errorText == null || errorText.isBlank() ? null : errorText.trim();
        return new QuestionDraft(topic, difficulty, p, c, wrong, h, kind, other, ch, cx, ex, er);
    }

    /** The lines of the lesson's program this question adds; null when they cannot be worked out and were not written. */
    public String codeChunk() {
        QuestionDraft q = cleaned();
        return CodeChunks.of(q.kind(), q.prompt(), q.correct(), q.chunk());
    }

    /** What is wrong with the question, in words for the professor; empty when it can be saved. */
    public Optional<String> problem() {
        return problem(false);
    }

    /** Same, for a question of a lesson that builds a program: it must also say which lines it adds. */
    public Optional<String> problem(boolean inProgramLesson) {
        Optional<String> own = ownProblem();
        if (own.isPresent() || !inProgramLesson) {
            return own;
        }
        QuestionDraft q = cleaned();
        if (q.explanation() == null) {
            return Optional.of("Explain why the answer is right (shown to the student after they answer).");
        }
        // a blank in code has many possible answers unless the question says what the code is for
        if ((q.kind() == AnswerKind.CHOICE || q.kind() == AnswerKind.FILL_BLANK) && q.context() == null) {
            return Optional.of("Say what this code is for (the context line), so the blank has only one right answer.");
        }
        String chunk = q.codeChunk();
        if (chunk == null) {
            return Optional.of(q.kind() == AnswerKind.TRUE_FALSE || q.kind() == AnswerKind.FIND_BUG
                    ? "Write the lines of the program this question adds."
                    : "Put " + BLANK + " where the answer goes, so the lines of the program can be worked out.");
        }
        if (chunk.length() > MAX_CHUNK) {
            return Optional.of("The code this question adds is too long (max " + MAX_CHUNK + " characters).");
        }
        return Optional.empty();
    }

    private Optional<String> ownProblem() {
        QuestionDraft q = cleaned();
        if (q.prompt().isEmpty()) {
            return Optional.of(q.kind() == AnswerKind.TRUE_FALSE ? "Write the statement first." : "Write the question first.");
        }
        if (q.prompt().length() > MAX_PROMPT) {
            return Optional.of("The question is too long (max " + MAX_PROMPT + " characters).");
        }
        if (q.errorText() != null && q.errorText().length() > MAX_ERROR) {
            return Optional.of("The terminal message is too long (max " + MAX_ERROR + " characters).");
        }
        if (q.explanation() != null && q.explanation().length() > MAX_EXPLANATION) {
            return Optional.of("The explanation is too long (max " + MAX_EXPLANATION + " characters).");
        }
        if (q.context() != null && q.context().length() > MAX_CONTEXT) {
            return Optional.of("The context line is too long (max " + MAX_CONTEXT + " characters).");
        }
        Optional<String> specific = switch (q.kind()) {
            case CHOICE -> choiceProblem(q);
            case FILL_BLANK -> fillProblem(q);
            case TRUE_FALSE -> q.correct().equals(Challenge.TRUE) || q.correct().equals(Challenge.FALSE)
                    ? Optional.empty() : Optional.of("Choose whether the statement is True or False.");
            case ARRANGE -> arrangeProblem(q);
            case FIND_BUG -> bugProblem(q);
        };
        if (specific.isPresent()) {
            return specific;
        }
        if (q.hint() != null && q.hint().length() > MAX_HINT) {
            return Optional.of("The hint is too long (max " + MAX_HINT + " characters).");
        }
        return Optional.empty();
    }

    private static Optional<String> choiceProblem(QuestionDraft q) {
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
        return Optional.empty();
    }

    private static Optional<String> fillProblem(QuestionDraft q) {
        if (!q.prompt().contains(BLANK)) {
            return Optional.of("Type " + BLANK + " in the question where the student's answer goes.");
        }
        if (q.correct().isEmpty()) {
            return Optional.of("Type the answer the student has to type.");
        }
        if (q.correct().length() > MAX_TOKEN) {
            return Optional.of("The answer is too long (max " + MAX_TOKEN + " characters).");
        }
        if (q.accepted().size() > MAX_ACCEPTED) {
            return Optional.of("Use at most " + MAX_ACCEPTED + " other accepted answers.");
        }
        for (String a : q.accepted()) {
            if (a.length() > MAX_TOKEN) {
                return Optional.of("An accepted answer is too long (max " + MAX_TOKEN + " characters).");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> arrangeProblem(QuestionDraft q) {
        List<String> tokens = AnswerMatcher.splitTokens(q.correct());
        if (tokens.size() < 2) {
            return Optional.of("Type the tokens in the right order, separated by spaces (at least 2).");
        }
        if (tokens.size() > MAX_ARRANGE_TOKENS) {
            return Optional.of("Use at most " + MAX_ARRANGE_TOKENS + " tokens.");
        }
        for (String t : tokens) {
            if (t.length() > MAX_TOKEN) {
                return Optional.of("A token is too long (max " + MAX_TOKEN + " characters).");
            }
        }
        if (q.distractors().size() > MAX_DISTRACTORS) {
            return Optional.of("Use at most " + MAX_DISTRACTORS + " extra tokens.");
        }
        for (String d : q.distractors()) {
            if (d.contains(" ")) {
                return Optional.of("An extra token cannot contain a space.");
            }
            if (d.length() > MAX_TOKEN) {
                return Optional.of("An extra token is too long (max " + MAX_TOKEN + " characters).");
            }
            if (tokens.contains(d)) {
                return Optional.of("An extra token is the same as one of the real tokens.");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> bugProblem(QuestionDraft q) {
        int lines = q.prompt().split("\n", -1).length;
        if (lines < 2) {
            return Optional.of("Write at least 2 lines of code, one per line.");
        }
        if (lines > MAX_BUG_LINES) {
            return Optional.of("Use at most " + MAX_BUG_LINES + " lines of code.");
        }
        int bad;
        try {
            bad = Integer.parseInt(q.correct());
        } catch (NumberFormatException e) {
            return Optional.of("Type the number of the line that has the bug.");
        }
        if (bad < 1 || bad > lines) {
            return Optional.of("The bug line must be between 1 and " + lines + ".");
        }
        return Optional.empty();
    }
}
