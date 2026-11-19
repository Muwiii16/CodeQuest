package codequest.data;

import codequest.content.CTopic;
import codequest.data.DictionaryData.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryDataTest {

    private static final List<Entry> ALL = List.of(
            new Entry("printf", CTopic.SYNTAX, "Prints text on the screen.", "printf(\"Hi\");"),
            new Entry("int", CTopic.VARIABLES, "The type for whole numbers.", "int x = 1;"),
            new Entry("pointer", CTopic.POINTERS, "Stores the address of another variable.", "int *p = &x;"));

    @Test
    void noFilterShowsEverything() {
        assertEquals(3, DictionaryData.filter(ALL, null, "").size());
        assertEquals(3, DictionaryData.filter(ALL, null, null).size());
    }

    @Test
    void aTopicNarrowsTheList() {
        List<Entry> got = DictionaryData.filter(ALL, CTopic.VARIABLES, "");
        assertEquals(1, got.size());
        assertEquals("int", got.get(0).term());
    }

    @Test
    void searchMatchesTheTermOrTheMeaningWithoutCaseOrOuterSpaces() {
        assertEquals("printf", DictionaryData.filter(ALL, null, "  PRINT ").get(0).term());
        assertEquals("pointer", DictionaryData.filter(ALL, null, "address").get(0).term());
        assertTrue(DictionaryData.filter(ALL, null, "banana").isEmpty());
    }

    @Test
    void topicAndSearchCombine() {
        assertTrue(DictionaryData.filter(ALL, CTopic.SYNTAX, "address").isEmpty());
        assertEquals(1, DictionaryData.filter(ALL, CTopic.POINTERS, "address").size());
    }
}
