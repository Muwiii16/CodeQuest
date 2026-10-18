package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/** Finished class tests of a section, newest first; any of them can be opened to see its podium and ranking again. */
public class ProfessorTestHistoryScreen extends ProfessorScreen {

    private final ClassTestData tests = new ClassTestData();
    private final ClassInfo cls;
    private final int base;

    public ProfessorTestHistoryScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Class test history  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        card(60, 140, 1800, 880);
        base = mark();
        load("Loading past tests...", () -> tests.history(cls.id(), 9), this::rebuild);
    }

    private void rebuild(List<Session> sessions) {
        clearFrom(base);
        if (sessions.isEmpty()) {
            text("No class test has been finished for this section yet.", 90, 170, 1700, 60, 2f, MUTED, Align.center);
            return;
        }
        float y = 170;
        for (Session s : sessions) {
            rect(90, y, 1740, 84, ROW);
            rect(90, y, 8, 84, AMBER);
            text("Lesson " + s.lessonNumber() + ": " + s.lessonTitle(), 120, y + 10, 1000, 36, 1.9f, Color.WHITE, Align.left);
            String when = s.endedAt() == null ? "" : s.endedAt().substring(0, Math.min(16, s.endedAt().length())).replace('T', ' ');
            text(when.isEmpty() ? "ended" : "ended " + when, 120, y + 48, 800, 28, 1.3f, MUTED, Align.left);
            solidButton("View results", 1520, y + 14, 290, 56, BUTTON, () -> open(s));
            y += 94;
        }
    }

    private void open(Session s) {
        load("Loading the results...", () -> tests.participants(s.id()), (List<Participant> ps) -> game.setScreen(
                new ProfessorTestResultsScreen(game, username, cls, s.lessonTitle(), s.lessonNumber(), ps)));
    }
}
