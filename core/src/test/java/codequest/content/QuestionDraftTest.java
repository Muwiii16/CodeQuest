package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionDraftTest {

    private static QuestionDraft q(String prompt, String correct, List<String> wrong, String hint) {
        return new QuestionDraft(CTopic.SYNTAX, Difficulty.EASY, prompt, correct, wrong, hint);
    }

    @Test
    void aCompleteQuestionHasNoProblem() {
        assertTrue(q("printf(\"Hi\")___", ";", List.of(":", ","), "end with a semicolon").problem().isEmpty());
    }

    @Test
    void theQuestionTextIsRequiredAndLimited() {
        assertTrue(q("   ", ";", List.of(":"), null).problem().isPresent());
        assertTrue(q("x".repeat(QuestionDraft.MAX_PROMPT + 1), ";", List.of(":"), null).problem().isPresent());
    }

    @Test
    void theCorrectAnswerIsRequiredAndLimited() {
        assertTrue(q("p", " ", List.of(":"), null).problem().isPresent());
        assertTrue(q("p", "y".repeat(QuestionDraft.MAX_TOKEN + 1), List.of(":"), null).problem().isPresent());
    }

    @Test
    void atLeastOneWrongAnswerIsNeededAndNoMoreThanThree() {
        assertTrue(q("p", ";", List.of(), null).problem().isPresent());
        assertTrue(q("p", ";", List.of(" ", ""), null).problem().isPresent());
        assertTrue(q("p", ";", List.of("a", "b", "c", "d"), null).problem().isPresent());
        assertTrue(q("p", ";", List.of("a", "b", "c"), null).problem().isEmpty());
    }

    @Test
    void aWrongAnswerCannotEqualTheCorrectOne() {
        assertTrue(q("p", ";", List.of(":", " ; "), null).problem().isPresent());
    }

    @Test
    void wrongAnswersAreTrimmedAndRepeatsDropped() {
        assertEquals(List.of("a", "b"), QuestionDraft.cleanDistractors(List.of(" a ", "", "b", "a", "  ")));
        assertEquals(List.of("a", "b"), q("p", ";", List.of(" a", "b ", "a"), null).cleaned().distractors());
    }

    @Test
    void aBlankHintBecomesNoHintAndALongOneIsRejected() {
        assertNull(q("p", ";", List.of(":"), "   ").cleaned().hint());
        assertTrue(q("p", ";", List.of(":"), "h".repeat(QuestionDraft.MAX_HINT + 1)).problem().isPresent());
    }
}
