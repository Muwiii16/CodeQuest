package codequest.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each question's chunk sits in a lesson of several small programs: which program it belongs to and on which line
 * of that program it starts (counting the lines the student sees, so a compiler message can name the right line), plus
 * what each program prints when it runs. Built from the lesson's program text, its output text and its chunks.
 */
public record ProgramLayout(List<Slot> slots, List<String> outputs, int blocks) {

    /** One chunk: its program (0-based) and the number of its first line in that program (1-based). */
    public record Slot(int block, int startLine) {
    }

    public ProgramLayout {
        slots = List.copyOf(slots);
        outputs = List.copyOf(outputs);
    }

    /**
     * @param program the program text; programs are separated by a line of ---
     * @param outputsText what each program prints, separated by ---, in the same order (may be null)
     * @param chunks the chunk of each question, in order (a null one counts as no lines)
     */
    public static ProgramLayout of(String program, String outputsText, List<String> chunks) {
        List<Integer> blockOf = ProgramCoverage.blockOfLines(program);
        int blocks = Math.max(1, ProgramCoverage.blockCount(program));
        List<Slot> slots = new ArrayList<>();
        int at = 0;
        for (String chunk : chunks) {
            int n = chunk == null ? 0 : ProgramCoverage.codeLines(chunk).size();
            int block = at < blockOf.size() ? blockOf.get(at) : blocks - 1;
            int startLine = 1;
            for (int i = at - 1; i >= 0 && blockOf.get(i) == block; i--) {
                startLine++;
            }
            slots.add(new Slot(block, startLine));
            at += n;
        }
        return new ProgramLayout(slots, splitOutputs(outputsText, blocks), blocks);
    }

    /** The output of each program; programs without one get an empty text. */
    public static List<String> splitOutputs(String text, int blocks) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        if (text != null && !text.isBlank()) {
            for (String line : text.split("\n", -1)) {
                if (ProgramCoverage.isMarker(line)) {
                    out.add(current.toString().strip());
                    current.setLength(0);
                } else if (line.stripTrailing().endsWith(ProgramCoverage.MARKER)) {
                    // a program that prints no newline: the AI glues the separator to its last output ("Hello---")
                    String before = line.stripTrailing();
                    current.append(before, 0, before.length() - ProgramCoverage.MARKER.length());
                    out.add(current.toString().strip());
                    current.setLength(0);
                } else {
                    current.append(line).append('\n');
                }
            }
            out.add(current.toString().strip());
        }
        while (out.size() < blocks) {
            out.add("");
        }
        return out;
    }

    /** How many questions belong to a program. */
    public int slotsIn(int block) {
        int n = 0;
        for (Slot s : slots) {
            n += s.block() == block ? 1 : 0;
        }
        return n;
    }
}
