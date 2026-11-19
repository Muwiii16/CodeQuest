package codequest.data;

import codequest.data.CampaignData.ClassInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubjectsTest {

    private static ClassInfo cls(long id, boolean ended) {
        return new ClassInfo(id, "CC10" + id, "A", "C", 0, 0, null, "term", ended);
    }

    @Test
    void aStudentLandsOnTheNewestSubjectThatIsStillRunning() {
        // oldest enrollment first: CC101 ended last semester, CC102 is running now
        List<ClassInfo> mine = List.of(cls(1, true), cls(2, false));
        assertEquals(1, CampaignData.defaultClassIndex(mine, null));
        // two running subjects at once: the newer enrollment first
        assertEquals(2, CampaignData.defaultClassIndex(List.of(cls(1, true), cls(2, false), cls(3, false)), null));
    }

    @Test
    void whenEverythingHasEndedTheNewestEndedSubjectOpens() {
        assertEquals(1, CampaignData.defaultClassIndex(List.of(cls(1, true), cls(2, true)), null));
        assertEquals(0, CampaignData.defaultClassIndex(List.of(), null));
    }

    @Test
    void aChosenSubjectIsAlwaysOpenedEvenWhenItHasEnded() {
        List<ClassInfo> mine = List.of(cls(1, true), cls(2, false));
        assertEquals(0, CampaignData.defaultClassIndex(mine, 1L));
        assertEquals(1, CampaignData.defaultClassIndex(mine, 99L)); // unknown id: fall back to the default
    }

    @Test
    void theSubjectNameFallsBackToTheCourseCode() {
        assertEquals("CC101", cls(1, false).subjectName());
        ClassInfo named = new ClassInfo(1, "CC101", "A", "C", 0, 0, "Programming 1", "1st Sem 2026-2027", false);
        assertEquals("Programming 1", named.subjectName());
        assertEquals("1st Sem 2026-2027", named.termLabel());
        assertEquals("", new ClassInfo(1, "CC101", "A", "C", 0, 0).termLabel());
    }

    @Test
    void aDateFallsInTheRightTerm() {
        assertEquals("1st Sem 2026-2027", Terms.current(LocalDate.of(2026, 8, 17)));
        assertEquals("1st Sem 2026-2027", Terms.current(LocalDate.of(2026, 11, 18)));
        assertEquals("1st Sem 2026-2027", Terms.current(LocalDate.of(2026, 12, 31)));
        assertEquals("2nd Sem 2026-2027", Terms.current(LocalDate.of(2027, 1, 12)));
        assertEquals("2nd Sem 2026-2027", Terms.current(LocalDate.of(2027, 5, 31)));
        assertEquals("Summer 2027", Terms.current(LocalDate.of(2027, 6, 10)));
    }
}
