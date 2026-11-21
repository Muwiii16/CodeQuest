package codequest.data;

import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The numbers of a whole subject: every section (class) of the professor that teaches it, side by side and pooled together.
 * A subject is named by the class's subject name (or its course code when it has none), so "Programming 1" taught to BSCS 1-1
 * and CS 1-1 is one subject with two sections. Pure arithmetic (no screen, no network), so it can be tested.
 */
public record SubjectStats(String name, List<Section> sections, int students, int lessons, int completionPercent,
        int averageGrade, int noActivity, int attention, List<PeriodPool> periods) {

    /** What one section needs to be measured. */
    public record SectionInput(ClassInfo cls, List<Lesson> lessons, List<ProfessorData.Student> roster,
            Map<Long, Map<Long, Progress>> progress) {
    }

    /** One section with its own class numbers. */
    public record Section(ClassInfo cls, ClassStats stats) {
    }

    /** One grading period pooled over all the sections. -1 where there is nothing to measure. */
    public record PeriodPool(Period period, int lessons, int completionPercent, int averageGrade) {
    }

    /** A lesson of one section (lessons differ from section to section, so the section is named with it). */
    public record LessonRef(ClassInfo cls, ClassStats.LessonStat stat) {
    }

    /** The key two classes share when they teach the same subject (case and outer spaces do not matter). */
    public static String key(ClassInfo c) {
        return c.subjectName().trim().toLowerCase(Locale.ROOT);
    }

    /** The professor's classes grouped by subject, in the order the subjects first appear. */
    public static Map<String, List<ClassInfo>> group(List<ClassInfo> classes) {
        Map<String, List<ClassInfo>> out = new LinkedHashMap<>();
        for (ClassInfo c : classes) {
            out.computeIfAbsent(key(c), k -> new ArrayList<>()).add(c);
        }
        return out;
    }

    public static SubjectStats of(String name, List<SectionInput> inputs) {
        List<Section> sections = new ArrayList<>();
        List<PeriodSummary> everyone = new ArrayList<>();
        int students = 0;
        int lessons = 0;
        int possible = 0;
        int done = 0;
        int idle = 0;
        int attention = 0;
        for (SectionInput in : inputs) {
            ClassStats stats = ClassStats.of(in.lessons(), in.roster(), in.progress());
            sections.add(new Section(in.cls(), stats));
            students += stats.students();
            lessons += stats.lessons();
            possible += stats.lessons() * stats.students();
            idle += stats.noActivity();
            attention += stats.attention().size();
            for (ClassStats.StudentRow r : stats.rows()) {
                everyone.add(r.summary());
                done += r.summary().done();
            }
        }
        List<PeriodPool> periods = new ArrayList<>();
        for (Period p : Period.values()) {
            int periodLessons = 0;
            int periodPossible = 0;
            int periodDone = 0;
            List<PeriodSummary> sums = new ArrayList<>();
            for (SectionInput in : inputs) {
                List<Lesson> inPeriod = Period.filter(in.lessons(), p);
                periodLessons += inPeriod.size();
                for (ProfessorData.Student s : in.roster()) {
                    PeriodSummary ps = PeriodSummary.of(inPeriod, in.progress().getOrDefault(s.id(), Map.of()));
                    sums.add(ps);
                    periodDone += ps.done();
                    periodPossible += inPeriod.size();
                }
            }
            if (periodLessons > 0) {
                periods.add(new PeriodPool(p, periodLessons, periodPossible == 0 ? -1 : Math.round(periodDone * 100f / periodPossible),
                        PeriodSummary.classAverage(sums)));
            }
        }
        return new SubjectStats(name, sections, students, lessons, possible == 0 ? 0 : Math.round(done * 100f / possible),
                PeriodSummary.classAverage(everyone), idle, attention, periods);
    }

    /** The lessons with the lowest average grade over all the sections (those someone completed), at most n. */
    public List<LessonRef> lowestGrade(int n) {
        List<LessonRef> all = new ArrayList<>();
        for (Section s : sections) {
            for (ClassStats.LessonStat l : s.stats().lessonStats()) {
                if (l.averageGrade() >= 0) {
                    all.add(new LessonRef(s.cls(), l));
                }
            }
        }
        all.sort(Comparator.comparingInt((LessonRef r) -> r.stat().averageGrade()).thenComparingInt(r -> r.stat().lesson().number()));
        return all.subList(0, Math.min(n, all.size()));
    }

    /** The section with the highest average grade (null when nobody has a grade yet). */
    public Section bestSection() {
        return sections.stream().filter(s -> s.stats().averageGrade() >= 0)
                .max(Comparator.comparingInt(s -> s.stats().averageGrade())).orElse(null);
    }

    /** Topic counts {right, total} of several sections added together. */
    public static Map<String, int[]> mergeTopics(List<Map<String, int[]>> perSection) {
        Map<String, int[]> out = new TreeMap<>();
        for (Map<String, int[]> m : perSection) {
            m.forEach((topic, c) -> {
                int[] t = out.computeIfAbsent(topic, k -> new int[2]);
                t[0] += c[0];
                t[1] += c[1];
            });
        }
        return out;
    }
}
