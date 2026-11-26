package codequest.content;

import java.util.ArrayList;
import java.util.List;

/**
 * The few lines of code a question is about, shown beside it: the question's own line (marked) and the lines of the same
 * program that lead up to it. Nothing after it is shown, because those lines are the answers of later questions.
 *
 * @param lines the lines from first to last
 */
public record CodeSnippet(List<Line> lines) {

    /** One line: its number in its program (0 when unknown), the code, and whether the question is about it. */
    public record Line(int number, String text, boolean asked) {
    }

    public CodeSnippet {
        lines = List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * @param program the lesson's program text (programs separated by a line of ---), may be null
     * @param chunks the chunk of each question in the order they are asked
     * @param question which question (0-based)
     * @param own the chunk of that question, used by itself when it cannot be found in the program; may be null
     * @param max the most lines to show
     */
    public static CodeSnippet of(String program, List<String> chunks, int question, String own, int max) {
        if (program == null || chunks == null || question < 0 || question >= chunks.size() || own == null
                || chunks.get(question) == null || !ProgramCoverage.sameCode(own, chunks.get(question))) {
            return alone(own, max);
        }
        List<String> code = new ArrayList<>();
        for (String raw : program.split("\n", -1)) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty() && !ProgramCoverage.isMarker(trimmed)) {
                code.add(raw.stripTrailing().replace("\t", "    "));
            }
        }
        List<Integer> blockOf = ProgramCoverage.blockOfLines(program);
        int first = 0;
        for (int k = 0; k < question; k++) {
            first += chunks.get(k) == null ? 0 : ProgramCoverage.codeLines(chunks.get(k)).size();
        }
        List<String> asked = ProgramCoverage.codeLines(own);
        if (asked.isEmpty() || first + asked.size() > code.size()) {
            return alone(own, max);
        }
        for (int i = 0; i < asked.size(); i++) {
            if (!ProgramCoverage.sameCode(asked.get(i), code.get(first + i))) {
                return alone(own, max);
            }
        }
        int blockStart = first;
        while (blockStart > 0 && blockOf.get(blockStart - 1).equals(blockOf.get(first))) {
            blockStart--;
        }
        int last = first + Math.min(asked.size(), max) - 1;
        int from = Math.max(blockStart, last - max + 1);
        List<Line> out = new ArrayList<>();
        for (int i = from; i <= last; i++) {
            out.add(new Line(i - blockStart + 1, code.get(i), i >= first));
        }
        return new CodeSnippet(out);
    }

    /** Only the question's own lines, without numbers; empty when the question has no code. */
    private static CodeSnippet alone(String own, int max) {
        List<Line> out = new ArrayList<>();
        for (String line : ProgramCoverage.codeLines(own)) {
            if (out.size() < max) {
                out.add(new Line(0, line, true));
            }
        }
        return new CodeSnippet(out);
    }
}
