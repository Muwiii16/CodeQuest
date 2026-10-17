package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import codequest.session.Grades;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Locale;

/**
 * Performance and grades of every student on one lesson: their campaign result,
 * and their best class-test score if the class has taken one. The written AI
 * analysis (Gemini) is a placeholder until that step is built.
 */
public class ProfessorLessonAnalysisScreen extends ProfessorScreen {

    private static final String[] HEADERS = { "STUDENT", "STATUS", "RANK", "GRADE", "SCORE", "ATTEMPTS", "CLASS TEST",
            "LAST PLAYED" };
    private static final float[] COL_X = { 110, 430, 650, 790, 950, 1090, 1260, 1520 };

    public ProfessorLessonAnalysisScreen(Main game, String username, ClassInfo cls, Lesson lesson) {
        super(game, username, "Lesson " + lesson.number() + ": " + lesson.title(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));

        int maxScore = Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(lesson.number())).towerCount());
        List<ProfessorData.LessonRow> rows = new ProfessorData().lessonAnalysis(cls.id(), lesson.stageId());

        long done = rows.stream().filter(r -> r.progress() != null && r.progress().completed()).count();
        double avg = rows.stream().filter(r -> r.progress() != null && r.progress().completed())
                .mapToInt(r -> Grades.percent(r.progress().score(), maxScore)).average().orElse(0);

        card(60, 140, 1800, 650);
        text(cls.sectionName() + "   -   " + done + " of " + rows.size() + " completed   -   average grade "
                + (done == 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", avg)), 90, 158, 1700, 40, 1.8f, AMBER,
                Align.left);

        rect(90, 215, 1740, 50, new Color(0.2f, 0.16f, 0.06f, 1f));
        for (int i = 0; i < HEADERS.length; i++) {
            text(HEADERS[i], COL_X[i], 215, 300, 50, 1.3f, AMBER, Align.left);
        }
        if (rows.isEmpty()) {
            text("No students in this class yet.", 110, 290, 1000, 40, 1.6f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(rows.size(), 9); i++) {
            ProfessorData.LessonRow r = rows.get(i);
            Progress p = r.progress();
            float y = 275 + i * 58;
            rect(90, y, 1740, 52, ROW);
            boolean completed = p != null && p.completed();
            String[] cells = {
                    r.student().username(),
                    completed ? "Completed" : p == null ? "Not started" : "In progress",
                    completed && p.rank() != null ? p.rank() : "-",
                    completed ? Grades.percent(p.score(), maxScore) + "%" : "-",
                    p == null ? "-" : String.valueOf(p.score()),
                    p == null ? "-" : String.valueOf(p.attempts()),
                    r.classTestScore() == null ? "-" : Grades.percent(r.classTestScore(), maxScore) + "%",
                    p == null || p.lastPlayed() == null ? "-" : p.lastPlayed().substring(0, Math.min(10, p.lastPlayed().length()))
            };
            for (int c = 0; c < cells.length; c++) {
                Color color = c == 1 ? (completed ? new Color(0.55f, 0.9f, 0.65f, 1f) : MUTED)
                        : c == 2 ? AMBER : Color.WHITE;
                text(cells[c], COL_X[c], y, 300, 52, 1.4f, color, Align.left);
            }
        }
        if (rows.size() > 9) {
            text("+ " + (rows.size() - 9) + " more students not shown", 110, 775, 800, 30, 1.2f, MUTED, Align.left);
        }

        card(60, 810, 1800, 210);
        text("AI analysis (Gemini)", 90, 825, 700, 36, 1.8f, AMBER, Align.left);
        text("A written summary of where this class struggled on the lesson, built from these results, is coming in a "
                + "later version.", 90, 870, 1740, 110, 1.5f, MUTED, Align.topLeft).setWrap(true);
    }
}
