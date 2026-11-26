package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSnippetTest {

    private static final String PROGRAM = "#include <stdio.h>\n\nint main() {\n    int age = 20;\n    if (age >= 18) {\n"
            + "        printf(\"Adult\");\n    }\n    return 0;\n}\n---\nint main() {\n    return 0;\n}";
    private static final List<String> CHUNKS = List.of("#include <stdio.h>", "int main() {", "int age = 20;",
            "if (age >= 18) {", "printf(\"Adult\");", "}", "return 0;\n}", "int main() {", "return 0;\n}");

    private static CodeSnippet at(int question, int max) {
        return CodeSnippet.of(PROGRAM, CHUNKS, question, CHUNKS.get(question), max);
    }

    @Test
    void showsTheLineWithTheLinesBeforeItAndNothingAfter() {
        CodeSnippet s = at(5, 6);
        assertEquals(6, s.lines().size());
        CodeSnippet.Line last = s.lines().get(5);
        assertEquals("    }", last.text());
        assertTrue(last.asked());
        assertEquals(6, last.number());
        assertEquals(1, s.lines().stream().filter(CodeSnippet.Line::asked).count());
    }

    @Test
    void theFirstLineStandsAlone() {
        assertEquals(1, at(0, 6).lines().size());
    }

    @Test
    void aChunkOfTwoLinesMarksBoth() {
        CodeSnippet s = at(6, 4);
        assertEquals(4, s.lines().size());
        assertEquals(2, s.lines().stream().filter(CodeSnippet.Line::asked).count());
    }

    @Test
    void linesOfAnEarlierProgramAreNotShown() {
        CodeSnippet s = at(7, 6);
        assertEquals(1, s.lines().size());
        assertEquals(1, s.lines().get(0).number());
    }

    @Test
    void aChunkThatDoesNotFitTheProgramIsShownByItself() {
        CodeSnippet s = CodeSnippet.of(PROGRAM, Arrays.asList("something else", "x = 1;"), 1, "x = 1;", 6);
        assertEquals(List.of(new CodeSnippet.Line(0, "x = 1;", true)), s.lines());
        assertEquals(1, CodeSnippet.of(null, null, 3, "}", 6).lines().size());
        assertTrue(CodeSnippet.of(null, null, 0, null, 6).isEmpty());
    }
}
