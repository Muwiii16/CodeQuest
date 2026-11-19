package codequest.data;

import codequest.content.AnswerKind;
import codequest.content.GeneratedLesson;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LessonGeneratorTest {

    private static final String GOOD = """
            {"title":"Say hello","outputs":"Hi","program":"#include <stdio.h>\\n\\nint main() {\\n    printf(\\"Hi\\");\\n    return 0;\\n}",
             "questions":[
              {"topic":"SYNTAX","difficulty":"easy","kind":"fill_blank","prompt":"#include <___.h>","correct":"stdio",
               "wrong":[],"accepted":[],"chunk":"","context":"Bring in the library that gives you printf.",
               "explanation":"stdio gives you printf.","hint":"stdio is standard input and output."},
              {"topic":"SYNTAX","difficulty":"easy","kind":"true_false","prompt":"C programs start at main.","correct":"True",
               "wrong":[],"accepted":[],"chunk":"int main() {","explanation":"Programs start at main.","hint":"main first."},
              {"topic":"SYNTAX","difficulty":"easy","kind":"arrange","prompt":"Build the print line:","correct":"printf ( \\"Hi\\" ) ;",
               "wrong":["print"],"accepted":[],"chunk":"","explanation":"printf takes its input in parentheses.","hint":"printf takes its input in parentheses."},
              {"topic":"FUNCTIONS","difficulty":"easy","kind":"choice","prompt":"return ___;\\n}","correct":"0",
               "wrong":["1","void"],"accepted":[],"chunk":"","context":"End main and report success.","explanation":"main returns 0 on success.","hint":"0 means success."}
             ]}
            """;

    @Test
    void aWellFormedAnswerParsesAndPassesEveryRule() {
        GeneratedLesson lesson = LessonGenerator.parse(GOOD).orElseThrow();
        assertEquals("Say hello", lesson.title());
        assertEquals(4, lesson.questions().size());
        assertEquals(AnswerKind.TRUE_FALSE, lesson.questions().get(1).kind());
        assertEquals("int main() {", lesson.questions().get(1).chunk());
        assertTrue(lesson.problems().isEmpty(), lesson.problems().orElse(""));
    }

    @Test
    void aDraftThatDoesNotRebuildTheProgramIsRejectedWithAReason() {
        String bad = GOOD.replace("\"correct\":\"stdio\"", "\"correct\":\"stdlib\"");
        Optional<String> problem = LessonGenerator.parse(bad).orElseThrow().problems();
        assertTrue(problem.isPresent());
        assertTrue(problem.get().contains("Question 1"), problem.get());
    }

    @Test
    void aTrueFalseQuestionWithoutItsLinesIsRejected() {
        String bad = GOOD.replace("\"chunk\":\"int main() {\"", "\"chunk\":\"\"");
        assertTrue(LessonGenerator.parse(bad).orElseThrow().problems().orElse("").contains("Question 2"));
    }

    @Test
    void tooManyQuestionsAreRejected() {
        StringBuilder sb = new StringBuilder("{\"title\":\"t\",\"program\":\"a;\",\"questions\":[");
        for (int i = 0; i < 11; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"topic\":\"SYNTAX\",\"difficulty\":\"easy\",\"kind\":\"choice\","
                    + "\"prompt\":\"a ___\",\"correct\":\"x\",\"wrong\":[\"y\"],\"accepted\":[],\"chunk\":\"\",\"hint\":\"\"}");
        }
        sb.append("]}");
        assertTrue(LessonGenerator.parse(sb.toString()).orElseThrow().problems().orElse("").contains("at most"));
    }

    @Test
    void garbageAndUnknownTopicsAreNotAccepted() {
        assertTrue(LessonGenerator.parse("not json").isEmpty());
        assertTrue(LessonGenerator.parse("{\"title\":\"t\"}").isEmpty());
        assertTrue(LessonGenerator.parse(GOOD.replace("\"SYNTAX\"", "\"MAGIC\"")).isEmpty());
    }
}
