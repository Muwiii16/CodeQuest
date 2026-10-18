package codequest.data;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonEscapeTest {

    private static String roundTrip(String text) {
        return new JsonReader().parse("{\"a\":\"" + SupabaseClient.jsonEscape(text) + "\"}").getString("a");
    }

    @Test
    void quotesBackslashesNewlinesAndTabsSurviveTheTrip() {
        String text = "printf(\"Hi\\n\");\n\tint x = 1;\r\n";
        assertEquals(text, roundTrip(text));
    }

    @Test
    void otherControlCharactersAreEscapedToo() {
        String text = "a\u0001b\u001fc\bd\fe";
        assertEquals(text, roundTrip(text));
    }

    @Test
    void plainTextIsUnchanged() {
        assertEquals("int main(void) { return 0; }", SupabaseClient.jsonEscape("int main(void) { return 0; }"));
    }
}
