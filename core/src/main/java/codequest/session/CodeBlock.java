package codequest.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The growing, player-visible code block. In a plain lesson every answered token is appended, correct or wrong. In a
 * lesson that builds a program every answered question appends its chunk of the program (several lines), green when
 * the answer was right and red when it was wrong. A lesson can hold several small programs; each chunk knows which
 * program it belongs to, and each program is shown on its own.
 */
public class CodeBlock {

    /**
     * One answer's addition.
     *
     * @param badLine for a wrong answer to a find-the-bug question, the number (counting the non-blank lines of the text
     *        from 1) of the line that held the bug: only that line is shown as wrong. 0 means the whole text is right or
     *        wrong together.
     * @param block which program of the lesson this belongs to (0 for the first)
     * @param startLine the number of the first line of this text in its program (1-based)
     * @param error the compiler message the terminal shows because of this answer, or null
     */
    public record Token(String text, boolean correct, int badLine, int block, int startLine, String error) {

        public Token(String text, boolean correct, int badLine) {
            this(text, correct, badLine, 0, 1, null);
        }

        public Token(String text, boolean correct) {
            this(text, correct, 0);
        }
    }

    /** One displayed line of code, already indented, with the color it is shown in and its number in its program. */
    public record Line(String text, boolean correct, int number, int block) {
    }

    private final List<Token> tokens = new ArrayList<>();

    public void append(Token token) {
        tokens.add(token);
    }

    public List<Token> tokens() {
        return Collections.unmodifiableList(tokens);
    }

    /** All lines of all programs, in order (the braces' indentation restarts with each program). */
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        int depth = 0;
        int currentBlock = -1;
        int number = 0;
        for (Token t : tokens) {
            if (t.block() != currentBlock) {
                currentBlock = t.block();
                depth = 0;
                number = 0;
            }
            int inChunk = 0;
            for (String raw : t.text().split("\n")) {
                String s = raw.trim();
                if (s.isEmpty()) {
                    continue;
                }
                inChunk++;
                number++;
                // a missed find-the-bug marks only the line that held the bug as wrong
                boolean right = t.correct() || (t.badLine() > 0 && inChunk != t.badLine());
                if (s.startsWith("}")) {
                    depth = Math.max(0, depth - 1);
                }
                out.add(new Line("    ".repeat(depth) + s, right, number, t.block()));
                if (s.endsWith("{")) {
                    depth++;
                }
            }
        }
        return out;
    }

    /** The lines of one program. */
    public List<Line> lines(int block) {
        List<Line> out = new ArrayList<>();
        for (Line l : lines()) {
            if (l.block() == block) {
                out.add(l);
            }
        }
        return out;
    }
}
