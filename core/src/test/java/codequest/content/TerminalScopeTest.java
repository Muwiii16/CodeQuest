package codequest.content;

import codequest.session.CodeBlock;
import codequest.session.StageRun;
import codequest.session.Terminal;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerminalScopeTest {

    // ---------- several small programs ----------

    private static final String TWO = "#include <stdio.h>\nint main() {\n    printf(\"A\");\n}\n---\n#include <stdio.h>\nint main() {\n    printf(\"B\");\n}";

    @Test
    void programsAreSeparatedByALineOfDashesAndTheDashesAreNotCode() {
        assertEquals(8, ProgramCoverage.codeLines(TWO).size());
        assertEquals(2, ProgramCoverage.blockCount(TWO));
        assertEquals(List.of(0, 0, 0, 0, 1, 1, 1, 1), ProgramCoverage.blockOfLines(TWO));
        assertEquals(1, ProgramCoverage.blockCount("int x;"));
        assertEquals(0, ProgramCoverage.blockCount(""));
    }

    @Test
    void aQuestionMustStayInsideOneProgram() {
        List<String> ok = List.of("#include <stdio.h>\nint main() {", "printf(\"A\");\n}", "#include <stdio.h>\nint main() {", "printf(\"B\");\n}");
        assertTrue(ProgramCoverage.check(TWO, ok).complete(), ProgramCoverage.describe(ProgramCoverage.check(TWO, ok)));
        List<String> crossing = List.of("#include <stdio.h>\nint main() {\nprintf(\"A\");\n}\n#include <stdio.h>", "int main() {", "printf(\"B\");\n}");
        assertTrue(ProgramCoverage.check(TWO, crossing).problem().contains("two different programs"));
    }

    @Test
    void theLayoutSaysWhichProgramAndWhichLineEachChunkStartsOn() {
        List<String> chunks = List.of("#include <stdio.h>\nint main() {", "printf(\"A\");\n}", "#include <stdio.h>\nint main() {", "printf(\"B\");\n}");
        ProgramLayout layout = ProgramLayout.of(TWO, "A\n---\nB", chunks);
        assertEquals(2, layout.blocks());
        assertEquals(new ProgramLayout.Slot(0, 1), layout.slots().get(0));
        assertEquals(new ProgramLayout.Slot(0, 3), layout.slots().get(1));
        assertEquals(new ProgramLayout.Slot(1, 1), layout.slots().get(2));
        assertEquals(new ProgramLayout.Slot(1, 3), layout.slots().get(3));
        assertEquals(List.of("A", "B"), layout.outputs());
        assertEquals(2, layout.slotsIn(0));
    }

    @Test
    void outputsArePaddedSoEveryProgramHasOne() {
        assertEquals(List.of("A", ""), ProgramLayout.splitOutputs("A", 2));
        assertEquals(List.of("", ""), ProgramLayout.splitOutputs(null, 2));
        // a program that prints no newline: the separator is glued to its output
        assertEquals(List.of("Hello, World!", "CodeQuest"), ProgramLayout.splitOutputs("Hello, World!---\nCodeQuest", 2));
    }

    // ---------- what a wrong answer does to the code, and the terminal ----------

    private static Challenge semicolon() {
        return new Challenge("s", CTopic.SYNTAX, Difficulty.EASY, "printf(\"A\")___", ";", List.of(":", ","), null, AnswerKind.CHOICE,
                List.of(), null, "end the statement", 0, "why", ":: ignored").withErrorText(
                ": :: error: expected ';' before '}' token\n, :: error: expected ';' before ',' token");
    }

    @Test
    void aWrongAnswerPutsTheStudentsMistakeIntoTheCode() {
        Challenge c = semicolon();
        assertEquals("printf(\"A\"):", c.wrongCode(":"));
        assertEquals("printf(\"A\")", c.wrongCode(null)); // out of time: the token is missing
        Challenge arrange = new Challenge("a", CTopic.SYNTAX, Difficulty.EASY, "Build it:", "int x = 1 ;", List.of("float"), null,
                AnswerKind.ARRANGE, List.of());
        assertEquals("x int = 1;", arrange.wrongCode("x int = 1 ;"));
        Challenge bug = new Challenge("b", CTopic.SYNTAX, Difficulty.EASY, "ok;\nbad", "2", List.of(), null, AnswerKind.FIND_BUG,
                List.of(), "ok;\nbad;");
        assertEquals("ok;\nbad", bug.wrongCode("1"));
        assertNull(new Challenge("t", CTopic.SYNTAX, Difficulty.EASY, "s", "True", List.of(), null, AnswerKind.TRUE_FALSE, List.of())
                .wrongCode("False"));
    }

    @Test
    void theCompilerMessageDependsOnTheWrongAnswer() {
        Challenge c = semicolon();
        assertEquals("error: expected ';' before '}' token", c.errorFor(":"));
        assertEquals("error: expected ';' before ',' token", c.errorFor(","));
        assertNull(c.errorFor("."), "no line for that answer: it is not an error we know about");
        Challenge one = c.withErrorText("error: one message for everything");
        assertEquals("error: one message for everything", one.errorFor("anything"));
        Challenge named = c.withErrorText("1 :: \nvoid :: error: expected expression before 'void'\n* :: error: '{answer}' undeclared");
        assertNull(named.errorFor("1"), "an answer listed with no message compiles");
        assertEquals("error: expected expression before 'void'", named.errorFor("void"));
        assertEquals("error: 'foo' undeclared", named.errorFor("foo"));
        assertNull(named.errorFor("42"), "a number is valid there, so it builds");
        assertNull(named.errorFor("+"));
    }

    private static StageRun runOf(String program, String outputs, List<Challenge> slots) {
        ProgramChallengeProvider provider = new ProgramChallengeProvider(slots);
        StageRun run = new StageRun(ProgramChallengeProvider.stageFor("t", "m", provider.slots()), Difficulty.NORMAL,
                StageRun.Mode.CAMPAIGN, provider, false);
        List<String> chunks = provider.slots().stream().map(Challenge::codeChunk).toList();
        run.useLayout(ProgramLayout.of(program, outputs, chunks));
        return run;
    }

    @Test
    void aCppLessonShowsTheCppCompilerAndFile() {
        Challenge a = new Challenge("a", CTopic.SYNTAX, Difficulty.NORMAL, "int main() {", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "int main() {", null).withErrorText("");
        Challenge close = new Challenge("c", CTopic.SYNTAX, Difficulty.NORMAL, "}", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "}", null);
        String program = "int main() {\nprintf(\"A\");\n}";
        StageRun run = runOf(program, "A", List.of(a.withSlot(1), semicolon().withSlot(2), close.withSlot(3)));
        run.useLanguage(Language.CPP);
        run.nextChallenge();
        run.answer("x", 0f);
        run.nextChallenge();
        run.answer(":", 0f);
        run.nextChallenge();
        run.answer("x", 0f);
        List<Terminal.Row> rows = run.terminal();
        assertEquals("$ g++ main.cpp -o main", rows.get(0).text());
        assertTrue(rows.stream().anyMatch(r -> r.text().startsWith("main.cpp:2: ")), rows.toString());
    }

    @Test
    void aFinishedProgramShowsItsOutputAndAWrongAnswerShowsTheCompilerError() {
        Challenge a = new Challenge("a", CTopic.SYNTAX, Difficulty.NORMAL, "int main() {", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "int main() {", null).withErrorText("");
        Challenge b = semicolon();
        Challenge close = new Challenge("c", CTopic.SYNTAX, Difficulty.NORMAL, "}", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "}", null);
        String program = "int main() {\nprintf(\"A\");\n}";

        // all right: the output
        StageRun good = runOf(program, "A", List.of(a.withSlot(1), b.withSlot(2), close.withSlot(3)));
        assertTrue(good.terminal().isEmpty(), "nothing runs until the program is finished");
        good.nextChallenge();
        good.answer("x", 0f);
        good.nextChallenge();
        good.answer(";", 0f);
        good.nextChallenge();
        good.answer("x", 0f);
        List<Terminal.Row> rows = good.terminal();
        assertEquals("$ gcc main.c -o main", rows.get(0).text());
        assertTrue(rows.stream().anyMatch(r -> r.kind() == Terminal.Kind.OUTPUT && r.text().equals("A")));
        assertTrue(rows.stream().noneMatch(r -> r.kind() == Terminal.Kind.ERROR));

        // a wrong answer in the middle: the compiler message, on the right line, and the program does not run
        StageRun bad = runOf(program, "A", List.of(a.withSlot(1), b.withSlot(2), close.withSlot(3)));
        bad.nextChallenge();
        bad.answer("x", 0f);
        bad.nextChallenge();
        bad.answer(":", 0f);
        bad.nextChallenge();
        bad.answer("x", 0f);
        List<Terminal.Row> failed = bad.terminal();
        assertTrue(failed.stream().anyMatch(r -> r.kind() == Terminal.Kind.ERROR
                && r.text().equals("main.c:2: error: expected ';' before '}' token")), failed.toString());
        assertTrue(failed.stream().anyMatch(r -> r.kind() == Terminal.Kind.CODE && r.text().contains("printf(\"A\"):")));
        assertTrue(failed.stream().noneMatch(r -> r.kind() == Terminal.Kind.OUTPUT), "it did not build, so it did not run");
        // the bad line is red in the code block, the rest stays as it was
        assertTrue(bad.codeBlock().lines().stream().anyMatch(l -> !l.correct() && l.text().contains(":")));
    }

    @Test
    void aWrongAnswerThatStillBuildsWarnsThatTheOutputMayDiffer() {
        Challenge price = new Challenge("p", CTopic.VARIABLES, Difficulty.NORMAL, "___ price = 9.99;", "float", List.of("int", "char"), null,
                AnswerKind.CHOICE, List.of(), null, "a price").withErrorText("int :: \nchar :: ");
        StageRun run = runOf("float price = 9.99;", "9.99", List.of(price));
        run.nextChallenge();
        run.answer("int", 0f);
        List<Terminal.Row> rows = run.terminal();
        assertTrue(rows.stream().anyMatch(r -> r.kind() == Terminal.Kind.OUTPUT));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == Terminal.Kind.NOTE && r.text().contains("may not be exactly")));
    }

    @Test
    void severalProgramsEachGetTheirOwnTerminalAndTheCodeBlockShowsOneAtATime() {
        Challenge one = new Challenge("1", CTopic.SYNTAX, Difficulty.NORMAL, "printf(\"A\");", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "printf(\"A\");", null);
        Challenge two = new Challenge("2", CTopic.SYNTAX, Difficulty.NORMAL, "printf(\"B\");", "x", List.of("y"), null, AnswerKind.TRUE_FALSE,
                List.of(), "printf(\"B\");", null);
        StageRun run = runOf("printf(\"A\");\n---\nprintf(\"B\");", "A\n---\nB", List.of(one.withSlot(1), two.withSlot(2)));
        assertEquals(0, run.displayBlock());
        run.nextChallenge();
        run.answer("x", 0f);
        assertEquals(1, run.displayBlock(), "the next question starts the second program");
        assertTrue(run.terminal().stream().anyMatch(r -> r.text().equals("A")), "the first program has run");
        run.nextChallenge();
        run.answer("x", 0f);
        assertTrue(run.terminal().stream().anyMatch(r -> r.text().equals("B")));
        assertEquals(1, run.codeBlock().lines(0).size());
        assertEquals(1, run.codeBlock().lines(1).size());
        assertEquals(1, run.codeBlock().lines(1).get(0).number(), "line numbers restart in each program");
    }

    // ---------- scope ----------

    private static Set<CTopic> only(CTopic... topics) {
        return ScopeCheck.allowed(EnumSet.copyOf(List.of(topics)), Set.of());
    }

    private static final String PRINTF_ONLY = "#include <stdio.h>\nint main() {\n    printf(\"Hello, if you can read this\\n\");\n    return 0;\n}";

    @Test
    void aPrintfLessonMayUseNothingButPrintf() {
        Set<CTopic> allowed = only(CTopic.SYNTAX);
        assertTrue(ScopeCheck.violations(PRINTF_ONLY, allowed).isEmpty(), "an if inside a string is not an if");
        assertFalse(ScopeCheck.violations("int main() {\n    int x = 5;\n    return 0;\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int main() {\n    for (;;) { }\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int main() {\n    if (1) { }\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int add(int a) {\n    return a;\n}\nint main() {\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int main() {\n    printf(\"%d\", nums[0]);\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int main() {\n    scanf(\"%d\", &x);\n}", allowed).isEmpty());
        assertTrue(ScopeCheck.violations("/* if for while */\nint main() {\n    return 0;\n}", allowed).isEmpty(), "comments are ignored");
    }

    @Test
    void laterLessonsMayReuseWhatWasTaughtBefore() {
        // lesson 2 teaches control structures; printf and variables came before
        Set<CTopic> allowed = ScopeCheck.allowed(Set.of(CTopic.CONTROL_STRUCTURES), Set.of(CTopic.SYNTAX, CTopic.VARIABLES));
        String program = "int main() {\n    int age = 18;\n    if (age >= 18) {\n        printf(\"Adult\");\n    }\n    return 0;\n}";
        assertTrue(ScopeCheck.violations(program, allowed).isEmpty(), ScopeCheck.violations(program, allowed).toString());
        // but it still may not jump ahead to what has not been taught
        assertFalse(ScopeCheck.violations("int main() {\n    int nums[3];\n}", allowed).isEmpty());
        assertFalse(ScopeCheck.violations("int main() {\n    int *p;\n}", allowed).isEmpty());
        // multiplication is not a pointer
        assertTrue(ScopeCheck.violations("int main() {\n    int a = 2 * 3;\n}", allowed).isEmpty());
    }

    @Test
    void topicsAreStoredAsAList() {
        assertEquals(Set.of(CTopic.SYNTAX, CTopic.ARRAYS), ScopeCheck.parseTopics("SYNTAX, arrays ,nonsense"));
        assertTrue(ScopeCheck.parseTopics(null).isEmpty());
        assertEquals("SYNTAX,ARRAYS", ScopeCheck.formatTopics(EnumSet.of(CTopic.ARRAYS, CTopic.SYNTAX)));
    }

    @Test
    void aDraftThatWandersOutsideTheLessonIsRejected() {
        QuestionDraft loop = new QuestionDraft(CTopic.CONTROL_STRUCTURES, Difficulty.EASY, "for (int i = 0; i < 3; i___) {", "++",
                List.of(), null, AnswerKind.FILL_BLANK, List.of(), null, "count up", "why");
        GeneratedLesson draft = new GeneratedLesson("t", "for (int i = 0; i < 3; i++) {", "x", List.of(loop));
        String problems = draft.problems(only(CTopic.SYNTAX)).orElse("");
        assertTrue(problems.contains("control structures") || problems.contains("control"), problems);
        assertTrue(problems.contains("loops") || problems.contains("if, loops"), problems);
        assertFalse(draft.problems(ScopeCheck.allowed(Set.of(CTopic.CONTROL_STRUCTURES), Set.of())).orElse("")
                .contains("not one of this lesson's topics"));
    }

    @Test
    void aDraftNeedsOneOutputPerProgram() {
        QuestionDraft q = new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "printf(\"A\")___", ";", List.of(":"), null, AnswerKind.CHOICE,
                List.of(), null, "end it", "why");
        assertTrue(new GeneratedLesson("t", "printf(\"A\");", null, List.of(q)).problems().orElse("").contains("outputs"));
        assertTrue(new GeneratedLesson("t", "printf(\"A\");", "A", List.of(q)).problems().isEmpty(),
                new GeneratedLesson("t", "printf(\"A\");", "A", List.of(q)).problems().orElse(""));
        assertTrue(new GeneratedLesson("t", "printf(\"A\");", "A\n---\nB", List.of(q)).problems().orElse("").contains("one output for each"));
    }

    @Test
    void theDescriptionDecidesTheTopics() {
        // a printf-only description asks for no topic beyond the basics
        assertEquals(java.util.Set.of(), ScopeCheck.infer("Create multiple sets of code blocks about the basic concept of printf and "
                + "the important parts such as the #include, the main function and the return 0, and such."));
        assertEquals(java.util.Set.of(CTopic.VARIABLES, CTopic.CONTROL_STRUCTURES),
                ScopeCheck.infer("Teach variables and an if-else that prints Adult."));
        assertEquals(java.util.Set.of(CTopic.FUNCTIONS), ScopeCheck.infer("Writing a function that adds two numbers"));
    }

    @Test
    void aNegatedMentionDoesNotCount() {
        assertEquals(java.util.Set.of(), ScopeCheck.infer("Only printf, no loops and without variables please."));
        assertEquals(java.util.Set.of(CTopic.CONTROL_STRUCTURES), ScopeCheck.infer("No variables. Teach loops."));
    }
}
