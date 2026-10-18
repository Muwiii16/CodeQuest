package codequest.data;

import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Professor-side reads and writes (DFD 2.0 and the progress views): their
 * classes, enrollment, and the per-lesson and whole-class performance tables.
 * Nothing is cached because a professor expects to see their own changes at once.
 */
public class ProfessorData {

    public record Student(long id, String username, String fullName) {
    }

    /** One student's result on one lesson, with their best class-test result for it if they took one. */
    public record LessonRow(Student student, Progress progress, Integer classTestScore) {
    }

    public enum EnrollResult {
        ENROLLED, NO_SUCH_STUDENT, NOT_A_STUDENT, ALREADY_ENROLLED, ERROR
    }

    public record CreatedClass(long classId, List<String> enrolled, List<String> skipped) {
    }

    private final DatabaseManager db = new DatabaseManager();
    private final CampaignData campaigns = new CampaignData();

    /** "student" or "professor"; empty when the account doesn't exist. */
    public String getRole(String username) {
        JsonValue rows = SupabaseClient.get("profiles?username=eq." + SupabaseClient.encode(username)
                + "&select=role&limit=1");
        if (rows == null || rows.size == 0) {
            return "";
        }
        return rows.get(0).getString("role", "student");
    }

    public long professorId(String username) {
        return db.profileId(username);
    }

    // ── Classes & enrollment ─────────────────────────────

