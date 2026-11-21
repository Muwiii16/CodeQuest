package codequest.screens;

import codequest.Main;
import codequest.data.AiFeedback;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import codequest.data.SubjectStats;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysis of a whole subject: every section of the professor that teaches it, pooled together and side by side. Pick
 * the subject with the tabs (a professor may teach several); the four numbers, the grading periods, the sections table (open
 * one to see that section's own class analysis), the weakest topics and lessons and a written AI analysis follow.
 */
public class ProfessorSubjectAnalysisScreen extends ProfessorScreen {

    private static final Color LOW = new Color(1f, 0.6f, 0.55f, 1f);
    private static final Color OK = new Color(0.55f, 0.9f, 0.65f, 1f);
    private static final int SECTION_ROWS = 5;

    private record Loaded(Map<String, List<ClassInfo>> subjects, Map<String, SubjectStats> stats,
            Map<String, Map<String, int[]>> topics) {
    }

    private final int base;
    private Loaded loaded;
    private String selected;

    public ProfessorSubjectAnalysisScreen(Main game, String username) {
        this(game, username, null);
    }

    /** {@code subjectKey} opens that subject (as {@link SubjectStats#key} names it); null opens the first. */
    public ProfessorSubjectAnalysisScreen(Main game, String username, String subjectKey) {
        super(game, username, "Subject analysis", () -> game.setScreen(new ProfessorDashboardScreen(game, username)));
        this.selected = subjectKey;
        base = mark();
        load("Loading your subjects...", () -> {
            ProfessorData data = new ProfessorData();
            CampaignData campaigns = new CampaignData();
            Map<String, List<ClassInfo>> subjects = SubjectStats.group(data.listClasses(data.professorId(username)));
            Map<String, SubjectStats> stats = new LinkedHashMap<>();
            Map<String, Map<String, int[]>> topics = new LinkedHashMap<>();
            for (Map.Entry<String, List<ClassInfo>> e : subjects.entrySet()) {
                List<SubjectStats.SectionInput> inputs = new ArrayList<>();
                List<Map<String, int[]>> perSection = new ArrayList<>();
                for (ClassInfo c : e.getValue()) {
                    List<codequest.data.Lesson> lessons = campaigns.lessons(c.id());
                    List<ProfessorData.Student> roster = data.roster(c.id());
                    inputs.add(new SubjectStats.SectionInput(c, lessons, roster, data.overallProgress(roster, lessons)));
                    perSection.add(data.topicAccuracyFor(roster, lessons).classTopics());
                }
                stats.put(e.getKey(), SubjectStats.of(e.getValue().get(0).subjectName(), inputs));
                topics.put(e.getKey(), SubjectStats.mergeTopics(perSection));
            }
            return new Loaded(subjects, stats, topics);
        }, l -> {
            loaded = l;
            if (selected == null || !l.stats().containsKey(selected)) {
                selected = l.stats().isEmpty() ? null : l.stats().keySet().iterator().next();
            }
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        if (selected == null) {
            card(60, 140, 1800, 200);
            text("You do not handle any subject yet. Create a class first.", 90, 140, 1740, 200, 2f, MUTED, Align.center);
            return;
        }
        // one tab per subject
        float tx = 90;
        for (Map.Entry<String, SubjectStats> e : loaded.stats().entrySet()) {
            String key = e.getKey();
            String label = e.getValue().name() + "  (" + e.getValue().sections().size() + ")";
            float w = Math.min(520, 60 + label.length() * 17);
            solidButton(label, tx, 135, w, 44, key.equals(selected) ? new Color(0.2f, 0.42f, 0.78f, 1f) : BUTTON, () -> {
                selected = key;
                rebuild();
            });
            tx += w + 12;
        }
        SubjectStats st = loaded.stats().get(selected);
        String sectionWord = st.sections().size() == 1 ? "section" : "sections";

        // ── the four numbers ──
        kpi(0, "Students in " + st.sections().size() + " " + sectionWord, String.valueOf(st.students()), Color.WHITE);
        kpi(1, "Work completed", st.completionPercent() + "%", st.completionPercent() < 50 ? LOW : OK);
        kpi(2, "Average grade", st.averageGrade() < 0 ? "n/a" : st.averageGrade() + "%",
                st.averageGrade() >= 0 && st.averageGrade() < 50 ? LOW : Color.WHITE);
        kpi(3, "Students who need attention", String.valueOf(st.attention()), st.attention() == 0 ? OK : LOW);

        // ── the sections, side by side ──
        card(60, 295, 1240, 380);
        text("Sections", 90, 305, 500, 36, 1.7f, AMBER, Align.left);
        text("SECTION", 90, 348, 300, 30, 1.2f, MUTED, Align.left);
        text("STUDENTS", 480, 348, 140, 30, 1.2f, MUTED, Align.left);
        text("DONE", 640, 348, 120, 30, 1.2f, MUTED, Align.left);
        text("AVG GRADE", 780, 348, 160, 30, 1.2f, MUTED, Align.left);
        text("ATTENTION", 950, 348, 150, 30, 1.2f, MUTED, Align.left);
        SubjectStats.Section best = st.bestSection();
        for (int i = 0; i < Math.min(SECTION_ROWS, st.sections().size()); i++) {
            SubjectStats.Section s = st.sections().get(i);
            float y = 384 + i * 56;
            rect(90, y, 1180, 50, ROW);
            text(s.cls().sectionName() + (s == best && st.sections().size() > 1 ? "  *" : ""), 100, y, 370, 50, 1.4f, Color.WHITE,
                    Align.left).setEllipsis(true);
            text(String.valueOf(s.stats().students()), 480, y, 140, 50, 1.4f, Color.WHITE, Align.left);
            text(s.stats().completionPercent() + "%", 640, y, 120, 50, 1.4f, Color.WHITE, Align.left);
            text(s.stats().averageGrade() < 0 ? "-" : s.stats().averageGrade() + "%", 780, y, 160, 50, 1.4f,
                    s.stats().averageGrade() >= 0 && s.stats().averageGrade() < 50 ? LOW : Color.WHITE, Align.left);
            text(String.valueOf(s.stats().attention().size()), 950, y, 100, 50, 1.4f, s.stats().attention().isEmpty() ? OK : LOW,
                    Align.left);
            ClassInfo c = s.cls();
            solidButton("Open", 1085, y + 4, 170, 42, new Color(0.2f, 0.42f, 0.78f, 1f),
                    () -> game.setScreen(new ProfessorClassAnalysisScreen(game, username, c)));
        }
        if (st.sections().size() > SECTION_ROWS) {
            text("+ " + (st.sections().size() - SECTION_ROWS) + " more sections", 90, 660, 700, 24, 1.1f, MUTED, Align.left);
        } else if (best != null && st.sections().size() > 1) {
            text("*  highest average grade", 90, 660, 700, 24, 1.1f, MUTED, Align.left, PixelFonts.Face.SANS);
        }

        // ── grading periods, pooled ──
        card(1340, 295, 520, 380);
        text("Grading periods", 1370, 305, 460, 36, 1.7f, AMBER, Align.left);
        if (st.periods().isEmpty()) {
            text("No lessons yet.", 1370, 360, 460, 30, 1.3f, MUTED, Align.left);
        }
        float py = 355;
        for (SubjectStats.PeriodPool p : st.periods()) {
            text(p.period().label(), 1370, py, 140, 44, 1.4f, Color.WHITE, Align.left);
            rect(1515, py + 10, 190, 22, new Color(0.08f, 0.1f, 0.14f, 1f));
            rect(1515, py + 10, Math.max(2, 190 * Math.max(0, p.completionPercent()) / 100f), 22, GOOD);
            text(p.completionPercent() + "%", 1712, py, 70, 44, 1.2f, MUTED, Align.left);
            text(p.averageGrade() < 0 ? "-" : p.averageGrade() + "%", 1780, py, 70, 44, 1.3f,
                    p.averageGrade() >= 0 && p.averageGrade() < 50 ? LOW : Color.WHITE, Align.left);
            py += 72;
        }

        // ── where the subject struggled ──
        card(60, 695, 1240, 325);
        text("Where the subject struggled", 90, 705, 800, 36, 1.7f, AMBER, Align.left);
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(loaded.topics().get(selected).entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded yet.", 90, 755, 800, 30, 1.3f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, weakest.size()); i++) {
            int[] c = weakest.get(i).getValue();
            text(label(weakest.get(i).getKey()), 90, 750 + i * 34, 260, 32, 1.35f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right  (" + c[1] + " answers)", 360, 750 + i * 34, 330, 32, 1.35f,
                    rate(c) < 0.5 ? LOW : MUTED, Align.left);
        }
        text("Lowest graded lessons", 90, 898, 800, 30, 1.5f, AMBER, Align.left);
        List<SubjectStats.LessonRef> hard = st.lowestGrade(3);
        if (hard.isEmpty()) {
            text("No lesson has been completed yet.", 90, 938, 800, 28, 1.3f, MUTED, Align.left);
        }
        for (int i = 0; i < hard.size(); i++) {
            SubjectStats.LessonRef r = hard.get(i);
            text(r.cls().sectionName() + "  -  " + r.stat().lesson().label() + ": " + r.stat().lesson().title(), 90, 934 + i * 28,
                    760, 28, 1.2f, Color.WHITE, Align.left, PixelFonts.Face.SANS).setEllipsis(true);
            text(r.stat().averageGrade() + "% average", 870, 934 + i * 28, 300, 28, 1.2f,
                    r.stat().averageGrade() < 50 ? LOW : MUTED, Align.left, PixelFonts.Face.SANS);
        }

        // ── AI analysis ──
        card(1340, 695, 520, 325);
        text("AI analysis", 1370, 705, 460, 36, 1.7f, AMBER, Align.left);
        text("A written summary of how the whole subject is doing, how the sections compare and what to do next. Only "
                + "statistics are sent; sections are called Section 1, 2, ... and student names are never sent.", 1370, 752, 460, 150,
                1.2f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 1370, 940, 460, 56, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", this::analyse));
    }

    private void kpi(int slot, String caption, String value, Color color) {
        float w = 435;
        float x = 60 + slot * (w + 20);
        card(x, 190, w, 90);
        text(caption, x + 24, 194, w - 40, 28, 1.15f, MUTED, Align.left, PixelFonts.Face.SANS);
        text(value, x + 24, 222, w - 40, 54, 2.6f, color, Align.left);
    }

    /** The subject numbers for the AI: statistics only; the sections are Section 1, 2, ... so no class name is sent. */
    private AiFeedback.Result analyse() {
        SubjectStats st = loaded.stats().get(selected);
        List<Object> sections = new ArrayList<>();
        for (int i = 0; i < st.sections().size(); i++) {
            SubjectStats.Section s = st.sections().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", "Section " + (i + 1));
            m.put("students", s.stats().students());
            m.put("lessons", s.stats().lessons());
            m.put("workCompletedPercent", s.stats().completionPercent());
            m.put("averageGradePercent", s.stats().averageGrade() < 0 ? null : s.stats().averageGrade());
            m.put("studentsWhoHaveNotPlayed", s.stats().noActivity());
            m.put("studentsNeedingAttention", s.stats().attention().size());
            sections.add(m);
        }
        List<Object> periods = new ArrayList<>();
        for (SubjectStats.PeriodPool p : st.periods()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", p.period().label());
            m.put("lessons", p.lessons());
            m.put("completedPercent", p.completionPercent());
            m.put("averageGradePercent", p.averageGrade() < 0 ? null : p.averageGrade());
            periods.add(m);
        }
        List<Object> topics = new ArrayList<>();
        loaded.topics().get(selected).forEach((topic, c) -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("topic", topic);
            t.put("rightPercent", Math.round(rate(c) * 100));
            t.put("answers", c[1]);
            topics.add(t);
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "a whole subject taught to " + st.sections().size() + " section(s); compare the sections");
        data.put("students", st.students());
        data.put("workCompletedPercent", st.completionPercent());
        data.put("averageGradePercent", st.averageGrade() < 0 ? null : st.averageGrade());
        data.put("studentsNeedingAttention", st.attention());
        data.put("sections", sections);
        data.put("periods", periods);
        data.put("topics", topics);
        // the class endpoint expects a student list; here the rows are the sections, so no names are involved
        data.put("studentRowsAreSections", true);
        AiFeedback.Result r = new AiFeedback().forClass(data, List.of());
        if (!r.ok()) {
            return r;
        }
        // the AI saw "Section 1, 2, ..."; the professor reads the real section names
        List<String> names = new ArrayList<>();
        st.sections().forEach(s -> names.add(s.cls().sectionName()));
        return new AiFeedback.Result(restoreSections(r.text(), names), null);
    }

    /** Replaces "Section 1", "Section 2", ... in the AI's text with the real section names (an unknown number stays as it is). */
    static String restoreSections(String text, List<String> names) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bSection (\\d{1,2})\\b").matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1)) - 1;
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(index >= 0 && index < names.size() ? names.get(index)
                    : m.group()));
        }
        m.appendTail(out);
        return out.toString();
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
