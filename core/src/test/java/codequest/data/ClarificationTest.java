package codequest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codequest.content.CTopic;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClarificationTest {

    @Test
    void aClearRequestHasNoQuestions() {
        var c = LessonGenerator.parseClarification(
                "{\"clear\":true,\"summary\":\"printf only\",\"topics\":[\"SYNTAX\"],\"questions\":[],\"ai\":\"claude\"}");
        assertTrue(c.ok());
        assertTrue(c.clear());
        assertEquals(Set.of(CTopic.SYNTAX), c.topics());
        assertEquals("printf only", c.summary());
    }

    @Test
    void anUnclearRequestKeepsItsQuestions() {
        var c = LessonGenerator.parseClarification("{\"clear\":false,\"summary\":\"loops\",\"topics\":[\"CONTROL_STRUCTURES\"],"
                + "\"questions\":[\"for, while or both?\",\"  \",\"With scanf?\"]}");
        assertFalse(c.clear());
        assertEquals(List.of("for, while or both?", "With scanf?"), c.questions());
    }

    @Test
    void theGameNeverTreatsAnAnswerWithQuestionsAsClear() {
        // the AI said clear but still listed a question: the professor is asked anyway (never assume)
        var c = LessonGenerator.parseClarification("{\"clear\":true,\"summary\":\"x\",\"topics\":[],\"questions\":[\"Which?\"]}");
        assertFalse(c.clear());
    }

    @Test
    void unknownTopicsAreIgnoredAndErrorsAreReported() {
        assertEquals(Set.of(CTopic.VARIABLES), LessonGenerator.parseClarification(
                "{\"clear\":true,\"summary\":\"\",\"topics\":[\"VARIABLES\",\"DATABASES\"],\"questions\":[]}").topics());
        assertEquals("The daily AI limit has been reached.", LessonGenerator.parseClarification(
                "{\"error\":\"The daily AI limit has been reached.\"}").error());
        assertFalse(LessonGenerator.parseClarification("not json at all").ok());
    }
}
