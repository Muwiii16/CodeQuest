package codequest.screens;

import codequest.Main;
import codequest.data.AiFeedback;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Session;
import codequest.data.DatabaseManager;
import codequest.data.ProfessorData;
import codequest.data.TestAnalysis;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysis of one finished class test: who took part and how they scored, each student's accuracy and answer speed
 * (click a name to open that student's analysis), the questions and topics the class got wrong most, and a written AI
 * analysis of the test the professor asks for.
 */
public class ProfessorTestAnalysisScreen extends ProfessorScreen {

    private static final String[] HEADERS = { "PLACE", "STUDENT", "SCORE", "CORRECT", "ACCURACY", "AVG TIME", "STATUS" };
    private static final float[] COL_X = { 110, 230, 640, 840, 1060, 1280, 1470 };
    private static final Color LOW = new Color(1f, 0.6f, 0.55f, 1f);
    private static final Color OK = new Color(0.55f, 0.9f, 0.65f, 1f);

    private record Loaded(TestAnalysis stats, Map<Long, String> fullNames) {
    }

    private final ClassInfo cls;
    private final Session session;
    private final Runnable back;
    private final int base;
    private Loaded loaded;

    public ProfessorTestAnalysisScreen(Main game, String username, ClassInfo cls, Session session, Runnable back) {
        super(game, username, "Class test analysis  -  " + session.label() + ": " + session.lessonTitle(), back);
        this.cls = cls;
        this.session = session;
        this.back = back;
        card(60, 140, 1800, 650);
        card(60, 810, 1800, 210);
        base = mark();
        load("Loading the test...", () -> {
            ClassTestData tests = new ClassTestData();
            List<ClassTestData.Participant> ranked = tests.participants(session.id());
            int roster = new ProfessorData().roster(cls.id()).size();
            TestAnalysis stats = TestAnalysis.of(ranked, roster, tests.answers(session.id()));
            List<Long> ids = new ArrayList<>();
            ranked.forEach(p -> ids.add(p.studentId()));
            return new Loaded(stats, DatabaseManager.fullNames(ids));
        }, l -> {
            loaded = l;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        TestAnalysis st = loaded.stats();
        String when = session.endedAt() == null ? "" : "   -   ended " + session.endedAt().substring(0, Math.min(10, session.endedAt().length()));
        if (st.joined() == 0) {
            text("Nobody took this test." + when, 90, 160, 1700, 44, 1.9f, MUTED, Align.left);
            return;
        }
        text(st.joined() + " of " + st.rosterSize() + " students took it   -   " + st.finished() + " finished"
                + (st.leftEarly() > 0 ? ", " + st.leftEarly() + " left early" : "") + when, 90, 155, 1700, 40, 1.8f, AMBER,
                Align.left);
        text("Score: average " + st.average() + " pts   -   middle " + st.median() + "   -   highest " + st.highest()
                + "   -   lowest " + st.lowest() + "      Answers: " + st.accuracyPercent() + "% right, about "
                + st.averageSeconds() + " s each", 90, 200, 1700, 34, 1.4f, MUTED, Align.left);

        rect(90, 250, 1740, 46, new Color(0.2f, 0.16f, 0.06f, 1f));
        for (int i = 0; i < HEADERS.length; i++) {
            text(HEADERS[i], COL_X[i], 250, 300, 46, 1.3f, AMBER, Align.left);
        }
        for (int i = 0; i < Math.min(st.students().size(), 9); i++) {
            TestAnalysis.StudentLine line = st.students().get(i);
            ClassTestData.Participant p = line.participant();
            float y = 303 + i * 53;
            rect(90, y, 1740, 48, ROW);
            text("#" + line.rank(), COL_X[0], y, 100, 48, 1.5f, line.rank() <= 3 ? AMBER : Color.WHITE, Align.left);
            ProfessorData.Student student = new ProfessorData.Student(p.studentId(), p.username(),
                    loaded.fullNames().get(p.studentId()));
            // the name opens that student own analysis
            solidButton(p.username(), COL_X[1] - 10, y + 2, 390, 44, ROW, Color.WHITE,
                    () -> game.setScreen(new ProfessorStudentAnalysisScreen(game, username, cls, student,
                            () -> game.setScreen(new ProfessorTestAnalysisScreen(game, username, cls, session, back)))));
            text(p.score() + " pts", COL_X[2], y, 190, 48, 1.4f, Color.WHITE, Align.left);
            text(line.answered() == 0 ? "-" : line.correct() + " / " + line.answered(), COL_X[3], y, 200, 48, 1.4f, Color.WHITE,
                    Align.left);
            text(line.answered() == 0 ? "-" : line.accuracyPercent() + "%", COL_X[4], y, 200, 48, 1.4f,
                    line.answered() > 0 && line.accuracyPercent() < 50 ? LOW : Color.WHITE, Align.left);
            text(line.answered() == 0 ? "-" : line.averageSeconds() + " s", COL_X[5], y, 180, 48, 1.4f, MUTED, Align.left);
            text(p.finished() ? "Finished" : "Left early", COL_X[6], y, 250, 48, 1.4f, p.finished() ? OK : LOW, Align.left);
        }
        if (st.students().size() > 9) {
            text("+ " + (st.students().size() - 9) + " more students not shown", 110, 780, 800, 28, 1.2f, MUTED, Align.left);
        }

        // ── where the class struggled in this test: topics, then the questions most missed ──
        text("Weakest topics", 90, 825, 500, 36, 1.6f, AMBER, Align.left);
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(st.topics().entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded for this test.", 90, 875, 500, 30, 1.3f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, weakest.size()); i++) {
            int[] c = weakest.get(i).getValue();
            text(label(weakest.get(i).getKey()), 90, 868 + i * 34, 250, 32, 1.3f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right  (" + c[1] + ")", 340, 868 + i * 34, 280, 32, 1.3f,
                    rate(c) < 0.5 ? LOW : MUTED, Align.left);
        }
        text("Questions most missed", 650, 825, 600, 36, 1.6f, AMBER, Align.left);
        if (st.questions().isEmpty()) {
            text("No question-level answers recorded.", 650, 875, 640, 30, 1.3f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, st.questions().size()); i++) {
            ProfessorData.QuestionStat q = st.questions().get(i);
            text((q.number() > 0 ? "Q" + q.number() + "  " : "") + q.label(), 650, 868 + i * 34, 470, 32, 1.15f, Color.WHITE,
                    Align.left, PixelFonts.Face.SANS).setEllipsis(true);
            text(Math.round(q.rate() * 100) + "% right  (" + q.right() + " of " + q.total() + ")", 1130, 868 + i * 34, 220, 32,
                    1.15f, q.rate() < 0.5 ? LOW : MUTED, Align.left, PixelFonts.Face.SANS);
        }
        text("AI analysis", 1390, 825, 440, 36, 1.6f, AMBER, Align.left);
        text("A written summary of how the test went, where the class struggled and who needs attention. Only statistics are "
                + "sent, never names.", 1390, 868, 440, 70, 1.15f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 1390, 950, 440, 56, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", this::analyse));
    }

    /** The test numbers for the AI: statistics only, students as S1, S2, ... (names stay here and are put back). */
    private AiFeedback.Result analyse() {
        TestAnalysis st = loaded.stats();
        List<Object> students = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < st.students().size(); i++) {
            TestAnalysis.StudentLine line = st.students().get(i);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", AiFeedback.label(i));
            s.put("place", line.rank());
            s.put("scorePoints", line.participant().score());
            s.put("finished", line.participant().finished());
            s.put("answered", line.answered());
            s.put("correct", line.correct());
            s.put("averageSecondsPerAnswer", line.averageSeconds());
            students.add(s);
            names.add(line.participant().username());
        }
        List<Object> topics = new ArrayList<>();
        st.topics().forEach((topic, c) -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("topic", topic);
            t.put("rightPercent", Math.round(rate(c) * 100));
            t.put("answers", c[1]);
            topics.add(t);
        });
        List<Object> questions = new ArrayList<>();
        for (ProfessorData.QuestionStat q : st.questions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", (q.number() > 0 ? "Q" + q.number() + ": " : "") + q.label());
            m.put("rightPercent", Math.round(q.rate() * 100));
            m.put("answers", q.total());
            questions.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "one class test (points: 500-1000 per correct answer by speed, plus streak bonuses)");
        data.put("test", session.label() + ": " + session.lessonTitle());
        data.put("studentsInClass", st.rosterSize());
        data.put("studentsWhoTookIt", st.joined());
        data.put("finished", st.finished());
        data.put("averageScorePoints", st.average());
        data.put("medianScorePoints", st.median());
        data.put("classAccuracyPercent", st.accuracyPercent());
        data.put("topics", topics);
        data.put("questionsWeakestFirst", questions);
        data.put("students", students);
        return new AiFeedback(codequest.content.Language.fromKey(cls.language())).forClass(data, names);
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
