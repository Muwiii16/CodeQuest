package codequest.data;

import java.util.Locale;

/**
 * The code a professor gives to the students of a class so that they can enroll themselves: 6 letters and digits (never
 * 0, O, 1 or I, which look alike). The database makes the codes; this class only reads and shows them.
 */
public final class JoinCode {

    public static final int LENGTH = 6;

    private JoinCode() {
    }

    /** What the student typed, reduced to what counts: capital letters and digits only ("ab-c d12" gives "ABCD12"). */
    public static String normalize(String typed) {
        if (typed == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : typed.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Whether the text holds a whole code. */
    public static boolean isComplete(String typed) {
        return normalize(typed).length() == LENGTH;
    }

    /** The code as it is shown and read aloud: three characters, a dash, three characters ("WWU-RJC"). */
    public static String display(String code) {
        String c = normalize(code);
        return c.length() == LENGTH ? c.substring(0, 3) + "-" + c.substring(3) : c;
    }

    /** Whether a character may be typed into the code box: a letter, a digit, a dash or a space (the rest is ignored). */
    public static boolean typable(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == ' ';
    }
}
