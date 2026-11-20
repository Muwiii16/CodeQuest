package codequest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JoinCodeTest {

    @Test
    void whatIsTypedIsReducedToCapitalLettersAndDigits() {
        assertEquals("WWURJC", JoinCode.normalize(" ww-urjc "));
        assertEquals("ABCD12", JoinCode.normalize("ab-c d12"));
        assertEquals("", JoinCode.normalize(null));
        assertEquals("", JoinCode.normalize("--  "));
    }

    @Test
    void aCodeIsCompleteWithSixCharacters() {
        assertTrue(JoinCode.isComplete("wwu-rjc"));
        assertFalse(JoinCode.isComplete("wwu-rj"));
        assertFalse(JoinCode.isComplete("wwu-rjcx"));
        assertFalse(JoinCode.isComplete(""));
    }

    @Test
    void aCodeIsShownInTwoGroupsOfThree() {
        assertEquals("WWU-RJC", JoinCode.display("WWURJC"));
        assertEquals("WWU-RJC", JoinCode.display("wwu rjc"));
        assertEquals("ABC", JoinCode.display("abc")); // not a whole code: shown as it is
    }

    @Test
    void onlyLettersDigitsDashesAndSpacesCanBeTyped() {
        assertTrue(JoinCode.typable('a') && JoinCode.typable('7') && JoinCode.typable('-') && JoinCode.typable(' '));
        assertFalse(JoinCode.typable('!') || JoinCode.typable('/') || JoinCode.typable('\n'));
    }
}
