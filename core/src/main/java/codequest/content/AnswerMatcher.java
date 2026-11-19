package codequest.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether an answer is right. Pure text comparison, so it is unit tested and runs instantly during play.
 * C is case sensitive, so case always matters; only spacing is forgiven where spacing carries no meaning.
 */
public final class AnswerMatcher {

    private AnswerMatcher() {
    }

    /** Whether the answer is right for the challenge's kind. */
    public static boolean matches(Challenge c, String answer) {
        if (answer == null) {
            return false;
        }
        return switch (c.kind()) {
            case CHOICE, TRUE_FALSE, FIND_BUG -> answer.equals(c.correctToken());
            case FILL_BLANK -> {
                if (sameWithoutSpaces(answer, c.correctToken())) {
                    yield true;
                }
                for (String other : c.accepted()) {
                    if (sameWithoutSpaces(answer, other)) {
                        yield true;
                    }
                }
                yield false;
            }
            case ARRANGE -> splitTokens(answer).equals(splitTokens(c.correctToken()));
        };
    }

    /** "i ++" and "i++" are the same typed answer: all spaces are ignored. */
    static boolean sameWithoutSpaces(String a, String b) {
        return strip(a).equals(strip(b));
    }

    private static String strip(String s) {
        return s.replaceAll("\\s+", "");
    }

    /** Splits an arranged answer into its tokens (separated by spaces). */
    public static List<String> splitTokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
