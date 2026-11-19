package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.data.AiFeedback;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import codequest.session.Grades;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Performance and grades of every student on one lesson: their campaign result, and their best
 * class-test points if the class has taken one. Below it, which topics the class got wrong most
 * often, from the answer log, and a written AI analysis (Claude) the professor asks for.
 */
public class ProfessorLessonAnalysisScreen extends ProfessorScreen {

    private static final String[] HEADERS = { "STUDENT", "STATUS", "RANK", "GRADE", "SCORE", "ATTEMPTS", "CLASS TEST",
            "LAST PLAYED" };
    private static final float[] COL_X = { 110, 430, 650, 790, 950, 1090, 1260, 1520 };

    private record Loaded(List<ProfessorData.LessonRow> rows, Map<String, int[]> topics,
            Map<Long, Map<String, int[]>> byStudent) {
    }

    private final ClassInfo cls;
    private final Lesson lesson;
    private final int base;

    public ProfessorLessonAnalysisScreen(Main game, String username, ClassInfo cls, Lesson lesson) {
        super(game, username, lesson.label() + ": " + lesson.title(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        this.lesson = lesson;
        card(60, 140, 1800, 650);
        card(60, 810, 1800, 210);
        base = mark();
        load("Loading the results...", () -> {
            ProfessorData data = new ProfessorData();
            CompletableFuture<Map<String, int[]>> topics = CompletableFuture
                    .supplyAsync(() -> data.topicAccuracy(cls.id(), lesson.stageId()));
            List<ProfessorData.LessonRow> rows = data.lessonAnalysis(cls.id(), lesson.stageId());
            List<ProfessorData.Student> students = new ArrayList<>();
            rows.forEach(r -> students.add(r.student()));
            return new Loaded(rows, topics.join(), data.studentTopicAccuracy(students, lesson.stageId()));
        }, this::rebuild);
    }

    private void rebuild(Loaded loaded) {
        clearFrom(base);
        int maxScore = Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(lesson.number())).towerCount());
        List<ProfessorData.LessonRow> rows = loaded.rows();

        long done = rows.stream().filter(r -> r.progress() != null && r.progress().completed()).count();
        double avg = rows.stream().filter(r -> r.progress() != null && r.progress().completed())
                .mapToInt(r -> Grades.percent(r.progress().score(), maxScore)).average().orElse(0);

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
                    r.classTestScore() == null ? "-" : r.classTestScore() + " pts",
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

        // ── where students struggled (from the answer log) ──
        text("Where students struggled", 90, 825, 800, 36, 1.7f, AMBER, Align.left);
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(loaded.topics().entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded for this lesson yet.", 90, 875, 800, 30, 1.4f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, weakest.size()); i++) {
            Map.Entry<String, int[]> e = weakest.get(i);
            int[] c = e.getValue();
            text(label(e.getKey()), 90, 872 + i * 35, 300, 32, 1.4f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right   (" + c[0] + " of " + c[1] + " answers)", 380, 872 + i * 35, 520, 32,
                    1.4f, rate(c) < 0.5 ? new Color(1f, 0.6f, 0.55f, 1f) : MUTED, Align.left);
        }

        text("AI analysis", 960, 825, 800, 36, 1.7f, AMBER, Align.left);
        text("A written summary of where this class struggled and who needs attention. Only statistics are sent to the AI, "
                + "never names.", 960, 868, 860, 70, 1.2f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 960, 950, 520, 60, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", () -> analyse(loaded, maxScore)));
    }

    /** The class's numbers for the AI: statistics and topics only, with the students as S1, S2, ... (names stay here). */
    private AiFeedback.Result analyse(Loaded loaded, int maxScore) {
        List<Object> topics = new ArrayList<>();
        for (Map.Entry<String, int[]> e : loaded.topics().entrySet()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("topic", e.getKey());
            t.put("rightPercent", Math.round(rate(e.getValue()) * 100));
            t.put("answers", e.getValue()[1]);
            topics.add(t);
        }
        List<Object> students = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<ProfessorData.LessonRow> rows = loaded.rows();
        for (int i = 0; i < rows.size(); i++) {
            ProfessorData.LessonRow r = rows.get(i);
            Progress p = r.progress();
            boolean completed = p != null && p.completed();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", AiFeedback.label(i));
            s.put("status", completed ? "Completed" : p == null ? "Not started" : "In progress");
            s.put("rank", completed ? p.rank() : null);
            s.put("gradePercent", completed ? Grades.percent(p.score(), maxScore) : null);
            s.put("attempts", p == null ? 0 : p.attempts());
            s.put("classTestPoints", r.classTestScore());
            Map<String, Object> accuracy = new LinkedHashMap<>();
            Map<String, int[]> mine = loaded.byStudent().getOrDefault(r.student().id(), Map.of());
            mine.forEach((topic, c) -> accuracy.put(topic, Math.round(rate(c) * 100)));
            s.put("topicRightPercent", accuracy);
            students.add(s);
            names.add(r.student().username());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "one lesson");
        data.put("lesson", lesson.label() + ": " + lesson.title());
        data.put("topics", topics);
        data.put("students", students);
        return new AiFeedback().forClass(data, names);
    }

    private static double rate(int[] c) {
        return c[1] == 0 ? 0 : (double) c[0] / c[1];
    }

    private static String label(String topic) {
        try {
            return ProfessorLessonScreen.CosmeticTopic.label(codequest.content.CTopic.valueOf(topic));
        } catch (IllegalArgumentException e) {
            return topic;
        }
    }
}
