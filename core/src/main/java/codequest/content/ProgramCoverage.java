package codequest.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a lesson's questions, in order, add up to its full program: every chunk must be the next lines of the
 * program (indentation and blank lines do not matter), and nothing may be left over. Pure logic, unit tested.
 */
public final class ProgramCoverage {

    /**
     * @param programLines how many lines of code the program has
     * @param coveredLines how many of them the questions cover, from the top
     * @param problem what is wrong (a chunk that does not match, or a question with no chunk); null when nothing is
     */
    public record Report(int programLines, int coveredLines, String problem) {

        /** The questions rebuild the whole program exactly. */
        public boolean complete() {
            return problem == null && programLines > 0 && coveredLines == programLines;
        }
    }

    private ProgramCoverage() {
    }

    /** A line of only this separates the programs of a lesson that has several small ones. */
    public static final String MARKER = "---";

    public static boolean isMarker(String line) {
        return line.trim().equals(MARKER);
    }

    /** The non-blank lines of code, trimmed. The ---  lines that separate programs are not code and are left out. */
    public static List<String> codeLines(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) {
            for (String raw : text.split("\n", -1)) {
                String s = raw.trim();
                if (!s.isEmpty() && !isMarker(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** For each line of code (as {@link #codeLines} counts them), the number of the program (0, 1, ...) it belongs to. */
    public static List<Integer> blockOfLines(String text) {
        List<Integer> out = new ArrayList<>();
        int block = 0;
        boolean any = false;
        if (text != null) {
            for (String raw : text.split("\n", -1)) {
                String s = raw.trim();
                if (isMarker(s)) {
                    if (any) {
                        block++;
                        any = false;
                    }
                } else if (!s.isEmpty()) {
                    out.add(block);
                    any = true;
                }
            }
        }
        return out;
    }

    /** How many programs the text holds. */
    public static int blockCount(String text) {
        List<Integer> blocks = blockOfLines(text);
        return blocks.isEmpty() ? 0 : blocks.get(blocks.size() - 1) + 1;
    }

    /** Spacing inside a line is not part of the code that matters here: {@code {10, 20}} and {@code { 10 , 20 }} match. */
    static boolean sameCode(String a, String b) {
        return a.replaceAll("\\s+", "").equals(b.replaceAll("\\s+", ""));
    }

    /** {@code chunks} are the questions' chunks in order; a null entry is a question whose chunk is missing. */
    public static Report check(String program, List<String> chunks) {
        List<String> lines = codeLines(program);
        List<Integer> blockOf = blockOfLines(program);
        int at = 0;
        for (int k = 0; k < chunks.size(); k++) {
            String chunk = chunks.get(k);
            if (chunk == null) {
                return new Report(lines.size(), at, "Question " + (k + 1) + " needs the code it adds (write it in the question).");
            }
            int first = at;
            for (String line : codeLines(chunk)) {
                if (at >= lines.size()) {
                    return new Report(lines.size(), at, "Question " + (k + 1) + " adds \"" + line
                            + "\", but the program has no more lines.");
                }
                if (!sameCode(line, lines.get(at))) {
                    return new Report(lines.size(), at, "Question " + (k + 1) + " adds \"" + line
                            + "\", but the program has \"" + lines.get(at) + "\" at that point.");
                }
                at++;
            }
            if (at > first && !blockOf.get(first).equals(blockOf.get(at - 1))) {
                return new Report(lines.size(), first, "Question " + (k + 1)
                        + " adds code from two different programs. Each question must stay inside one program (the --- lines).");
            }
        }
        return new Report(lines.size(), at, null);
    }

    /**
     * Same check for a lesson whose chunks have a question for each difficulty: {@code slots} holds, for each chunk in
     * program order, the chunk of every difficulty variant. All the variants of a chunk must add the same code.
     */
    public static Report checkSlots(String program, List<List<String>> slots) {
        List<String> firsts = new ArrayList<>();
        for (int k = 0; k < slots.size(); k++) {
            List<String> variants = slots.get(k);
            String first = variants.isEmpty() ? null : variants.get(0);
            for (String other : variants) {
                if (first != null && other != null && !codeLines(first).stream().map(s -> s.replaceAll("\\s+", "")).toList()
                        .equals(codeLines(other).stream().map(s -> s.replaceAll("\\s+", "")).toList())) {
                    return new Report(codeLines(program).size(), 0, "Question " + (k + 1)
                            + ": its difficulty levels add different code. Every level must add the same lines of the program.");
                }
                if (other == null) {
                    first = null;
                }
            }
            firsts.add(first);
        }
        return check(program, firsts);
    }

    /** One sentence for the professor. */
    public static String describe(Report r) {
        if (r.problem() != null) {
            return r.problem();
        }
        if (r.programLines() == 0) {
            return "Write the program first.";
        }
        if (r.complete()) {
            return "The questions build the whole program (" + r.programLines() + " lines).";
        }
        return r.coveredLines() + " of " + r.programLines() + " lines are covered. Add questions for the rest.";
    }
}
