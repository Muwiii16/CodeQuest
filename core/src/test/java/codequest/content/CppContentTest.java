package codequest.content;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The C++ side of the game (the pilot language): built-in programs, starter questions, language keys. */
class CppContentTest {

    private static final List<String> C_ONLY = List.of("printf", "scanf", "stdio", "%d", "%lf", "%.2f", "&a", "&b");

    @Test
    void languagesAreKnownByTheirStoredKeyAndAnythingUnknownIsC() {
        assertEquals(Language.CPP, Language.fromKey("C++"));
        assertEquals(Language.CPP, Language.fromKey(" c++ "));
        assertEquals(Language.C, Language.fromKey("C"));
        assertEquals(Language.C, Language.fromKey(null));
        assertEquals(Language.C, Language.fromKey("Pascal"));
        assertEquals(Language.CPP, Language.PILOT);
        assertEquals("C++", Language.CPP.key());
    }

    @Test
    void everyCppStarterQuestionIsValidAndAnswersItself() {
        List<Challenge> all = new StaticChallengeProvider(Language.CPP).all();
        assertFalse(all.isEmpty());
        for (AnswerKind k : AnswerKind.values()) {
            assertTrue(all.stream().anyMatch(c -> c.kind() == k), "no C++ question of kind " + k);
        }
        for (CTopic t : CTopic.values()) {
            assertTrue(all.stream().anyMatch(c -> c.topic() == t), "no C++ question of topic " + t);
        }
        Set<String> ids = new HashSet<>();
        for (Challenge c : all) {
            assertTrue(ids.add(c.id()), "duplicate id " + c.id());
            assertTrue(c.id().startsWith("cpp-"), c.id());
            QuestionDraft d = new QuestionDraft(c.topic(), c.difficulty(), c.prompt(), c.correctToken(), c.distractors(),
                    c.hint(), c.kind(), c.accepted());
            assertTrue(d.problem().isEmpty(), c.id() + ": " + d.problem().orElse(""));
            assertTrue(c.isCorrect(c.correctToken()), c.id() + " does not accept its own answer");
            if (c.kind() == AnswerKind.ARRANGE) {
                assertNotEquals(c.correctOrder(), c.bank(new Random(3)), c.id());
            }
        }
    }

    @Test
    void theCppPoolDoesNotTeachCSyntax() {
        for (Challenge c : new StaticChallengeProvider(Language.CPP).all()) {
            for (String cOnly : C_ONLY) {
                assertFalse(c.prompt().contains(cOnly), c.id() + " still uses " + cOnly);
            }
        }
    }

    @Test
    void theCAndCppPoolsKeepTheirOwnIdsSoTheAnswerLogTellsThemApart() {
        Set<String> c = new HashSet<>();
        new StaticChallengeProvider(Language.C).all().forEach(x -> c.add(x.id()));
        for (Challenge x : new StaticChallengeProvider(Language.CPP).all()) {
            assertFalse(c.contains(x.id()), x.id());
        }
        // the default constructor is still the C pool
        assertEquals(c.size(), new StaticChallengeProvider().all().size());
    }

    @Test
    void everyCppBuiltInProgramIsCompleteAndItsQuestionsBuildIt() {
        for (int layout = 1; layout <= DefaultStages.LAYOUTS; layout++) {
            BuiltInPrograms.Program p = BuiltInPrograms.forLayout(layout, Language.CPP);
            assertTrue(p.text().contains("#include <iostream>"), "layout " + layout);
            assertTrue(p.text().contains("using namespace std;"), "layout " + layout);
            for (String cOnly : C_ONLY) {
                assertFalse(p.text().contains(cOnly), "layout " + layout + " still uses " + cOnly);
            }
            List<String> chunks = p.slots().stream().map(Challenge::codeChunk).toList();
            ProgramCoverage.Report coverage = ProgramCoverage.check(p.text(), chunks);
            assertNull(coverage.problem(), "layout " + layout + ": " + coverage.problem());
            assertTrue(coverage.complete(), "layout " + layout);
            assertTrue(p.outputs() != null && !p.outputs().isBlank(), "layout " + layout + " has no sample output");
            for (Challenge c : p.slots()) {
                assertTrue(c.id().startsWith("cpp"), c.id());
                for (Difficulty d : Difficulty.values()) {
                    Challenge asked = c.forDifficulty(d);
                    assertTrue(asked.isCorrect(asked.correctToken()), c.id() + " at " + d);
                    assertEquals(c.codeChunk(), asked.codeChunk(), c.id() + " at " + d);
                    if (asked.kind() == AnswerKind.CHOICE) {
                        assertFalse(asked.distractors().isEmpty(), c.id() + " at " + d + " has no wrong choice");
                    }
                }
            }
        }
    }

    @Test
    void theLanguageChoosesWhichBuiltInProgramIsPlayed() {
        assertTrue(BuiltInPrograms.forLayout(1, Language.C).text().contains("stdio.h"));
        assertTrue(BuiltInPrograms.forLayout(1, Language.CPP).text().contains("iostream"));
        // the old single-argument form is C, so the existing C classes keep playing what they always did
        assertEquals(BuiltInPrograms.forLayout(2).text(), BuiltInPrograms.forLayout(2, Language.C).text());
    }
}
