package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import codequest.data.Poller;
import codequest.session.Grades;
import codequest.session.RunSummary;
import codequest.session.SectionResult;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * After a class test: the student's own grade and per-section breakdown (when they
 * just played) next to live standings for the whole class. The AI-written
 * performance analysis is a placeholder until the Claude feedback step is built.
 */
public class ClassTestResultScreen extends StudentScreen {

    private record Standings(List<Participant> participants, String status) {
    }

    private final Session session;
    private final RunSummary summary;
    private final int maxScore;
    private final Poller<Standings> poller;
    private final int base;
    private String shown = "";

    public ClassTestResultScreen(Main game, String username, Session session, RunSummary summary) {
        super(game, username, "Class Test Results", () -> game.setScreen(new MainMenuScreen(game, username)));
        this.session = session;
        this.summary = summary;
        this.maxScore = summary != null ? summary.maxScore()
                : Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(session.lessonNumber())).towerCount());
        ClassTestData tests = new ClassTestData();
        base = mark();
        poller = new Poller<>(() -> new Standings(tests.participants(session.id()),
                tests.session(session.id()).map(Session::status).orElse("active")), 1500);
    }

    @Override
    public void render(float delta) {
        Standings st = poller.latest();
        StringBuilder sig = new StringBuilder(st == null ? "loading" : st.status());
        if (st != null) {
            st.participants().forEach(p -> sig.append(p.username()).append(p.score()).append(p.finished()));
        }
        if (!sig.toString().equals(shown)) {
            shown = sig.toString();
            rebuild(st);
        }
        super.render(delta);
    }

    private void rebuild(Standings st) {
        clearFrom(base);

        // ── your result ──
        card(60, 140, 760, 880);
        text(session.sectionName() + "  -  Lesson " + session.lessonNumber(), 90, 155, 700, 40, 1.8f, TEAL, Align.left);
        if (summary != null) {
            text(summary.gradePercent() + "%", 90, 210, 700, 120, 7f, Color.WHITE, Align.center);
            text("Grade   -   Rank " + summary.rank(), 90, 345, 700, 40, 1.9f, GOLD, Align.center);
            text("Accuracy " + Math.round(summary.accuracy() * 100) + "%      Score " + summary.score() + " / "
                    + summary.maxScore() + "      Hints " + summary.hintsUsed(), 90, 400, 700, 36, 1.4f, MUTED, Align.center);
            float y = 460;
            for (SectionResult r : summary.sections()) {
                text("Section " + r.sectionIndex() + ":   " + r.correct() + " / " + r.total() + "   ("
                        + Math.round(r.accuracy() * 100) + "%)", 130, y, 640, 34, 1.5f, Color.WHITE, Align.left);
                y += 40;
            }
            if (summary.failed()) {
                text("You ran out of health before the end.", 90, y + 10, 700, 34, 1.4f,
                        new Color(1f, 0.55f, 0.5f, 1f), Align.left);
            }
        } else {
            text("You finished this test.", 90, 230, 700, 40, 2f, Color.WHITE, Align.center);
        }
        card(90, 760, 700, 220);
        text("Performance analysis (AI)", 110, 775, 660, 36, 1.7f, GOLD, Align.left);
        text("Detailed written feedback on your answers is coming in a later version.", 110, 820, 660, 120, 1.4f, MUTED,
                Align.topLeft).setWrap(true);

        // ── standings ──
        card(860, 140, 1000, 880);
        text("Class standings", 890, 155, 600, 40, 2f, TEAL, Align.left);
        if (st == null) {
            text("Loading...", 890, 220, 900, 40, 1.6f, MUTED, Align.left);
            return;
        }
        text("completed".equals(st.status()) ? "The professor has ended the test." : "Updating live as classmates finish.",
                890, 200, 940, 34, 1.4f, MUTED, Align.left);
        Optional<Participant> mine = st.participants().stream().filter(p -> p.username().equals(username)).findFirst();
        float y = 255;
        for (int i = 0; i < Math.min(st.participants().size(), 11); i++) {
            Participant p = st.participants().get(i);
            boolean you = mine.isPresent() && mine.get().studentId() == p.studentId();
            rect(890, y, 940, 58, you ? new Color(0.1f, 0.3f, 0.34f, 1f) : ROW);
            text("#" + (i + 1), 905, y, 80, 58, 1.7f, i == 0 ? GOLD : Color.WHITE, Align.left);
            text(p.username() + (you ? "  (you)" : ""), 990, y, 420, 58, 1.7f, Color.WHITE, Align.left);
            text(p.finished() ? p.score() + " pts   " + Grades.percent(p.score(), maxScore) + "%" : "playing...", 1400, y,
                    410, 58, 1.6f, p.finished() ? new Color(0.6f, 0.9f, 0.7f, 1f) : MUTED, Align.right);
            y += 66;
        }
        if (st.participants().isEmpty()) {
            text("Nobody has joined yet.", 890, 255, 900, 40, 1.6f, MUTED, Align.left);
        }
        text(String.format(Locale.ROOT, "%d finished of %d", st.participants().stream().filter(Participant::finished).count(),
                st.participants().size()), 890, 975, 940, 30, 1.3f, MUTED, Align.right);
    }

    @Override
    public void dispose() {
        poller.close();
        super.dispose();
    }
}