    public List<ClassInfo> listClasses(long professorId) {
        List<ClassInfo> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("classes?professor_id=eq." + professorId
                + "&select=id,course_code,section_name,language,class_enrollments(count),campaigns(stages(count))"
                + "&order=created_at");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            int lessons = 0;
            JsonValue camp = r.get("campaigns");
            if (camp != null && camp.size > 0) {
                JsonValue stages = camp.get(0).get("stages");
                lessons = stages != null && stages.size > 0 ? stages.get(0).getInt("count", 0) : 0;
            }
            JsonValue enrolled = r.get("class_enrollments");
            out.add(new ClassInfo(r.getLong("id"), r.getString("course_code", "CC102"),
                    r.getString("section_name", "?"), r.getString("language", "C"),
                    enrolled != null && enrolled.size > 0 ? enrolled.get(0).getInt("count", 0) : 0, lessons));
        }
        return out;
    }

    /**
     * Creates the class and its (empty) campaign, then enrolls the given usernames.
     * Usernames that aren't student accounts are skipped and reported back.
     */
    public Optional<CreatedClass> createClass(long professorId, String sectionName, String courseCode,
            String language, List<String> studentUsernames) {
        String body = "{\"professor_id\":" + professorId + ",\"section_name\":\"" + CampaignData.escape(sectionName)
                + "\",\"course_code\":\"" + CampaignData.escape(courseCode) + "\",\"language\":\""
                + CampaignData.escape(language) + "\"}";
        JsonValue inserted = SupabaseClient.post("classes", body);
        if (inserted == null || inserted.size == 0) {
            return Optional.empty();
        }
        long classId = inserted.get(0).getLong("id");
        campaigns.campaignIdFor(classId, sectionName);

        List<String> enrolled = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String name : studentUsernames) {
            EnrollResult r = enrollStudent(classId, name);
            (r == EnrollResult.ENROLLED || r == EnrollResult.ALREADY_ENROLLED ? enrolled : skipped).add(name);
        }
        return Optional.of(new CreatedClass(classId, enrolled, skipped));
    }

    /** Renames a class (section name and course code). */
    public boolean updateClass(long classId, String sectionName, String courseCode) {
        return SupabaseClient.patch("classes?id=eq." + classId, "{\"section_name\":\"" + CampaignData.escape(sectionName)
                + "\",\"course_code\":\"" + CampaignData.escape(courseCode) + "\"}") != null;
    }

    /** Deletes a class with its lessons, questions, enrollments, class tests and student results for it. */
    public boolean deleteClass(long classId) {
        return SupabaseClient.delete("classes?id=eq." + classId);
    }

    /** Checks an account exists and is a student, without enrolling it anywhere. */
    public EnrollResult checkStudent(String username) {
        JsonValue found = SupabaseClient.get("profiles?username=eq." + SupabaseClient.encode(username)
                + "&select=id,role&limit=1");
        if (found == null) {
            return EnrollResult.ERROR;
        }
        if (found.size == 0) {
            return EnrollResult.NO_SUCH_STUDENT;
        }
        return "student".equals(found.get(0).getString("role", "student")) ? EnrollResult.ENROLLED
                : EnrollResult.NOT_A_STUDENT;
    }

    public EnrollResult enrollStudent(long classId, String studentUsername) {
        JsonValue found = SupabaseClient.get("profiles?username=eq." + SupabaseClient.encode(studentUsername)
                + "&select=id,role&limit=1");
        if (found == null) {
            return EnrollResult.ERROR;
        }
        if (found.size == 0) {
            return EnrollResult.NO_SUCH_STUDENT;
        }
        if (!"student".equals(found.get(0).getString("role", "student"))) {
            return EnrollResult.NOT_A_STUDENT;
        }
        long studentId = found.get(0).getLong("id");
        JsonValue existing = SupabaseClient.get("class_enrollments?class_id=eq." + classId + "&student_id=eq."
                + studentId + "&select=student_id");
        if (existing != null && existing.size > 0) {
            return EnrollResult.ALREADY_ENROLLED;
        }
        String body = "{\"class_id\":" + classId + ",\"student_id\":" + studentId + "}";
        return SupabaseClient.post("class_enrollments", body) != null ? EnrollResult.ENROLLED : EnrollResult.ERROR;
    }

    /** Removes a student from the class (their progress and certificates are kept). */
    public boolean dropStudent(long classId, long studentId) {
        return SupabaseClient.delete("class_enrollments?class_id=eq." + classId + "&student_id=eq." + studentId);
    }

    public List<Student> roster(long classId) {
        List<Student> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("class_enrollments?class_id=eq." + classId
                + "&select=student:profiles(id,username,full_name)&order=enrolled_at");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue s = r.get("student");
            if (s != null) {
                out.add(new Student(s.getLong("id"), s.getString("username", "?"), s.getString("full_name", null)));
            }
        }
        out.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
        return out;
    }

    // ── Performance ──────────────────────────────────────

    /** Everyone in the class with their result on one lesson. */
    public List<LessonRow> lessonAnalysis(long classId, long stageId) {
        List<Student> students = roster(classId);
        Map<Long, Progress> progress = new HashMap<>();
        Map<Long, Integer> testBest = new HashMap<>();
        if (!students.isEmpty()) {
            String ids = idList(students);
            JsonValue rows = SupabaseClient.get("campaign_progress?stage_id=eq." + stageId + "&student_id=in.(" + ids
                    + ")&select=student_id,status,score,rank,attempts,last_played_at");
            if (rows != null) {
                for (JsonValue r : rows) {
                    progress.put(r.getLong("student_id"), new Progress(r.getString("status", "locked"),
                            r.getInt("score", 0), r.getString("rank", null), r.getInt("attempts", 0),
                            r.getString("last_played_at", null)));
                }
            }
            JsonValue tests = SupabaseClient.get("class_test_participants?student_id=in.(" + ids
                    + ")&finished_at=not.is.null&select=student_id,score,session:class_test_sessions!inner(stage_id,class_id)"
                    + "&session.stage_id=eq." + stageId + "&session.class_id=eq." + classId);
            if (tests != null) {
                for (JsonValue t : tests) {
                    testBest.merge(t.getLong("student_id"), t.getInt("score", 0), Math::max);
                }
            }
        }
        List<LessonRow> out = new ArrayList<>();
        for (Student s : students) {
            out.add(new LessonRow(s, progress.get(s.id()), testBest.get(s.id())));
        }
        return out;
    }

    /** Each student's progress on each of the given lessons (key: student id, then stage id). */
    public Map<Long, Map<Long, Progress>> overallProgress(List<Student> students, List<Lesson> lessons) {
        Map<Long, Map<Long, Progress>> out = new HashMap<>();
        if (students.isEmpty() || lessons.isEmpty()) {
            return out;
        }
        StringBuilder stageIds = new StringBuilder();
        for (Lesson l : lessons) {
            stageIds.append(stageIds.length() == 0 ? "" : ",").append(l.stageId());
        }
        JsonValue rows = SupabaseClient.get("campaign_progress?student_id=in.(" + idList(students) + ")&stage_id=in.("
                + stageIds + ")&select=student_id,stage_id,status,score,rank,attempts,last_played_at");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.computeIfAbsent(r.getLong("student_id"), k -> new HashMap<>()).put(r.getLong("stage_id"),
                        new Progress(r.getString("status", "locked"), r.getInt("score", 0), r.getString("rank", null),
                                r.getInt("attempts", 0), r.getString("last_played_at", null)));
            }
        }
        return out;
    }

    /** Per topic: {answers that were right, answers in total} for a lesson, from the answer log of these students. */
    public Map<String, int[]> topicAccuracy(long classId, long stageId) {
        Map<String, int[]> out = new java.util.TreeMap<>();
        List<Student> students = roster(classId);
        if (students.isEmpty()) {
            return out;
        }
        JsonValue rows = SupabaseClient.get("submissions?stage_id=eq." + stageId + "&student_id=in.(" + idList(students)
                + ")&select=topic,is_correct&limit=5000");
        if (rows != null) {
            for (JsonValue r : rows) {
                String topic = r.getString("topic", null);
                if (topic == null) {
                    continue;
                }
                int[] c = out.computeIfAbsent(topic, k -> new int[2]);
                c[1]++;
                if (r.getBoolean("is_correct", false)) {
                    c[0]++;
                }
            }
        }
        return out;
    }

    private static String idList(List<Student> students) {
        StringBuilder ids = new StringBuilder();
        for (Student s : students) {
            ids.append(ids.length() == 0 ? "" : ",").append(s.id());
        }
        return ids.toString();
    }
}
