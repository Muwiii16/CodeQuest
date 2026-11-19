package codequest.content;

import java.util.List;
import java.util.Set;

/**
 * The lines of a lesson's program that one question adds to the student's code block. A multiple-choice or
 * fill-in-the-blank question simply is its chunk with the blank filled in; an arrange question is its tokens put
 * together; a true/false or find-the-bug question has to have its chunk written, because its text is not the code.
 */
public final class CodeChunks {

    private static final Set<String> KEYWORDS_BEFORE_PAREN = Set.of("if", "for", "while", "switch", "return", "sizeof");

    private CodeChunks() {
    }

    /**
     * The chunk for a question, or null when it cannot be worked out and was not written.
     * {@code explicit} (when not blank) always wins.
     */
    public static String of(AnswerKind kind, String prompt, String correct, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        return switch (kind) {
            case CHOICE, FILL_BLANK -> {
                int at = prompt.indexOf(QuestionDraft.BLANK);
                yield at < 0 ? null : (prompt.substring(0, at) + correct + prompt.substring(at + QuestionDraft.BLANK.length())).trim();
            }
            case ARRANGE -> joinTokens(AnswerMatcher.splitTokens(correct));
            case TRUE_FALSE, FIND_BUG -> null;
        };
    }

    /** Puts tokens back together the way code is written: {@code printf ( "Hi" ) ;} becomes {@code printf("Hi");}. */
    public static String joinTokens(List<String> tokens) {
        StringBuilder out = new StringBuilder();
        String prev = null;
        for (String t : tokens) {
            boolean space = prev != null;
            if (prev != null) {
                if (t.equals(")") || t.equals("]") || t.equals(";") || t.equals(",") || t.equals("[")
                        || t.equals("++") || t.equals("--")) {
                    space = false;
                }
                if (prev.equals("(") || prev.equals("[")) {
                    space = false;
                }
                if (t.equals("(") && isName(prev) && !KEYWORDS_BEFORE_PAREN.contains(prev)) {
                    space = false; // a call or a declaration: main(), printf(
                }
            }
            if (space) {
                out.append(' ');
            }
            out.append(t);
            prev = t;
        }
        return out.toString();
    }

    private static boolean isName(String s) {
        return !s.isEmpty() && (Character.isLetter(s.charAt(0)) || s.charAt(0) == '_');
    }
}
