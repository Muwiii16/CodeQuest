package codequest.screens;

import codequest.Main;
import codequest.data.AiFeedback;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.Lesson;
import codequest.data.Period;
import codequest.data.PeriodSummary;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The analysis of one grading period (Prelims, Midterms, Semis or Finals): how every student did over the lessons of that
 * period, which topics the class got wrong most often in them, and a written AI analysis of the period the professor asks for.
 */
public class ProfessorPeriodAnalysisScreen extends ProfessorScreen {

    private static final String[] HEADERS = { "STUDENT", "LESSONS DONE", "AVG GRADE", "ATTEMPTS", "LAST PLAYED", "NOTE" };
    private static final float[] COL_X = { 110, 470, 700, 920, 1120, 1420 };
    private static final Color LOW = new Color(1f, 0.6f, 0.55f, 1f);

    private record Loaded(List<Lesson> lessons, List<ProfessorData.Student> students, Map<Long, Map<Long, Progress>> progress) {
    }

    private final ClassInfo cls;
    private final int base;
    private List<Lesson> allLessons = List.of();
    private List<ProfessorData.Student> students = List.of();
    private Map<Long, Map<Long, Progress>> progress = Map.of();
    private Period period = Period.PRELIMS;
    private ProfessorData.TopicStats topics = new ProfessorData.TopicStats(Map.of(), Map.of());

    public ProfessorPeriodAnalysisScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Period analysis  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        card(60, 140, 1800, 650);
        card(60, 810, 1800, 210);
        base = mark();
        load("Loading the results...", () -> {
            ProfessorData data = new ProfessorData();
            List<Lesson> ls = new CampaignData().lessons(cls.id());
            List<ProfessorData.Student> st = data.roster(cls.id());
            return new Loaded(ls, st, data.overallProgress(st, ls));
        }, l -> {
            allLessons = l.lessons();
            students = l.students();
            progress = l.progress();
            // open on the first period that has lessons
            for (Period p : Period.values()) {
                if (!Period.filter(allLessons, p).isEmpty()) {
                    period = p;
                    break;
                }
            }
            pick(period);
        });
    }

    private void pick(Period next) {
        period = next;
        List<Lesson> lessons = Period.filter(allLessons, period);
        load("Loading " + period.label() + "...", () -> new ProfessorData().topicAccuracyFor(students, lessons), stats -> {
            topics = stats;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        List<Lesson> lessons = Period.filter(allLessons, period);

        float tx = 90;
        for (Period p : Period.values()) {
            int n = Period.filter(allLessons, p).size();
            solidButton(p.label() + "  (" + n + ")", tx, 148, 240, 44, p == period ? new Color(0.2f, 0.42f, 0.78f, 1f) : BUTTON,
                    () -> pick(p));
            tx += 250;
        }

        if (lessons.isEmpty()) {
            text("No lessons in " + period.label() + " yet. Set a lesson's period in the lesson editor.", 90, 240, 1700, 40, 1.8f,
                    MUTED, Align.left);
            return;
        }
        List<PeriodSummary> summaries = new ArrayList<>();
        for (ProfessorData.Student s : students) {
            summaries.add(PeriodSummary.of(lessons, progress.getOrDefault(s.id(), Map.of())));
        }
        int doneTotal = summaries.stream().mapToInt(PeriodSummary::done).sum();
        double avg = summaries.stream().filter(x -> x.averageGrade() >= 0).mapToInt(PeriodSummary::averageGrade).average()
                .orElse(-1);
        text(lessons.size() + (lessons.size() == 1 ? " lesson" : " lessons") + "   -   " + doneTotal + " of "
                + lessons.size() * students.size() + " lesson results completed   -   class average "
                + (avg < 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", avg)), 90, 207, 1700, 36, 1.5f, AMBER, Align.left);

        rect(90, 250, 1740, 46, new Color(0.2f, 0.16f, 0.06f, 1f));
        for (int i = 0; i < HEADERS.length; i++) {
            text(HEADERS[i], COL_X[i], 250, 300, 46, 1.3f, AMBER, Align.left);
        }
        if (students.isEmpty()) {
            text("No students in this class yet.", 110, 315, 1000, 40, 1.6f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(students.size(), 9); i++) {
            PeriodSummary sum = summaries.get(i);
            float y = 303 + i * 53;
            rect(90, y, 1740, 48, ROW);
            String last = sum.lastPlayed() == null ? "-" : sum.lastPlayed().substring(0, Math.min(10, sum.lastPlayed().length()));
            String note = sum.note();
            boolean flag = "Needs attention".equals(note) || "Not started".equals(note);
            String[] cells = { students.get(i).username(), sum.done() + " / " + sum.total(),
                    sum.averageGrade() < 0 ? "-" : sum.averageGrade() + "%", String.valueOf(sum.attempts()), last, note };
            for (int c = 0; c < cells.length; c++) {
                Color color = c == 5 ? (flag ? LOW : MUTED) : c == 2 && sum.averageGrade() >= 0
                        && sum.averageGrade() < PeriodSummary.LOW_GRADE ? LOW : Color.WHITE;
                text(cells[c], COL_X[c], y, 340, 48, 1.4f, color, Align.left);
            }
        }
        if (students.size() > 9) {
            text("+ " + (students.size() - 9) + " more students not shown", 110, 780, 800, 28, 1.2f, MUTED, Align.left);
        }

        // ── where students struggled in this period (from the answer log) ──
        text("Where students struggled in " + period.label(), 90, 825, 850, 36, 1.7f, AMBER, Align.left);
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(topics.classTopics().entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded for this period yet.", 90, 875, 800, 30, 1.4f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, weakest.size()); i++) {
            Map.Entry<String, int[]> e = weakest.get(i);
            int[] c = e.getValue();
            text(label(e.getKey()), 90, 872 + i * 35, 300, 32, 1.4f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right   (" + c[0] + " of " + c[1] + " answers)", 380, 872 + i * 35, 520, 32,
                    1.4f, rate(c) < 0.5 ? LOW : MUTED, Align.left);
        }

        text("AI analysis of " + period.label(), 960, 825, 860, 36, 1.7f, AMBER, Align.left);
        text("A written summary of how the class did in this period, where it struggled and who needs attention. Only "
                + "statistics are sent to the AI, never names.", 960, 868, 860, 70, 1.2f, MUTED, Align.topLeft,
                PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 960, 950, 520, 60, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", () -> analyse(lessons, summaries)));
    }

    /** The period's numbers for the AI: statistics and topics only, students as S1, S2, ... (names stay here). */
    private AiFeedback.Result analyse(List<Lesson> lessons, List<PeriodSummary> summaries) {
        List<Object> topicList = new ArrayList<>();
        for (Map.Entry<String, int[]> e : topics.classTopics().entrySet()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("topic", e.getKey());
            t.put("rightPercent", Math.round(rate(e.getValue()) * 100));
            t.put("answers", e.getValue()[1]);
            topicList.add(t);
        }
        List<Object> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            PeriodSummary sum = summaries.get(i);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", AiFeedback.label(i));
            s.put("lessonsCompleted", sum.done() + " of " + sum.total());
            s.put("averageGradePercent", sum.averageGrade() < 0 ? null : sum.averageGrade());
            s.put("attempts", sum.attempts());
            Map<String, Object> accuracy = new LinkedHashMap<>();
            topics.byStudent().getOrDefault(students.get(i).id(), Map.of())
                    .forEach((topic, c) -> accuracy.put(topic, Math.round(rate(c) * 100)));
            s.put("topicRightPercent", accuracy);
            rows.add(s);
            names.add(students.get(i).username());
        }
        List<Object> lessonList = new ArrayList<>();
        for (Lesson l : lessons) {
            lessonList.add("Lesson " + l.number() + ": " + l.title());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", period.label() + " period");
        data.put("lessons", lessonList);
        data.put("topics", topicList);
        data.put("students", rows);
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
