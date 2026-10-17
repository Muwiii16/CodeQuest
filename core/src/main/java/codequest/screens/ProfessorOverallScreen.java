package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import codequest.session.Grades;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Map;

/**
 * Overall class performance: every student's result on every lesson, from lesson 1
 * to the latest, as a grid. The written AI analysis is a placeholder for now.
 */
public class ProfessorOverallScreen extends ProfessorScreen {

    private static final int LESSONS_PER_PAGE = 7;

    private final ClassInfo cls;
    private final List<Lesson> lessons;
    private final List<ProfessorData.Student> students;
    private final Map<Long, Map<Long, Progress>> progress;
    private final int base;
    private int page;

    public ProfessorOverallScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Overall performance  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        ProfessorData data = new ProfessorData();
        lessons = new CampaignData().lessons(cls.id());
        students = data.roster(cls.id());
        progress = data.overallProgress(students, lessons);

        card(60, 140, 1800, 650);
        card(60, 810, 1800, 210);
        text("AI analysis (Gemini)", 90, 825, 700, 36, 1.8f, AMBER, Align.left);
        text("A written summary of how each student and the whole class progressed across the lessons is coming in a "
                + "later version.", 90, 870, 1740, 110, 1.5f, MUTED, Align.topLeft).setWrap(true);
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        if (lessons.isEmpty() || students.isEmpty()) {
            text(lessons.isEmpty() ? "This section has no lessons yet." : "This section has no students yet.", 90, 170,
                    1700, 40, 1.8f, MUTED, Align.left);
            return;
        }
        int from = page * LESSONS_PER_PAGE;
        int to = Math.min(lessons.size(), from + LESSONS_PER_PAGE);
        float nameW = 330;
        int visible = to - from;
        float colW = Math.min(190f, (1740 - nameW - 190) / Math.max(1, visible));

        text("Rank per lesson   (- = not completed)", 90, 155, 800, 36, 1.5f, MUTED, Align.left);
        if (lessons.size() > LESSONS_PER_PAGE) {
            int pages = (lessons.size() + LESSONS_PER_PAGE - 1) / LESSONS_PER_PAGE;
            if (page > 0) {
                solidButton("< Lessons", 1320, 148, 220, 48, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            if (page < pages - 1) {
                solidButton("Lessons >", 1560, 148, 220, 48, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }

        rect(90, 215, 1740, 50, new Color(0.2f, 0.16f, 0.06f, 1f));
        text("STUDENT", 110, 215, nameW, 50, 1.3f, AMBER, Align.left);
        for (int i = from; i < to; i++) {
            text("L" + lessons.get(i).number(), 90 + nameW + (i - from) * colW, 215, colW, 50, 1.3f, AMBER, Align.center);
        }
        text("AVG GRADE", 90 + nameW + visible * colW, 215, 190, 50, 1.3f, AMBER, Align.center);

        for (int r = 0; r < Math.min(students.size(), 9); r++) {
            ProfessorData.Student s = students.get(r);
            float y = 275 + r * 58;
            rect(90, y, 1740, 52, ROW);
            text(s.username(), 110, y, nameW - 20, 52, 1.5f, Color.WHITE, Align.left);
            Map<Long, Progress> mine = progress.getOrDefault(s.id(), Map.of());
            for (int i = from; i < to; i++) {
                Progress p = mine.get(lessons.get(i).stageId());
                boolean done = p != null && p.completed();
                text(done && p.rank() != null ? p.rank() : "-", 90 + nameW + (i - from) * colW, y, colW, 52, 1.6f,
                        done ? AMBER : MUTED, Align.center);
            }
            int sum = 0;
            int counted = 0;
            for (Lesson l : lessons) {
                Progress p = mine.get(l.stageId());
                if (p != null && p.completed()) {
                    int max = Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(l.number())).towerCount());
                    sum += Grades.percent(p.score(), max);
                    counted++;
                }
            }
            text(counted == 0 ? "-" : (sum / counted) + "%", 90 + nameW + visible * colW, y, 190, 52, 1.5f,
                    Color.WHITE, Align.center);
        }
        if (students.size() > 9) {
            text("+ " + (students.size() - 9) + " more students not shown", 110, 775, 800, 30, 1.2f, MUTED, Align.left);
        }
    }
}
