package codequest.data;

import codequest.content.DefaultStages;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.session.Grades;
import codequest.session.RankCalculator;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a student has earned. A lesson they completed gives a badge (proof of accomplishment to show the professor; it is read
 * from their progress, nothing extra is stored). Finishing every lesson of a subject gives that subject's certificate, which
 * only the server can issue (it checks the completion itself).
 */
public class AwardsData {

    private final DatabaseManager db = new DatabaseManager();
    private final CampaignData campaigns = new CampaignData();

    /** A completed lesson. */
    public record Badge(long stageId, int lessonNumber, String lessonTitle, Period period, String rank, int gradePercent,
            String earnedAt, String subject, String section) {
    }

    /** The certificate of a finished subject. */
    public record Certificate(long id, String rank, double percent, String issuedAt) {
    }

    /** One grading period of a subject: how many of its lessons are finished, and its certificate once all are. */
    public record PeriodAward(Period period, int done, int total, Certificate certificate) {
    }

    /**
     * A subject (class) the student is in: how many lessons are finished, the certificate once all are (the grand one), and
     * each grading period that has lessons with its own certificate.
     */
    public record Subject(long classId, String name, String courseCode, String section, int done, int total,
            Certificate certificate, List<PeriodAward> periods) {
        public boolean finished() {
            return total > 0 && done >= total;
        }

        /** The award of one period, or null when the subject has no lessons in it. */
        public PeriodAward award(Period period) {
            for (PeriodAward a : periods) {
                if (a.period() == period) {
                    return a;
                }
            }
            return null;
        }
    }

    /** The student's badges, newest first. */
    public List<Badge> badges(String username) {
        List<Badge> out = new ArrayList<>();
        long studentId = db.profileId(username);
        if (studentId < 0) {
            return out;
        }
        JsonValue rows = SupabaseClient.get("campaign_progress?student_id=eq." + studentId + "&status=eq.completed"
                + "&select=stage_id,rank,score,max_score,last_played_at,stage:stages(title,order_index,period,"
                + "campaign:campaigns(kind,class:classes(section_name,subject,course_code)))&order=last_played_at.desc");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue stage = r.get("stage");
            JsonValue campaign = stage == null ? null : stage.get("campaign");
            if (stage == null || campaign == null || !"lesson".equals(campaign.getString("kind", "lesson"))) {
                continue;
            }
            JsonValue cls = campaign.get("class");
            int number = stage.getInt("order_index", 1);
            int max = r.getInt("max_score", 0) > 0 ? r.getInt("max_score", 0)
                    : Grades.maxScore(DefaultStages.forNumber(DefaultStages.layoutFor(number)).towerCount());
            String subject = cls == null ? "CodeQuest"
                    : nonBlank(cls.getString("subject", null), cls.getString("course_code", "CodeQuest"));
            out.add(new Badge(r.getLong("stage_id"), number, stage.getString("title", "Lesson"),
                    Period.fromKey(stage.getString("period", null)), r.getString("rank", "-"),
                    Grades.percent(r.getInt("score", 0), max), r.getString("last_played_at", ""), subject,
                    cls == null ? "" : cls.getString("section_name", "")));
        }
        return out;
    }

    /**
     * The student's subjects with their progress. A subject whose lessons are all completed gets its certificate (issued on
     * the server now if it was not yet).
     */
    public List<Subject> subjects(String username) {
        List<Subject> out = new ArrayList<>();
        long studentId = db.profileId(username);
        if (studentId < 0) {
            return out;
        }
        Map<Long, Progress> progress = campaigns.progress(studentId);
        for (ClassInfo c : campaigns.studentClasses(studentId)) {
            out.add(standing(c, campaigns.lessons(c.id()), progress));
        }
        return out;
    }

    /** The subject a lesson belongs to, after the student played it (empty for the built-in lessons, which have no subject). */
    public Optional<Subject> subjectOfLesson(String username, Lesson lesson) {
        long studentId = db.profileId(username);
        if (studentId < 0) {
            return Optional.empty();
        }
        JsonValue rows = SupabaseClient.get("campaigns?id=eq." + lesson.campaignId() + "&select=class_id&limit=1");
        if (rows == null || rows.size == 0 || rows.get(0).get("class_id") == null || rows.get(0).get("class_id").isNull()) {
            return Optional.empty();
        }
        long classId = rows.get(0).getLong("class_id");
        for (ClassInfo c : campaigns.studentClasses(studentId)) {
            if (c.id() == classId) {
                return Optional.of(standing(c, campaigns.lessons(classId), campaigns.progress(studentId)));
            }
        }
        return Optional.empty();
    }

    private Subject standing(ClassInfo c, List<Lesson> lessons, Map<Long, Progress> progress) {
        PeriodSummary sum = PeriodSummary.of(lessons, progress);
        Certificate cert = null;
        if (sum.total() > 0 && sum.done() == sum.total()) {
            cert = issue(c.id(), null, sum.averageGrade());
        }
        List<PeriodAward> periods = new ArrayList<>();
        for (Period p : Period.values()) {
            List<Lesson> inPeriod = Period.filter(lessons, p);
            if (inPeriod.isEmpty()) {
                continue;
            }
            PeriodSummary ps = PeriodSummary.of(inPeriod, progress);
            Certificate pc = ps.done() == ps.total() ? issue(c.id(), p, ps.averageGrade()) : null;
            periods.add(new PeriodAward(p, ps.done(), ps.total(), pc));
        }
        return new Subject(c.id(), c.subjectName(), c.courseCode(), c.sectionName(), sum.done(), sum.total(), cert, periods);
    }

    /**
     * Asks the server for a certificate: the one of a grading period, or (period null) the one of the whole subject. It
     * answers only when it sees every lesson concerned completed.
     */
    private Certificate issue(long classId, Period period, int averageGrade) {
        String rank = RankCalculator.rankFor(Math.max(0, averageGrade) / 100.0).name();
        String common = "\"p_class_id\":" + classId + ",\"p_rank\":\"" + rank + "\",\"p_percent\":" + Math.max(0, averageGrade);
        JsonValue r = period == null
                ? SupabaseClient.post("rpc/issue_subject_certificate", "{" + common + "}")
                : SupabaseClient.post("rpc/issue_period_certificate", "{" + common + ",\"p_period\":\"" + period.key() + "\"}");
        if (r == null || !r.isObject() || !"ok".equals(r.getString("status", ""))) {
            return null;
        }
        return new Certificate(r.getLong("id"), r.getString("rank", rank), r.getDouble("score_percentage", averageGrade),
                r.getString("issued_at", ""));
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
