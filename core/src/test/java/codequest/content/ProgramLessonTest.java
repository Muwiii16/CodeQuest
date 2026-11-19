package codequest.content;

import codequest.session.CodeBlock;
import codequest.session.StageRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProgramLessonTest {

    // ── chunks ──

    @Test
    void aChoiceOrFillQuestionIsItsChunkWithTheBlankFilledIn() {
        assertEquals("#include <stdio.h>", CodeChunks.of(AnswerKind.FILL_BLANK, "#include <___.h>", "stdio", null));
        assertEquals("float price = 9.99;", CodeChunks.of(AnswerKind.CHOICE, "___ price = 9.99;", "float", null));
        assertEquals("return 0;\n}", CodeChunks.of(AnswerKind.FILL_BLANK, "return ___;\n}", "0", null));
    }

    @Test
    void aWrittenChunkAlwaysWins() {
        assertEquals("x;", CodeChunks.of(AnswerKind.CHOICE, "___", "y", "  x;  "));
    }

    @Test
    void trueFalseAndFindTheBugNeedAWrittenChunk() {
        assertNull(CodeChunks.of(AnswerKind.TRUE_FALSE, "statement", "True", null));
        assertNull(CodeChunks.of(AnswerKind.FIND_BUG, "a\nb", "1", " "));
        assertNull(CodeChunks.of(AnswerKind.CHOICE, "no blank here", "x", null));
    }

    @Test
    void arrangedTokensAreWrittenLikeCode() {
        assertEquals("printf(\"Hi\");", CodeChunks.of(AnswerKind.ARRANGE, "p", "printf ( \"Hi\" ) ;", null));
        assertEquals("int main() {", CodeChunks.of(AnswerKind.ARRANGE, "p", "int main ( ) {", null));
        assertEquals("if (x > 5)", CodeChunks.of(AnswerKind.ARRANGE, "p", "if ( x > 5 )", null));
        assertEquals("nums[4] = 9;", CodeChunks.of(AnswerKind.ARRANGE, "p", "nums [ 4 ] = 9 ;", null));
        assertEquals("p = &x;", CodeChunks.of(AnswerKind.ARRANGE, "p", "p = &x ;", null));
    }

    // ── the code block ──

    @Test
    void theCodeBlockIndentsByBraceDepthAndSkipsBlankLines() {
        CodeBlock block = new CodeBlock();
        block.append(new CodeBlock.Token("int main() {", true));
        block.append(new CodeBlock.Token("\nif (b != 0) {\nprintf(\"q\");\n} else {\nprintf(\"z\");\n}", false));
        block.append(new CodeBlock.Token("return 0;\n}", true));
        List<String> text = block.lines().stream().map(CodeBlock.Line::text).toList();
        assertEquals(List.of("int main() {", "    if (b != 0) {", "        printf(\"q\");", "    } else {",
                "        printf(\"z\");", "    }", "    return 0;", "}"), text);
        assertTrue(block.lines().get(0).correct());
        assertFalse(block.lines().get(1).correct());
    }

    // ── coverage ──

    @Test
    void chunksInOrderRebuildTheProgram() {
        String program = "#include <stdio.h>\n\nint main() {\n    return 0;\n}\n";
        ProgramCoverage.Report r = ProgramCoverage.check(program, List.of("#include <stdio.h>", "int main() {", "return 0;\n}"));
        assertTrue(r.complete(), ProgramCoverage.describe(r));
    }

    @Test
    void spacingInsideALineDoesNotMatter() {
        assertTrue(ProgramCoverage.check("int a[2] = {1, 2};\nreturn 0;",
                List.of("int a[2] = { 1 , 2 } ;", "return 0 ;")).complete());
        assertFalse(ProgramCoverage.check("int a = 1;", List.of("int a = 2;")).complete());
    }

    @Test
    void missingLinesAreReportedAsNotCoveredYet() {
        ProgramCoverage.Report r = ProgramCoverage.check("a;\nb;\nc;", List.of("a;"));
        assertFalse(r.complete());
        assertNull(r.problem());
        assertEquals(1, r.coveredLines());
        assertEquals(3, r.programLines());
    }

    @Test
    void aChunkThatDoesNotMatchIsReportedWithItsQuestionNumber() {
        ProgramCoverage.Report r = ProgramCoverage.check("a;\nb;", List.of("a;", "x;"));
        assertNotNull(r.problem());
        assertTrue(r.problem().contains("Question 2"));
        assertEquals(1, r.coveredLines());
    }

    @Test
    void aQuestionWithoutAChunkAndExtraCodeAreProblems() {
        assertNotNull(ProgramCoverage.check("a;", java.util.Arrays.asList("a;", null)).problem());
        assertNotNull(ProgramCoverage.check("a;", List.of("a;", "b;")).problem());
        assertFalse(ProgramCoverage.check("", List.of()).complete());
    }

    // ── the provider and the run ──

    @Test
    void theProviderServesTheQuestionsInProgramOrderAndBuildsAStageFromThem() {
        BuiltInPrograms.Program p = BuiltInPrograms.forLayout(1);
        ProgramChallengeProvider provider = new ProgramChallengeProvider(p.slots());
        Stage stage = ProgramChallengeProvider.stageFor("t", "map1", provider.slots());
        assertEquals(p.slots().size(), stage.towerCount());
        assertTrue(stage.sections().size() <= 4);
        for (Challenge expected : p.slots()) {
            assertSame(expected, provider.getChallenge(CTopic.SYNTAX, expected.difficulty()).orElseThrow());
        }
        assertTrue(provider.getChallenge(CTopic.SYNTAX, Difficulty.EASY).isEmpty());
        assertTrue(provider.buildsProgram());
    }

    @Test
    void sectionsHoldTheTopicsOfTheirTowersInOrder() {
        List<Challenge> slots = new ArrayList<>();
        CTopic[] topics = { CTopic.SYNTAX, CTopic.VARIABLES, CTopic.ARRAYS, CTopic.POINTERS, CTopic.FUNCTIONS, CTopic.SYNTAX };
        for (int i = 0; i < topics.length; i++) {
            slots.add(new Challenge("c" + i, topics[i], Difficulty.EASY, "x ___", "y", List.of("z"), null));
        }
        Stage stage = ProgramChallengeProvider.stageFor("t", "map1", slots);
        assertEquals(4, stage.sections().size());
        List<CTopic> flat = new ArrayList<>();
        stage.sections().forEach(s -> flat.addAll(s.towerTopics()));
        assertEquals(List.of(topics), flat);
    }

    @Test
    void aProgramLessonNeverHasMoreTowersThanTheMapsHold() {
        List<Challenge> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(new Challenge("c" + i, CTopic.SYNTAX, Difficulty.EASY, "x ___", "y", List.of("z"), null));
        }
        assertEquals(ProgramChallengeProvider.MAX_SLOTS, new ProgramChallengeProvider(many).slots().size());
        assertEquals(ProgramChallengeProvider.MAX_SLOTS,
                ProgramChallengeProvider.stageFor("t", "m", many).towerCount());
    }

    @Test
    void answeringBuildsTheProgramGreenWhenRightAndRedWhenWrong() {
        BuiltInPrograms.Program p = BuiltInPrograms.forLayout(1);
        ProgramChallengeProvider provider = new ProgramChallengeProvider(p.slots());
        Stage stage = ProgramChallengeProvider.stageFor("t", "map1", provider.slots());
        StageRun run = new StageRun(stage, Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, provider, false);
        assertTrue(run.buildsProgram());
        int i = 0;
        while (run.nextChallenge().isPresent()) {
            Challenge c = run.nextChallenge().orElseThrow();
            boolean right = i % 3 != 2; // every third answer is wrong
            run.answer(right ? c.correctToken() : "definitely wrong", 0f);
            i++;
        }
        List<CodeBlock.Line> lines = run.codeBlock().lines();
        // right answers add the program's lines; wrong ones put the student's mistake into the code, in red
        assertTrue(lines.stream().anyMatch(CodeBlock.Line::correct));
        assertTrue(lines.stream().anyMatch(l -> !l.correct()));
        assertTrue(lines.stream().anyMatch(l -> l.text().contains("definitely wrong")), "the wrong answer is in the code");
        assertNotEquals(ProgramCoverage.codeLines(p.text()), lines.stream().map(l -> l.text().trim()).toList());
    }

    @Test
    void aMissedFindTheBugOnlyMarksTheLineThatHeldTheBug() {
        Challenge bug = new Challenge("b", CTopic.SYNTAX, Difficulty.EASY, "ok line;\nbad line", "2", List.of(), null,
                AnswerKind.FIND_BUG, List.of(), "ok line;\nbad line;");
        for (String answer : new String[] { "1", null }) {
            ProgramChallengeProvider provider = new ProgramChallengeProvider(List.of(bug));
            StageRun run = new StageRun(ProgramChallengeProvider.stageFor("t", "m", provider.slots()), Difficulty.NORMAL,
                    StageRun.Mode.CAMPAIGN, provider, false);
            run.nextChallenge();
            run.answer(answer, 0f);
            List<CodeBlock.Line> lines = run.codeBlock().lines();
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).correct(), "the line without the bug is never red");
            assertFalse(lines.get(1).correct(), "the line with the bug is red when it was missed");
        }
        // found it: both lines green
        ProgramChallengeProvider provider = new ProgramChallengeProvider(List.of(bug));
        StageRun run = new StageRun(ProgramChallengeProvider.stageFor("t", "m", provider.slots()), Difficulty.NORMAL,
                StageRun.Mode.CAMPAIGN, provider, false);
        run.nextChallenge();
        run.answer("2", 0f);
        assertTrue(run.codeBlock().lines().stream().allMatch(CodeBlock.Line::correct));
    }

    @Test
    void aRunThatAnswersOutOfTimeStillAddsItsChunkInRed() {
        BuiltInPrograms.Program p = BuiltInPrograms.forLayout(2);
        ProgramChallengeProvider provider = new ProgramChallengeProvider(p.slots());
        StageRun run = new StageRun(ProgramChallengeProvider.stageFor("t", "m", provider.slots()), Difficulty.NORMAL,
                StageRun.Mode.CAMPAIGN, provider, false);
        run.nextChallenge();
        run.answer(null, 0f);
        assertFalse(run.codeBlock().lines().get(0).correct());
    }

    @Test
    void aPlainLessonStillAddsSingleTokens() {
        ChallengeProvider plain = (topic, difficulty) -> Optional.of(
                new Challenge("c", topic, difficulty, "x ___", ";", List.of(":"), null));
        Stage stage = new Stage("t", "m", List.of(new Section(1, List.of(CTopic.SYNTAX))));
        StageRun run = new StageRun(stage, Difficulty.NORMAL, StageRun.Mode.CAMPAIGN, plain, false);
        run.nextChallenge();
        run.answer(";", 0f);
        assertFalse(run.buildsProgram());
        assertEquals(";", run.codeBlock().tokens().get(0).text());
    }

    // ── the professor's rules for a program lesson ──

    private static QuestionDraft draft(AnswerKind kind, String prompt, String correct, String chunk) {
        return new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, prompt, correct, kind == AnswerKind.CHOICE ? List.of("z") : List.of(),
                null, kind, List.of(), chunk, "what the code is for", "why it is right");
    }

    @Test
    void inAProgramLessonTrueFalseAndFindTheBugMustSayWhatCodeTheyAdd() {
        assertTrue(draft(AnswerKind.TRUE_FALSE, "s", "True", null).problem(true).isPresent());
        assertTrue(draft(AnswerKind.TRUE_FALSE, "s", "True", "int x;").problem(true).isEmpty());
        assertTrue(draft(AnswerKind.FIND_BUG, "a\nb", "1", "").problem(true).isPresent());
        assertTrue(draft(AnswerKind.FIND_BUG, "a\nb", "1", "a;\nb;").problem(true).isEmpty());
        // outside a program lesson no chunk is asked for
        assertTrue(draft(AnswerKind.TRUE_FALSE, "s", "True", null).problem().isEmpty());
    }

    @Test
    void inAProgramLessonAChoiceQuestionNeedsItsBlank() {
        assertTrue(draft(AnswerKind.CHOICE, "no blank", "x", null).problem(true).isPresent());
        assertTrue(draft(AnswerKind.CHOICE, "int ___;", "x", null).problem(true).isEmpty());
    }

    @Test
    void aBlankNeedsAContextLineSoItHasOnlyOneRightAnswer() {
        QuestionDraft noContext = new QuestionDraft(CTopic.CONTROL_STRUCTURES, Difficulty.EASY, "if (age ___ 18) {", ">=",
                List.of("=>"), null, AnswerKind.CHOICE, List.of(), null, null, "why");
        assertTrue(noContext.problem(true).orElse("").contains("context"));
        assertTrue(noContext.problem().isEmpty()); // outside a program lesson it is optional
        QuestionDraft withContext = new QuestionDraft(CTopic.CONTROL_STRUCTURES, Difficulty.EASY, "if (age ___ 18) {", ">=",
                List.of("=>"), null, AnswerKind.CHOICE, List.of(), null, "Run the next lines only when the person is an adult.", "why");
        assertTrue(withContext.problem(true).isEmpty());
        // true/false, arrange and find-the-bug do not need one
        assertTrue(new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "s", "True", List.of(), null, AnswerKind.TRUE_FALSE,
                List.of(), "int x;", null, "why").problem(true).isEmpty());
        QuestionDraft tooLong = new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "int ___;", "x", List.of("z"), null,
                AnswerKind.CHOICE, List.of(), null, "c".repeat(QuestionDraft.MAX_CONTEXT + 1));
        assertTrue(tooLong.problem().isPresent());
        assertNull(new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "int ___;", "x", List.of("z"), null, AnswerKind.CHOICE,
                List.of(), null, "   ").cleaned().context());
    }

    @Test
    void aProgramLessonQuestionMustExplainItsAnswer() {
        QuestionDraft none = new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "int ___;", "x", List.of("z"), null,
                AnswerKind.CHOICE, List.of(), null, "make a variable", null);
        assertTrue(none.problem(true).orElse("").contains("Explain"));
        assertTrue(none.problem().isEmpty()); // optional outside a program lesson
        QuestionDraft tooLong = new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, "int ___;", "x", List.of("z"), null,
                AnswerKind.CHOICE, List.of(), null, "make a variable", "e".repeat(QuestionDraft.MAX_EXPLANATION + 1));
        assertTrue(tooLong.problem().isPresent());
    }

    @Test
    void whatIsShownAfterAnAnswerIsTheExplanationElseTheHintElseNothing() {
        Challenge withBoth = new Challenge("a", CTopic.SYNTAX, Difficulty.EASY, "p", "x", List.of("y"), "the hint")
                .withExplanation("  because reasons ");
        assertEquals("because reasons", withBoth.explanationText());
        assertEquals("the hint", new Challenge("b", CTopic.SYNTAX, Difficulty.EASY, "p", "x", List.of("y"), "the hint").explanationText());
        assertEquals("", new Challenge("c", CTopic.SYNTAX, Difficulty.EASY, "p", "x", List.of("y"), null).explanationText());
        // the explanation survives re-asking the question at another difficulty
        assertEquals("because reasons", withBoth.withContext("goal").forDifficulty(Difficulty.HARD).explanationText());
        assertEquals("because reasons", withBoth.withSlot(3).explanationText());
    }

    @Test
    void everyBuiltInProgramQuestionExplainsItsAnswer() {
        for (int layout = 1; layout <= DefaultStages.LAYOUTS; layout++) {
            for (Challenge c : BuiltInPrograms.forLayout(layout).slots()) {
                assertNotNull(c.explanation(), c.id() + " has no explanation of its own");
                assertTrue(c.explanation().length() >= 40 && c.explanation().length() <= QuestionDraft.MAX_EXPLANATION,
                        c.id() + " explanation length " + c.explanation().length());
            }
        }
    }

    @Test
    void otherKindsIgnoreAWrittenChunk() {
        assertNull(draft(AnswerKind.CHOICE, "int ___;", "x", "something else").cleaned().chunk());
        assertEquals("int x;", draft(AnswerKind.CHOICE, "int ___;", "x", "something else").codeChunk());
    }

    // ── the built-in programs ──

    @Test
    void everyBuiltInProgramIsValidAndItsQuestionsRebuildItExactly() {
        for (int layout = 1; layout <= DefaultStages.LAYOUTS; layout++) {
            BuiltInPrograms.Program p = BuiltInPrograms.forLayout(layout);
            assertFalse(p.slots().isEmpty());
            assertTrue(p.slots().size() <= ProgramChallengeProvider.MAX_SLOTS, "layout " + layout);
            List<String> chunks = new ArrayList<>();
            AnswerKind previous = null;
            for (Challenge c : p.slots()) {
                QuestionDraft d = new QuestionDraft(c.topic(), c.difficulty(), c.prompt(), c.correctToken(), c.distractors(),
                        c.hint(), c.kind(), c.accepted(), c.chunk(), c.context(), c.explanation());
                assertTrue(d.problem(true).isEmpty(), c.id() + ": " + d.problem(true).orElse(""));
                assertTrue(c.isCorrect(c.correctToken()), c.id());
                assertNotEquals(previous, c.kind(), c.id() + " repeats the kind of the question before it");
                previous = c.kind();
                chunks.add(c.codeChunk());
            }
            ProgramCoverage.Report r = ProgramCoverage.check(p.text(), chunks);
            assertTrue(r.complete(), "layout " + layout + ": " + ProgramCoverage.describe(r));
        }
    }

    @Test
    void theBuiltInProgramsUseSeveralKinds() {
        for (int layout = 1; layout <= DefaultStages.LAYOUTS; layout++) {
            long kinds = BuiltInPrograms.forLayout(layout).slots().stream().map(Challenge::kind).distinct().count();
            assertTrue(kinds >= 4, "layout " + layout + " has only " + kinds + " kinds");
        }
    }
}
