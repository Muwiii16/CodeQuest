package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.data.AiFeedback;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.Lesson;
import codequest.data.Period;
import codequest.data.PeriodSummary;
import codequest.data.ProfessorData;
import codequest.session.Grades;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysis of one student: their result on every lesson (grouped by grading period), how each period went, how they
 * compare with the class average, which topics they got wrong most often, the certificates they earned, and a written AI
 * analysis of the student the professor asks for.
 */
public class ProfessorStudentAnalysisScreen extends ProfessorScreen {

    private static final int ROWS = 7;
    private static final String[] HEADERS = { "#", "LESSON", "PERIOD", "STATUS", "RANK", "GRADE", "ATTEMPTS", "LAST PLAYED" };
    private static final float[] COL_X = { 110, 180, 740, 920, 1130, 1250, 1390, 1560 };
    private static final Color LOW = new Color(1f, 0.6f, 0.55f, 1f);
    private static final Color OK = new Color(0.55f, 0.9f, 0.65f, 1f);

    private record Loaded(List<Lesson> lessons, Map<Long, Progress> mine, int classAverage, ProfessorData.TopicStats topics,
            List<String> certificates) {
    }

    private final ClassInfo cls;
    private final ProfessorData.Student student;
    private final int base;
    private Loaded loaded;
    private int page;

