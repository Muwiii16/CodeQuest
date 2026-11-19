package codequest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiFeedbackTest {

    @Test
    void labelsAreNumberedFromOne() {
        assertEquals("S1", AiFeedback.label(0));
        assertEquals("S12", AiFeedback.label(11));
    }

    @Test
    void labelsInTheAnswerBecomeNames() {
        List<String> names = List.of("ana", "ben", "cy");
        assertEquals("ana is fine but ben and cy need help.",
                AiFeedback.restoreNames("S1 is fine but S2 and S3 need help.", names));
    }

    @Test
    void aLabelIsOnlyReplacedAsAWholeNumber() {
        List<String> names = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
        assertEquals("k", AiFeedback.restoreNames("S11", names));
        assertEquals("a and k", AiFeedback.restoreNames("S1 and S11", names));
    }

    @Test
    void unknownLabelsAndOtherWordsAreLeftAlone() {
        assertEquals("S9 and SYNTAX and S1x", AiFeedback.restoreNames("S9 and SYNTAX and S1x", List.of("ana")));
    }

    @Test
    void namesWithSpecialCharactersAreInsertedAsTyped() {
        assertEquals("$1 and \\n", AiFeedback.restoreNames("S1 and S2", List.of("$1", "\\n")));
    }

    @Test
    void jsonWriterEscapesAndNests() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("q", "say \"hi\"\nnow");
        m.put("n", 3);
        m.put("ok", true);
        m.put("none", null);
        m.put("list", List.of("a", 1));
        assertEquals("{\"q\":\"say \\\"hi\\\"\\nnow\",\"n\":3,\"ok\":true,\"none\":null,\"list\":[\"a\",1]}", AiFeedback.toJson(m));
    }

    @Test
    void theStudentDataNeverHasAName() {
        // the feedback request is built from question text and answers only: nothing named "student", "username" or "name"
        String json = AiFeedback.toJson(Map.of("lesson", "If", "rank", "B", "accuracyPercent", 80, "answers", List.of(Map.of(
                "topic", "SYNTAX", "question", "int main() {", "yourAnswer", "x", "correctAnswer", "y", "correct", false))));
        assertFalse(json.toLowerCase().contains("username"));
        assertFalse(json.toLowerCase().contains("\"name\""));
    }
}
