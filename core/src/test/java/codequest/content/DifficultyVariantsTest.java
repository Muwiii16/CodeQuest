package codequest.content;

import codequest.data.QuestionData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyVariantsTest {

    private static Challenge choice(String id, Difficulty d, String context) {
        return new Challenge(id, CTopic.CONTROL_STRUCTURES, d, "if (age ___ 18) {", ">=", List.of("=>", "=", "=<"), null,
                AnswerKind.CHOICE, List.of(), null, context);
    }

    private static Challenge arrange(Difficulty d) {
        return new Challenge("a", CTopic.SYNTAX, d, "Build the line:", "int x = 1 ;", List.of("float", "char"), null,
                AnswerKind.ARRANGE, List.of());
    }

    // ── how a question is re-asked ──

    @Test
    void aQuestionWrittenForThatDifficultyIsUnchanged() {
        Challenge c = choice("c", Difficulty.NORMAL, "adult");
        assertSame(c, c.forDifficulty(Difficulty.NORMAL));
    }

    @Test
    void easierMeansFewerChoicesAndNoDecoys() {
        Challenge easy = choice("c", Difficulty.NORMAL, "adult").forDifficulty(Difficulty.EASY);
        assertEquals(AnswerKind.CHOICE, easy.kind());
        assertEquals(1, easy.distractors().size());
        assertEquals(2, easy.bank(new java.util.Random(1)).size());
        assertTrue(arrange(Difficulty.NORMAL).forDifficulty(Difficulty.EASY).distractors().isEmpty());
    }

    @Test
    void harderMeansTypingItOnlyWhenTheContextKeepsTheBlankUnambiguous() {
        Challenge typed = choice("c", Difficulty.NORMAL, "adult").forDifficulty(Difficulty.HARD);
        assertEquals(AnswerKind.FILL_BLANK, typed.kind());
        assertTrue(typed.isCorrect(">="));
        assertTrue(typed.isCorrect(" > = ")); // spaces do not matter
        assertFalse(typed.isCorrect(">"));
        assertTrue(typed.bank(new java.util.Random(1)).isEmpty());
        // without a context line a blank has many possible answers, so it stays a multiple choice
        assertEquals(AnswerKind.CHOICE, choice("c", Difficulty.NORMAL, null).forDifficulty(Difficulty.HARD).kind());
    }

    @Test
    void theCodeAQuestionAddsNeverChanges() {
        Challenge c = choice("c", Difficulty.NORMAL, "adult");
        for (Difficulty d : Difficulty.values()) {
            assertEquals(c.codeChunk(), c.forDifficulty(d).codeChunk());
        }
        assertEquals(arrange(Difficulty.NORMAL).codeChunk(), arrange(Difficulty.NORMAL).forDifficulty(Difficulty.EASY).codeChunk());
    }

    // ── choosing the variant ──

    @Test
    void theNearestWrittenVariantIsServedAndAnExactOneIsUsedAsWritten() {
        Challenge easy = choice("e", Difficulty.EASY, "adult").withSlot(1);
        Challenge hard = new Challenge("h", CTopic.CONTROL_STRUCTURES, Difficulty.HARD, "if (age ___ 18) {", ">=", List.of(), null,
                AnswerKind.FILL_BLANK, List.of(), null, "adult", 1);
        ProgramChallengeProvider p = new ProgramChallengeProvider(List.of(easy, hard));
        assertEquals("e", p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).orElseThrow().id());
        p = new ProgramChallengeProvider(List.of(easy, hard));
        assertEquals("h", p.getChallenge(CTopic.SYNTAX, Difficulty.HARD).orElseThrow().id());
        p = new ProgramChallengeProvider(List.of(easy, hard));
        // NORMAL is as near to easy as to hard: the easier one wins the tie
        assertEquals("e", p.getChallenge(CTopic.SYNTAX, Difficulty.NORMAL).orElseThrow().id());
    }

    @Test
    void variantsOfOneChunkAreOneTowerAndEachChunkKeepsItsPlace() {
        List<Challenge> all = new ArrayList<>();
        all.add(choice("a-e", Difficulty.EASY, "x").withSlot(1));
        all.add(choice("a-h", Difficulty.HARD, "x").withSlot(1));
        all.add(choice("b-n", Difficulty.NORMAL, "x").withSlot(2));
        all.add(arrange(Difficulty.NORMAL).withSlot(3));
        ProgramChallengeProvider p = new ProgramChallengeProvider(all);
        assertEquals(3, p.slots().size());
        assertEquals(3, ProgramChallengeProvider.stageFor("t", "m", p.slots()).towerCount());
        assertTrue(p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).orElseThrow().id().startsWith("a"));
        assertTrue(p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).orElseThrow().id().startsWith("b"));
        assertEquals("a", p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).orElseThrow().id());
        assertTrue(p.getChallenge(CTopic.SYNTAX, Difficulty.EASY).isEmpty());
    }

    @Test
    void theSameAnswersGiveTheSameQuestionsEverytime() {
        BuiltInPrograms.Program prog = BuiltInPrograms.forLayout(3);
        for (Difficulty start : Difficulty.values()) {
            List<String> first = new ArrayList<>();
            List<String> second = new ArrayList<>();
            ProgramChallengeProvider a = new ProgramChallengeProvider(prog.slots());
            ProgramChallengeProvider b = new ProgramChallengeProvider(prog.slots());
            for (int i = 0; i < prog.slots().size(); i++) {
                Challenge x = a.getChallenge(CTopic.SYNTAX, start).orElseThrow();
                Challenge y = b.getChallenge(CTopic.SYNTAX, start).orElseThrow();
                first.add(x.id() + x.kind() + x.bank(new java.util.Random(5)));
                second.add(y.id() + y.kind() + y.bank(new java.util.Random(5)));
            }
            assertEquals(first, second);
        }
    }

    @Test
    void everyBuiltInQuestionStaysAnswerableAtEveryDifficulty() {
        for (int layout = 1; layout <= DefaultStages.LAYOUTS; layout++) {
            for (Challenge c : BuiltInPrograms.forLayout(layout).slots()) {
                for (Difficulty d : Difficulty.values()) {
                    Challenge asked = c.forDifficulty(d);
                    assertTrue(asked.isCorrect(asked.correctToken()), c.id() + " at " + d);
                    assertEquals(c.codeChunk(), asked.codeChunk(), c.id() + " at " + d);
                    if (asked.kind() == AnswerKind.CHOICE) {
                        assertFalse(asked.distractors().isEmpty(), c.id() + " at " + d + " has no wrong choice");
                    }
                }
            }
        }
    }

    // ── slots in the editor and the coverage check ──

    private static QuestionData.Question q(long id, int slot, Difficulty d, String chunkLine) {
        QuestionDraft draft = new QuestionDraft(CTopic.SYNTAX, d, "s", "True", List.of(), null, AnswerKind.TRUE_FALSE,
                List.of(), chunkLine, null);
        return new QuestionData.Question(id, draft, slot);
    }

    @Test
    void questionsAreGroupedBySlotEasiestFirst() {
        List<List<QuestionData.Question>> groups = QuestionData.bySlot(List.of(
                q(1, 1, Difficulty.HARD, "a;"), q(2, 1, Difficulty.EASY, "a;"), q(3, 2, Difficulty.NORMAL, "b;"), q(4, 0, Difficulty.EASY, "c;")));
        assertEquals(3, groups.size());
        assertEquals(2L, groups.get(0).get(0).id());
        assertEquals(1L, groups.get(0).get(1).id());
    }

    @Test
    void coverageUsesOneChunkPerSlotAndFlagsLevelsThatAddDifferentCode() {
        List<QuestionData.Question> ok = List.of(q(1, 1, Difficulty.EASY, "a;"), q(2, 1, Difficulty.HARD, "a ;"), q(3, 2, Difficulty.NORMAL, "b;"));
        assertTrue(QuestionData.coverage("a;\nb;", ok).complete());
        List<QuestionData.Question> bad = List.of(q(1, 1, Difficulty.EASY, "a;"), q(2, 1, Difficulty.HARD, "z;"));
        assertTrue(QuestionData.coverage("a;", bad).problem().contains("difficulty levels"));
    }
}
