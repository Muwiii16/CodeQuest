package codequest.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The sign-in and sign-up functions answer with bare JSON values; make sure the parser reads them. */
class JsonReplyTest {

    private static final JsonReader READER = new JsonReader();

    @Test
    void aBareStringReplyIsReadAsText() {
        JsonValue v = READER.parse("\"bad_invite\"");
        assertEquals("bad_invite", v.asString());
    }

    @Test
    void aNullReplyIsReadAsNull() {
        JsonValue v = READER.parse("null");
        assertTrue(v == null || v.isNull());
    }

    @Test
    void anAccountReplyIsReadAsAnObject() {
        JsonValue v = READER.parse("{\"id\": 23, \"role\": \"student\"}");
        assertTrue(v.isObject());
        assertEquals(23L, v.getLong("id"));
        assertEquals("student", v.getString("role", ""));
    }
}
