package codequest.content;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a lesson to what it is allowed to teach. A lesson may use its own topics and those of the lessons before it,
 * nothing else: a lesson about printf must not use loops, an if-else lesson may reuse printf. The boilerplate every
 * program needs (#include, main, return, printf and comments) is always allowed. This looks at the program text for the
 * constructs of each topic, so it is a safety net for what the AI writes, not a C parser.
 */
public final class ScopeCheck {

    private static final Pattern STRINGS = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
    private static final Pattern LOOP_OR_IF = Pattern.compile("\\b(if|else|for|while|do|switch|case|break|continue)\\b");
    private static final Pattern DECLARATION = Pattern.compile("\\b(int|float|double|char|long|short)\\s+\\*?\\s*[A-Za-z_]\\w*\\s*(=|;|,|\\[)");
    private static final Pattern FUNCTION_DEF = Pattern.compile("\\b(int|void|float|double|char|long)\\s+([A-Za-z_]\\w*)\\s*\\([^)]*\\)\\s*[{;]");
    private static final Pattern ARRAY = Pattern.compile("[A-Za-z_]\\w*\\s*\\[");
    private static final Pattern POINTER = Pattern.compile("\\b(int|float|double|char|void|long)\\s*\\*|\\bNULL\\b|(^|[=(,;{\\s])\\*\\s*[A-Za-z_]\\w*\\s*=[^=]");
    private static final Pattern SCANF = Pattern.compile("\\b(scanf|gets|fgets|getchar)\\b");

    private ScopeCheck() {
    }

    /** The topics a lesson may use: its own, those taught before, and SYNTAX (always). */
    public static Set<CTopic> allowed(Set<CTopic> own, Set<CTopic> taughtBefore) {
        Set<CTopic> out = EnumSet.of(CTopic.SYNTAX);
        out.addAll(own);
        out.addAll(taughtBefore);
        return out;
    }

    /** What in the program is outside the allowed topics, in words; empty when everything is in scope. */
    public static List<String> violations(String program, Set<CTopic> allowed) {
        List<String> out = new ArrayList<>();
        if (program == null) {
            return out;
        }
        String code = COMMENTS.matcher(STRINGS.matcher(program).replaceAll("\"\"")).replaceAll(" ");
        if (!allowed.contains(CTopic.CONTROL_STRUCTURES)) {
            Matcher m = LOOP_OR_IF.matcher(code);
            if (m.find()) {
                out.add("The program uses \"" + m.group() + "\" (if, loops and switch), which is not part of this lesson's topics.");
            }
        }
        if (!allowed.contains(CTopic.VARIABLES)) {
            Matcher m = DECLARATION.matcher(code);
            if (m.find()) {
                out.add("The program declares a variable (" + m.group(1) + "), which is not part of this lesson's topics.");
            } else if (SCANF.matcher(code).find()) {
                out.add("The program reads input, which needs variables, and variables are not part of this lesson's topics.");
            }
        }
        if (!allowed.contains(CTopic.FUNCTIONS)) {
            Matcher m = FUNCTION_DEF.matcher(code);
            while (m.find()) {
                if (!m.group(2).equals("main")) {
                    out.add("The program defines a function (" + m.group(2) + "), which is not part of this lesson's topics.");
                    break;
                }
            }
        }
        if (!allowed.contains(CTopic.ARRAYS) && ARRAY.matcher(code).find()) {
            out.add("The program uses an array, which is not part of this lesson's topics.");
        }
        if (!allowed.contains(CTopic.POINTERS) && POINTER.matcher(code).find()) {
            out.add("The program uses a pointer, which is not part of this lesson's topics.");
        }
        return out;
    }

    private static final Pattern NEGATION = Pattern.compile("(\\bno\\b|\\bnot\\b|\\bwithout\\b|\\bdon'?t\\b|\\bavoid\\b|\\bskip\\b|\\bexcept\\b|\\bnever\\b)[^.;\\n]{0,30}$");
    private static final Pattern[] MENTION = new Pattern[CTopic.values().length];

    static {
        MENTION[CTopic.VARIABLES.ordinal()] = Pattern.compile("\\b(variables?|data ?types?|integers?|floats?|doubles?|scanf|user input)\\b");
        MENTION[CTopic.CONTROL_STRUCTURES.ordinal()] = Pattern.compile("\\b(if[- /]else|else|if statements?|loops?|while|switch|conditionals?)\\b");
        MENTION[CTopic.FUNCTIONS.ordinal()] = Pattern.compile("(?<!main )\\bfunctions?\\b");
        MENTION[CTopic.ARRAYS.ordinal()] = Pattern.compile("\\barrays?\\b");
        MENTION[CTopic.POINTERS.ordinal()] = Pattern.compile("\\bpointers?\\b");
    }

    /**
     * The topics a professor's own description asks for ("... variables and an if-else ..."), so that what the lesson
     * covers is what was described and nothing else. A mention that is negated ("no loops", "without variables") does not
     * count. SYNTAX (printf, #include, main, return) is never inferred because it is always allowed.
     */
    public static Set<CTopic> infer(String description) {
        Set<CTopic> out = EnumSet.noneOf(CTopic.class);
        if (description == null) {
            return out;
        }
        String text = description.toLowerCase(java.util.Locale.ROOT);
        for (CTopic t : CTopic.values()) {
            Pattern p = MENTION[t.ordinal()];
            if (p == null) {
                continue;
            }
            Matcher m = p.matcher(text);
            while (m.find()) {
                String before = text.substring(Math.max(0, m.start() - 40), m.start());
                if (!NEGATION.matcher(before).find()) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    /** Parses a stored list such as {@code "SYNTAX,VARIABLES"}; unknown names are ignored, null or blank is empty. */
    public static Set<CTopic> parseTopics(String stored) {
        Set<CTopic> out = EnumSet.noneOf(CTopic.class);
        if (stored != null) {
            for (String part : stored.split(",")) {
                try {
                    out.add(CTopic.valueOf(part.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // not a topic: skip it
                }
            }
        }
        return out;
    }

    public static String formatTopics(Set<CTopic> topics) {
        List<String> names = new ArrayList<>();
        for (CTopic t : CTopic.values()) {
            if (topics.contains(t)) {
                names.add(t.name());
            }
        }
        return String.join(",", names);
    }
}
