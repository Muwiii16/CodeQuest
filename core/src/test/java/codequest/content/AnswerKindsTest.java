package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AnswerKindsTest {

    private static Challenge of(AnswerKind kind, String prompt, String correct, List<String> wrong, List<String> accepted) {
        return new Challenge("t", CTopic.SYNTAX, Difficulty.EASY, prompt, correct, wrong, null, kind, accepted);
    }

    // ── matching ──

    @Test
    void choiceNeedsTheExactToken() {
        Challenge c = of(AnswerKind.CHOICE, "p", ";", List.of(":"), List.of());
        assertTrue(c.isCorrect(";"));
        assertFalse(c.isCorrect(":"));
        assertFalse(c.isCorrect(null));
    }

    @Test
    void aTypedAnswerIgnoresSpacesButNotCase() {
        Challenge c = of(AnswerKind.FILL_BLANK, "x ___", "i++", List.of(), List.of("i+=1"));
        assertTrue(c.isCorrect("i++"));
        assertTrue(c.isCorrect("  i ++ "));
        assertTrue(c.isCorrect("i += 1"));   // another accepted spelling
        assertFalse(c.isCorrect("I++"));     // C is case sensitive
        assertFalse(c.isCorrect(""));
        assertFalse(c.isCorrect(null));
    }

    @Test
    void trueFalseIsExact() {
        Challenge c = of(AnswerKind.TRUE_FALSE, "statement", Challenge.FALSE, List.of(), List.of());
        assertTrue(c.isCorrect("False"));
        assertFalse(c.isCorrect("True"));
    }

    @Test
    void anArrangedAnswerMustBeInTheRightOrder() {
        Challenge c = of(AnswerKind.ARRANGE, "build", "int age = 18 ;", List.of("float"), List.of());
        assertTrue(c.isCorrect("int age = 18 ;"));
        assertTrue(c.isCorrect("int  age = 18 ;"));
        assertFalse(c.isCorrect("age int = 18 ;"));
        assertFalse(c.isCorrect("int age = 18"));
        assertFalse(c.isCorrect("float age = 18 ;"));
    }

    @Test
    void findTheBugIsTheLineNumber() {
        Challenge c = of(AnswerKind.FIND_BUG, "a\nb\nc", "2", List.of(), List.of());
        assertTrue(c.isCorrect("2"));
        assertFalse(c.isCorrect("3"));
        assertEquals("line 2", c.answerLabel());
        assertEquals(3, c.codeLines().size());
    }

    // ── what the student picks from ──

    @Test
    void theBankMatchesTheKind() {
        Random rng = new Random(1);
        assertEquals(4, of(AnswerKind.CHOICE, "p", ";", List.of("a", "b", "c"), List.of()).bank(rng).size());
        assertEquals(List.of("True", "False"), of(AnswerKind.TRUE_FALSE, "s", "True", List.of(), List.of()).bank(rng));
        assertEquals(List.of("1", "2", "3"), of(AnswerKind.FIND_BUG, "a\nb\nc", "2", List.of(), List.of()).bank(rng));
        assertTrue(of(AnswerKind.FILL_BLANK, "x ___", "i", List.of(), List.of()).bank(rng).isEmpty());
    }

    @Test
    void anArrangeBankHasEveryTokenAndTheDecoysAndStartsMixedUp() {
        Challenge c = of(AnswerKind.ARRANGE, "build", "printf ( \"Hi\" ) ;", List.of("print"), List.of());
        for (int seed = 0; seed < 20; seed++) {
            List<String> bank = c.bank(new Random(seed));
            assertEquals(6, bank.size());
            assertTrue(bank.containsAll(c.correctOrder()));
            assertTrue(bank.contains("print"));
            assertNotEquals(c.correctOrder(), bank);
        }
    }

    // ── kinds ──

    @Test
    void kindsRoundTripThroughTheDatabaseName() {
        for (AnswerKind k : AnswerKind.values()) {
            assertEquals(k, AnswerKind.fromDb(k.dbName()));
        }
        assertEquals(AnswerKind.CHOICE, AnswerKind.fromDb(null));
        assertEquals(AnswerKind.CHOICE, AnswerKind.fromDb("nonsense"));
    }

    @Test
    void typingAndBuildingGetMoreTimeThanTapping() {
        assertTrue(AnswerKind.FILL_BLANK.timeFactor() > AnswerKind.CHOICE.timeFactor());
        assertTrue(AnswerKind.ARRANGE.timeFactor() > AnswerKind.FILL_BLANK.timeFactor());
    }

    // ── the professor's rules for each kind ──

    private static QuestionDraft q(AnswerKind kind, String prompt, String correct, List<String> wrong, List<String> accepted) {
        return new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, prompt, correct, wrong, null, kind, accepted);
    }

    @Test
    void fillInTheBlankNeedsABlankAndAnAnswer() {
        assertTrue(q(AnswerKind.FILL_BLANK, "x ___", "i", List.of(), List.of()).problem().isEmpty());
        assertTrue(q(AnswerKind.FILL_BLANK, "no blank here", "i", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.FILL_BLANK, "x ___", " ", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.FILL_BLANK, "x ___", "i", List.of(), List.of("a", "b", "c", "d")).problem().isPresent());
    }

    @Test
    void fillInTheBlankKeepsNoWrongAnswersAndDropsTheAnswerFromTheAcceptedList() {
        QuestionDraft d = q(AnswerKind.FILL_BLANK, "x ___", "i", List.of("junk"), List.of(" i ", "j", "j")).cleaned();
        assertTrue(d.distractors().isEmpty());
        assertEquals(List.of("j"), d.accepted());
    }

    @Test
    void trueFalseAcceptsTheWordsInAnyCaseAndNothingElse() {
        assertEquals("True", q(AnswerKind.TRUE_FALSE, "s", " true ", List.of("x"), List.of()).cleaned().correct());
        assertTrue(q(AnswerKind.TRUE_FALSE, "s", "FALSE", List.of(), List.of()).problem().isEmpty());
        assertTrue(q(AnswerKind.TRUE_FALSE, "s", "maybe", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.TRUE_FALSE, " ", "True", List.of(), List.of()).problem().isPresent());
    }

    @Test
    void arrangeNeedsTwoToTwelveTokensAndDecoysThatAreNotRealTokens() {
        assertTrue(q(AnswerKind.ARRANGE, "b", "int  a = 1 ;", List.of("float"), List.of()).problem().isEmpty());
        assertEquals("int a = 1 ;", q(AnswerKind.ARRANGE, "b", "int  a = 1 ;", List.of(), List.of()).cleaned().correct());
        assertTrue(q(AnswerKind.ARRANGE, "b", "int", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.ARRANGE, "b", "a b c d e f g h i j k l m", List.of(), List.of()).problem().isPresent());
        // a decoy that is a real token, or has a space in it, is dropped (decoys are optional)
        QuestionDraft withBadDecoys = q(AnswerKind.ARRANGE, "b", "int a ;", List.of("a", "two words", "float"), List.of());
        assertTrue(withBadDecoys.problem().isEmpty());
        assertEquals(List.of("float"), withBadDecoys.cleaned().distractors());
    }

    @Test
    void findTheBugNeedsAnExistingLineNumber() {
        String code = "int a;\nint b\nint c;";
        assertTrue(q(AnswerKind.FIND_BUG, code, "2", List.of(), List.of()).problem().isEmpty());
        assertTrue(q(AnswerKind.FIND_BUG, code, "4", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.FIND_BUG, code, "two", List.of(), List.of()).problem().isPresent());
        assertTrue(q(AnswerKind.FIND_BUG, "one line", "1", List.of(), List.of()).problem().isPresent());
    }

    // ── the built-in questions ──

    @Test
    void everyBuiltInQuestionIsValidAndAnswersItself() {
        List<Challenge> all = new StaticChallengeProvider().all();
        assertFalse(all.isEmpty());
        for (AnswerKind k : AnswerKind.values()) {
            assertTrue(all.stream().anyMatch(c -> c.kind() == k), "no built-in question of kind " + k);
        }
        for (Challenge c : all) {
            QuestionDraft d = new QuestionDraft(c.topic(), c.difficulty(), c.prompt(), c.correctToken(), c.distractors(),
                    c.hint(), c.kind(), c.accepted());
            assertTrue(d.problem().isEmpty(), c.id() + ": " + d.problem().orElse(""));
            assertTrue(c.isCorrect(c.correctToken()), c.id() + " does not accept its own answer");
            assertFalse(c.isCorrect(null));
            if (c.kind() == AnswerKind.ARRANGE) {
                assertNotEquals(c.correctOrder(), c.bank(new Random(3)), c.id());
            }
        }
    }
}
