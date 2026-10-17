package codequest.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The growing, player-visible code block: every answered token is appended, correct or wrong. */
public class CodeBlock {

    public record Token(String text, boolean correct) {
    }

    private final List<Token> tokens = new ArrayList<>();

    public void append(Token token) {
        tokens.add(token);
    }

    public List<Token> tokens() {
        return Collections.unmodifiableList(tokens);
    }
}
