package codequest.session;

import codequest.content.ProgramLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * What the terminal shows for a finished program: the compiler's messages when the student's answers broke the code,
 * otherwise what the program prints. The messages and the sample output are written ahead of time with the lesson;
 * nothing is compiled or run, and no C is executed.
 */
public final class Terminal {

    public enum Kind { COMMAND, ERROR, WARNING, CODE, OUTPUT, NOTE }

    /** One line of the terminal. */
    public record Row(String text, Kind kind) {
    }

    /** The most compiler messages shown for one program. */
    public static final int MAX_MESSAGES = 3;

    private Terminal() {
    }

    /** Whether a compiler message is a warning (the program still builds) rather than an error. */
    static boolean isWarning(String message) {
        return message.trim().toLowerCase().startsWith("warning");
    }

    /** The terminal for one program of the lesson, as the student's answers left it. */
    public static List<Row> run(CodeBlock block, ProgramLayout layout, int program) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("$ gcc main.c -o main", Kind.COMMAND));
        boolean failed = false;
        boolean changedSilently = false;
        int shown = 0;
        for (CodeBlock.Token t : block.tokens()) {
            if (t.block() != program || t.correct() && t.error() == null) {
                continue;
            }
            if (t.error() == null) {
                changedSilently |= !t.correct();
                continue;
            }
            if (shown++ >= MAX_MESSAGES) {
                continue;
            }
            int offset = t.badLine() > 0 ? t.badLine() - 1 : 0;
            int line = t.startLine() + offset;
            boolean warning = isWarning(t.error());
            failed |= !warning;
            rows.add(new Row("main.c:" + line + ": " + t.error().trim(), warning ? Kind.WARNING : Kind.ERROR));
            List<String> code = new ArrayList<>();
            for (String raw : t.text().split("\n")) {
                if (!raw.isBlank()) {
                    code.add(raw.trim());
                }
            }
            if (offset < code.size()) {
                rows.add(new Row(String.format("%5d | %s", line, code.get(offset)), Kind.CODE));
            }
        }
        if (failed) {
            rows.add(new Row("The program was not built, so it did not run.", Kind.NOTE));
            return rows;
        }
        rows.add(new Row("$ ./main", Kind.COMMAND));
        String out = program < layout.outputs().size() ? layout.outputs().get(program) : "";
        if (out.isBlank()) {
            rows.add(new Row("(no output)", Kind.NOTE));
        } else {
            for (String line : out.split("\n", -1)) {
                rows.add(new Row(line, Kind.OUTPUT));
            }
        }
        if (changedSilently) {
            rows.add(new Row("Some of your answers changed the code, so this may not be exactly what it prints.", Kind.NOTE));
        }
        return rows;
    }
}
