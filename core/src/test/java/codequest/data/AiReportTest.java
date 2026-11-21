package codequest.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReportTest {

    private static final String JSON = "{\"summary\":\"Little data yet.\",\"tiles\":[{\"label\":\"Students\",\"value\":\"3\"}],"
            + "\"attention\":[{\"who\":\"S1\",\"finding\":\"Has not started.\",\"next\":\"Message S1.\"}],\"watch\":[],"
            + "\"doingWell\":[{\"who\":\"S2\",\"finding\":\"100% first try.\",\"next\":\"\"}],"
            + "\"struggles\":[\"Syntax 69%\"],\"nextSteps\":[\"a\",\"b\",\"c\",\"d\"],\"dataGaps\":[\"one lesson only\"]}";

    @Test
    void readsEveryPartAndCapsTheLists() {
        AiReport r = AiReport.parse(JSON);
        assertEquals("Little data yet.", r.summary());
        assertEquals("3", r.tiles().get(0).value());
        assertEquals("S1", r.attention().get(0).who());
        assertTrue(r.watch().isEmpty());
        assertEquals(3, r.nextSteps().size());
    }

    @Test
    void putsNamesBackInEveryString() {
        List<String> names = List.of("rayle", "muwiii");
        AiReport r = AiReport.parse(JSON).map(t -> AiFeedback.restoreNames(t, names));
        assertEquals("rayle", r.attention().get(0).who());
        assertEquals("Message rayle.", r.attention().get(0).next());
        assertEquals("muwiii", r.doingWell().get(0).who());
    }

    @Test
    void aJsonAnswerSurvivesANameWithAQuote() {
        String restored = AiFeedback.restoreNames("{\"summary\":\"S1 is ok\"}", List.of("a\"b"), true);
        assertEquals("a\"b is ok", AiReport.parse(restored).summary());
    }

    @Test
    void plainTextOrJunkIsNotAReport() {
        assertNull(AiReport.parse("Class: all fine."));
        assertNull(AiReport.parse("{\"summary\":\"\"}"));
        assertNull(AiReport.parse("{not json}"));
    }

    @Test
    void longStringsAreCut() {
        String longText = "x".repeat(400);
        AiReport r = AiReport.parse("{\"summary\":\"" + longText + "\"}");
        assertTrue(r.summary().length() <= 170);
    }
}
