package codequest.screens;

import codequest.Main;
import codequest.data.AiFeedback;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ClassStats;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysis of the whole class on one screen: how much of the work is completed, the average grade, how each grading
 * period went, the topics and lessons the class struggled with, the students who need attention (click one to open that
 * student's analysis), the certificates earned, and a written AI analysis of the class the professor asks for.
 */
public class ProfessorClassAnalysisScreen extends ProfessorScreen {

    private static final Color LOW = new Color(1f, 0.6f, 0.55f, 1f);
    private static final Color OK = new Color(0.55f, 0.9f, 0.65f, 1f);
    private static final int ATTENTION_ROWS = 6;

    private record Loaded(ClassStats stats, ProfessorData.TopicStats topics, Map<String, Integer> certificates) {
    }

    private final ClassInfo cls;
    private final int base;
    private Loaded loaded;

    public ProfessorClassAnalysisScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Class analysis  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        base = mark();
        load("Loading the class...", () -> {
            ProfessorData data = new ProfessorData();
            List<Lesson> lessons = new CampaignData().lessons(cls.id());
            List<ProfessorData.Student> roster = data.roster(cls.id());
            ClassStats stats = ClassStats.of(lessons, roster, data.overallProgress(roster, lessons));
            return new Loaded(stats, data.topicAccuracyFor(roster, lessons), data.classCertificateCounts(cls.id()));
        }, l -> {
            loaded = l;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        ClassStats st = loaded.stats();
        if (st.lessons() == 0 || st.students() == 0) {
            card(60, 140, 1800, 200);
            text(st.lessons() == 0 ? "This class has no lessons yet." : "This class has no students yet.", 90, 140, 1740, 200,
                    2f, MUTED, Align.center);
            return;
        }

        // ── the four numbers ──
        float cw = 435;
        kpi(0, "Work completed", st.completionPercent() + "%", st.completionPercent() < 50 ? LOW : OK, cw);
        kpi(1, "Class average grade", st.averageGrade() < 0 ? "n/a" : st.averageGrade() + "%",
                st.averageGrade() >= 0 && st.averageGrade() < 50 ? LOW : Color.WHITE, cw);
        kpi(2, "Students who have not played", st.noActivity() + " of " + st.students(), st.noActivity() > 0 ? LOW : OK, cw);
        kpi(3, "Need attention", String.valueOf(st.attention().size()), st.attention().isEmpty() ? OK : LOW, cw);

        // ── grading periods ──
        card(60, 270, 880, 330);
        text("Grading periods", 90, 282, 600, 36, 1.7f, AMBER, Align.left);
        if (st.periods().isEmpty()) {
            text("No lessons yet.", 90, 340, 600, 30, 1.4f, MUTED, Align.left);
        }
        float y = 330;
        for (ClassStats.PeriodStat p : st.periods()) {
            text(p.period().label(), 90, y, 200, 44, 1.5f, Color.WHITE, Align.left);
            rect(300, y + 10, 380, 24, new Color(0.08f, 0.1f, 0.14f, 1f));
            rect(300, y + 10, Math.max(2, 380 * Math.max(0, p.completionPercent()) / 100f), 24, GOOD);
            text(p.completionPercent() + "% done", 690, y, 130, 44, 1.2f, MUTED, Align.left);
            text(p.averageGrade() < 0 ? "-" : p.averageGrade() + "%", 830, y, 90, 44, 1.4f,
                    p.averageGrade() >= 0 && p.averageGrade() < 50 ? LOW : Color.WHITE, Align.left);
            y += 62;
        }

        // ── where the class struggled ──
        card(980, 270, 880, 330);
        text("Where the class struggled", 1010, 282, 800, 36, 1.7f, AMBER, Align.left);
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(loaded.topics().classTopics().entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded yet.", 1010, 330, 800, 30, 1.4f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(3, weakest.size()); i++) {
            int[] c = weakest.get(i).getValue();
            text(label(weakest.get(i).getKey()), 1010, 326 + i * 34, 300, 32, 1.4f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right   (" + c[1] + " answers)", 1320, 326 + i * 34, 500, 32, 1.4f,
                    rate(c) < 0.5 ? LOW : MUTED, Align.left);
        }
        text("Lessons that need a look", 1010, 440, 800, 30, 1.5f, AMBER, Align.left);
        List<ClassStats.LessonStat> hard = st.lowestGrade(2);
        List<ClassStats.LessonStat> least = st.leastCompleted(2);
        float ly = 476;
        for (ClassStats.LessonStat l : hard) {
            text("Lowest grade:  " + l.lesson().label() + " - " + l.lesson().title(), 1010, ly, 560, 30, 1.25f, Color.WHITE,
                    Align.left, PixelFonts.Face.SANS).setEllipsis(true);
            text(l.averageGrade() + "% average", 1580, ly, 250, 30, 1.25f, l.averageGrade() < 50 ? LOW : MUTED, Align.left,
                    PixelFonts.Face.SANS);
            ly += 30;
        }
        for (ClassStats.LessonStat l : least) {
            text("Least completed:  " + l.lesson().label() + " - " + l.lesson().title(), 1010, ly, 560, 30, 1.25f, Color.WHITE,
                    Align.left, PixelFonts.Face.SANS).setEllipsis(true);
            text(l.completed() + " of " + l.students() + " students", 1580, ly, 250, 30, 1.25f, MUTED, Align.left,
                    PixelFonts.Face.SANS);
            ly += 30;
        }

        // ── students who need attention ──
        card(60, 620, 1240, 400);
        text("Students who need attention  (" + st.attention().size() + ")", 90, 632, 900, 36, 1.7f, AMBER, Align.left);
        if (st.attention().isEmpty()) {
            text("Everyone has completed something and is averaging 50% or more.", 90, 690, 1180, 34, 1.4f, OK, Align.left);
        }
        for (int i = 0; i < Math.min(ATTENTION_ROWS, st.attention().size()); i++) {
            ClassStats.StudentRow r = st.attention().get(i);
            float ry = 680 + i * 54;
            rect(90, ry, 1180, 48, ROW);
            solidButton(r.student().username(), 98, ry + 2, 330, 44, ROW, Color.WHITE,
                    () -> game.setScreen(new ProfessorStudentAnalysisScreen(game, username, cls, r.student(),
                            () -> game.setScreen(new ProfessorClassAnalysisScreen(game, username, cls)))));
            text(r.summary().note(), 450, ry, 260, 48, 1.3f, LOW, Align.left);
            text(r.summary().done() + " / " + r.summary().total() + " lessons", 720, ry, 240, 48, 1.3f, MUTED, Align.left);
            text(r.summary().averageGrade() < 0 ? "-" : r.summary().averageGrade() + "%", 980, ry, 120, 48, 1.3f, Color.WHITE,
                    Align.left);
            text(r.summary().attempts() + " attempts", 1090, ry, 170, 48, 1.2f, MUTED, Align.left);
        }
        if (st.attention().size() > ATTENTION_ROWS) {
            text("+ " + (st.attention().size() - ATTENTION_ROWS) + " more: see Overall class performance", 90, 1002, 900, 24,
                    1.1f, MUTED, Align.left, PixelFonts.Face.SANS);
        }

        // ── certificates and the AI analysis ──
        card(1340, 620, 520, 400);
        text("Certificates earned", 1370, 632, 460, 36, 1.7f, AMBER, Align.left);
        if (loaded.certificates().isEmpty()) {
            text("None yet.", 1370, 680, 460, 30, 1.3f, MUTED, Align.left, PixelFonts.Face.SANS);
        }
        float cy = 678;
        for (Map.Entry<String, Integer> e : loaded.certificates().entrySet()) {
            text(e.getKey() + ":  " + e.getValue() + (e.getValue() == 1 ? " student" : " students"), 1370, cy, 460, 28, 1.25f,
                    Color.WHITE, Align.left, PixelFonts.Face.SANS);
            cy += 28;
        }
        text("AI summary. Only statistics are sent, never names.",
                1370, 855, 460, 70, 1.15f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 1370, 940, 460, 56, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", this::analyse));
    }

    private void kpi(int slot, String caption, String value, Color color, float w) {
        float x = 60 + slot * (w + 20);
        card(x, 145, w, 105);
        text(caption, x + 24, 152, w - 40, 30, 1.2f, MUTED, Align.left, PixelFonts.Face.SANS);
        text(value, x + 24, 184, w - 40, 60, 2.8f, color, Align.left);
    }

    /** The class's numbers for the AI: statistics only, students as S1, S2, ... (names stay here and are put back). */
    private AiFeedback.Result analyse() {
        ClassStats st = loaded.stats();
        List<Object> periods = new ArrayList<>();
        for (ClassStats.PeriodStat p : st.periods()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", p.period().label());
            m.put("lessons", p.lessons());
            m.put("completedPercent", p.completionPercent());
            m.put("averageGradePercent", p.averageGrade() < 0 ? null : p.averageGrade());
            periods.add(m);
        }
        List<Object> lessonList = new ArrayList<>();
        for (ClassStats.LessonStat l : st.lessonStats()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lesson", l.lesson().label() + ": " + l.lesson().title());
            m.put("period", l.lesson().periodEnum().label());
            m.put("completedBy", l.completed() + " of " + l.students());
            m.put("averageGradePercent", l.averageGrade() < 0 ? null : l.averageGrade());
            lessonList.add(m);
        }
        List<Object> topics = new ArrayList<>();
        loaded.topics().classTopics().forEach((topic, c) -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("topic", topic);
            t.put("rightPercent", Math.round(rate(c) * 100));
            t.put("answers", c[1]);
            topics.add(t);
        });
        List<Object> students = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int i = 0;
        // every student (the attention list is only part of the class)
        for (ClassStats.StudentRow r : st.rows()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", AiFeedback.label(i++));
            s.put("lessonsCompleted", r.summary().done() + " of " + r.summary().total());
            s.put("averageGradePercent", r.summary().averageGrade() < 0 ? null : r.summary().averageGrade());
            s.put("attempts", r.summary().attempts());
            Map<String, Object> accuracy = new LinkedHashMap<>();
            loaded.topics().byStudent().getOrDefault(r.student().id(), Map.of())
                    .forEach((topic, c) -> accuracy.put(topic, Math.round(rate(c) * 100)));
            s.put("topicRightPercent", accuracy);
            students.add(s);
            names.add(r.student().username());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "the whole class, all lessons");
        data.put("workCompletedPercent", st.completionPercent());
        data.put("classAverageGradePercent", st.averageGrade() < 0 ? null : st.averageGrade());
        data.put("studentsWhoHaveNotPlayed", st.noActivity());
        data.put("periods", periods);
        data.put("lessons", lessonList);
        data.put("topics", topics);
        data.put("students", students);
        data.put("certificatesEarned", loaded.certificates());
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