    public ProfessorStudentAnalysisScreen(Main game, String username, ClassInfo cls, ProfessorData.Student student,
            Runnable back) {
        super(game, username, "Student  -  " + student.username(), back);
        this.cls = cls;
        this.student = student;
        card(60, 140, 1800, 650);
        card(60, 810, 1800, 210);
        base = mark();
        load("Loading the results...", () -> {
            ProfessorData data = new ProfessorData();
            List<Lesson> lessons = new CampaignData().lessons(cls.id());
            List<ProfessorData.Student> roster = data.roster(cls.id());
            Map<Long, Map<Long, Progress>> all = data.overallProgress(roster, lessons);
            List<PeriodSummary> classSummaries = new ArrayList<>();
            for (ProfessorData.Student s : roster) {
                classSummaries.add(PeriodSummary.of(lessons, all.getOrDefault(s.id(), Map.of())));
            }
            return new Loaded(lessons, all.getOrDefault(student.id(), Map.of()), PeriodSummary.classAverage(classSummaries),
                    data.topicAccuracyFor(List.of(student), lessons), data.studentCertificates(cls.id(), student.id()));
        }, l -> {
            loaded = l;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        List<Lesson> lessons = loaded.lessons();
        Map<Long, Progress> mine = loaded.mine();
        PeriodSummary all = PeriodSummary.of(lessons, mine);

        String who = student.fullName() == null || student.fullName().isBlank() ? student.username()
                : student.username() + "   -   " + student.fullName();
        text(who + "   -   " + cls.sectionName(), 90, 155, 1700, 40, 1.9f, AMBER, Align.left).setEllipsis(true);
        String last = all.lastPlayed() == null ? "never" : all.lastPlayed().substring(0, Math.min(10, all.lastPlayed().length()));
        text(all.done() + " of " + all.total() + " lessons completed   -   average grade "
                + (all.averageGrade() < 0 ? "n/a" : all.averageGrade() + "%")
                + (loaded.classAverage() < 0 ? "" : "  (class " + loaded.classAverage() + "%)") + "   -   " + all.attempts()
                + " attempts   -   last played " + last, 90, 200, 1700, 34, 1.4f, MUTED, Align.left).setEllipsis(true);

        // how each grading period went
        float px = 90;
        for (Period p : Period.values()) {
            List<Lesson> inPeriod = Period.filter(lessons, p);
            PeriodSummary ps = PeriodSummary.of(inPeriod, mine);
            rect(px, 245, 420, 52, ROW);
            String line = inPeriod.isEmpty() ? "no lessons" : ps.done() + " / " + ps.total()
                    + (ps.averageGrade() < 0 ? "" : "   " + ps.averageGrade() + "%");
            boolean low = ps.averageGrade() >= 0 && ps.averageGrade() < PeriodSummary.LOW_GRADE;
            text(p.label(), px + 14, 245, 150, 52, 1.4f, AMBER, Align.left);
            text(line, px + 150, 245, 256, 52, 1.4f, inPeriod.isEmpty() ? MUTED : low ? LOW : Color.WHITE, Align.left);
            px += 435;
        }

        rect(90, 308, 1740, 44, new Color(0.2f, 0.16f, 0.06f, 1f));
        for (int i = 0; i < HEADERS.length; i++) {
            text(HEADERS[i], COL_X[i], 308, 300, 44, 1.2f, AMBER, Align.left);
        }
        if (lessons.isEmpty()) {
            text("This class has no lessons yet.", 110, 372, 1000, 40, 1.6f, MUTED, Align.left);
        }
        int from = page * ROWS;
        int to = Math.min(lessons.size(), from + ROWS);
        for (int i = from; i < to; i++) {
            Lesson l = lessons.get(i);
            Progress p = mine.get(l.stageId());
            boolean done = p != null && p.completed();
            float y = 358 + (i - from) * 50;
            rect(90, y, 1740, 46, ROW);
            int grade = done ? Grades.percent(p.score(), p.max(l.number())) : -1;
            String[] cells = { String.valueOf(l.number()), l.title(), l.periodEnum().label(),
                    done ? "Completed" : p == null ? "Not started" : "In progress", done && p.rank() != null ? p.rank() : "-",
                    grade < 0 ? "-" : grade + "%", p == null ? "-" : String.valueOf(p.attempts()),
                    p == null || p.lastPlayed() == null ? "-" : p.lastPlayed().substring(0, Math.min(10, p.lastPlayed().length())) };
            for (int c = 0; c < cells.length; c++) {
                Color color = c == 3 ? (done ? OK : MUTED) : c == 4 ? AMBER : c == 5 && grade >= 0
                        && grade < PeriodSummary.LOW_GRADE ? LOW : Color.WHITE;
                text(cells[c], COL_X[c], y, c == 1 ? 540 : 300, 46, 1.35f, color, Align.left).setEllipsis(true);
            }
        }
        if (lessons.size() > ROWS) {
            int pages = (lessons.size() + ROWS - 1) / ROWS;
            if (page > 0) {
                solidButton("< Prev", 700, 735, 200, 44, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 920, 735, 200, 44, 1.4f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 1140, 735, 200, 44, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }

        // ── where this student struggled (from the answer log) ──
        text("Where " + student.username() + " struggled", 90, 825, 850, 36, 1.7f, AMBER, Align.left).setEllipsis(true);
        Map<String, int[]> topics = loaded.topics().byStudent().getOrDefault(student.id(), Map.of());
        List<Map.Entry<String, int[]>> weakest = new ArrayList<>(topics.entrySet());
        weakest.sort((a, b) -> Double.compare(rate(a.getValue()), rate(b.getValue())));
        if (weakest.isEmpty()) {
            text("No answers recorded for this student yet.", 90, 875, 800, 30, 1.4f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(4, weakest.size()); i++) {
            int[] c = weakest.get(i).getValue();
            text(label(weakest.get(i).getKey()), 90, 872 + i * 35, 300, 32, 1.4f, Color.WHITE, Align.left);
            text(Math.round(rate(c) * 100) + "% right   (" + c[0] + " of " + c[1] + " answers)", 380, 872 + i * 35, 520, 32,
                    1.4f, rate(c) < 0.5 ? LOW : MUTED, Align.left);
        }

        text("Certificates and AI analysis", 960, 825, 860, 36, 1.7f, AMBER, Align.left);
        text(loaded.certificates().isEmpty() ? "No certificates earned yet."
                : "Certificates: " + String.join(", ", loaded.certificates()), 960, 868, 860, 34, 1.3f,
                loaded.certificates().isEmpty() ? MUTED : OK, Align.left, PixelFonts.Face.SANS).setEllipsis(true);
        text("A written summary of how this student is doing and what to do about it. Only statistics are sent to the AI, "
                + "never the name.", 960, 905, 860, 60, 1.2f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton("Write AI analysis", 960, 960, 520, 54, new Color(0.45f, 0.3f, 0.7f, 1f),
                () -> showAiAnalysis("Writing the analysis...", this::analyse));
    }

    /** The student's numbers for the AI (no name: the student is S1 and the name is put back in the answer). */
    private AiFeedback.Result analyse() {
        List<Lesson> lessons = loaded.lessons();
        Map<Long, Progress> mine = loaded.mine();
        List<Object> lessonRows = new ArrayList<>();
        for (Lesson l : lessons) {
            Progress p = mine.get(l.stageId());
            boolean done = p != null && p.completed();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lesson", l.number());
            row.put("period", l.periodEnum().label());
            row.put("status", done ? "Completed" : p == null ? "Not started" : "In progress");
            if (done) {
                row.put("rank", p.rank());
                row.put("gradePercent", Grades.percent(p.score(), p.max(l.number())));
            }
            row.put("attempts", p == null ? 0 : p.attempts());
            lessonRows.add(row);
        }
        List<Object> periods = new ArrayList<>();
        for (Period p : Period.values()) {
            List<Lesson> inPeriod = Period.filter(lessons, p);
            if (inPeriod.isEmpty()) {
                continue;
            }
            PeriodSummary ps = PeriodSummary.of(inPeriod, mine);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", p.label());
            row.put("lessonsCompleted", ps.done() + " of " + ps.total());
            row.put("averageGradePercent", ps.averageGrade() < 0 ? null : ps.averageGrade());
            periods.add(row);
        }
        Map<String, Object> accuracy = new LinkedHashMap<>();
        loaded.topics().byStudent().getOrDefault(student.id(), Map.of())
                .forEach((topic, c) -> accuracy.put(topic, Math.round(rate(c) * 100)));
        PeriodSummary all = PeriodSummary.of(lessons, mine);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", AiFeedback.label(0));
        s.put("lessonsCompleted", all.done() + " of " + all.total());
        s.put("averageGradePercent", all.averageGrade() < 0 ? null : all.averageGrade());
        s.put("classAverageGradePercent", loaded.classAverage() < 0 ? null : loaded.classAverage());
        s.put("attempts", all.attempts());
        s.put("certificatesEarned", loaded.certificates());
        s.put("periods", periods);
        s.put("lessons", lessonRows);
        s.put("topicRightPercent", accuracy);
        List<Object> students = new ArrayList<>();
        students.add(s);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", "one student, compared with the class average");
        data.put("students", students);
        return new AiFeedback(codequest.content.Language.fromKey(cls.language())).forClass(data, List.of(student.username()));
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
