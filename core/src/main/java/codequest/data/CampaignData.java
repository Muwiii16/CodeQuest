package codequest.data;

import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Classes, lessons and progress as the student and professor screens need them.
 * A class owns one campaign; its stages are the lessons. A student who is in no
 * class plays the built-in default campaign (the one with no class).
 */
public class CampaignData {

    /** A class (section) a professor handles and students belong to. */
    public record ClassInfo(long id, String courseCode, String sectionName, String language, int studentCount,
            int lessonCount, String subject, String term, boolean ended) {

        /** A class with no subject name or term yet. */
        public ClassInfo(long id, String courseCode, String sectionName, String language, int studentCount, int lessonCount) {
            this(id, courseCode, sectionName, language, studentCount, lessonCount, null, null, false);
        }

        /** The subject's name, or the course code when none was given. */
        public String subjectName() {
            return subject != null && !subject.isBlank() ? subject : courseCode;
        }

        public String termLabel() {
            return term == null ? "" : term;
        }
    }

    /**
     * Which of the student's classes to open first: the one asked for, else the newest class that is still running
     * (a student who has moved on to a new subject lands there), else the newest ended one. The list is oldest
     * enrollment first, as {@link #studentClasses} returns it.
     */
    public static int defaultClassIndex(List<ClassInfo> classes, Long preferredId) {
        if (preferredId != null) {
            for (int i = 0; i < classes.size(); i++) {
                if (classes.get(i).id() == preferredId) {
                    return i;
                }
            }
        }
        for (int i = classes.size() - 1; i >= 0; i--) {
            if (!classes.get(i).ended()) {
                return i;
            }
        }
        return Math.max(0, classes.size() - 1);
    }

    /** A student's best result on one lesson. */
    public record Progress(String status, int score, String rank, int attempts, String lastPlayed) {
        public boolean completed() {
            return "completed".equals(status);
        }
    }

    /** Classes the student is enrolled in, oldest enrollment first. */
    public List<ClassInfo> studentClasses(long studentId) {
        List<ClassInfo> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("class_enrollments?student_id=eq." + studentId
                + "&select=class:classes(id,course_code,section_name,language,subject,term,ended)&order=enrolled_at");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue c = r.get("class");
            if (c != null) {
                out.add(new ClassInfo(c.getLong("id"), c.getString("course_code", "CC102"),
                        c.getString("section_name", "?"), c.getString("language", "C"), 0, 0,
                        c.getString("subject", null), c.getString("term", null), c.getBoolean("ended", false)));
            }
        }
        return out;
    }

    /** Lessons of a class's campaign in order; a null classId means the built-in default campaign. */
    public List<Lesson> lessons(Long classId) {
        return stagesOf(classId, "lesson");
    }

    /** The stages a professor made only for class tests (students never see them in the campaign), in order. */
    public List<Lesson> tests(long classId) {
        return stagesOf(classId, "test");
    }

    private List<Lesson> stagesOf(Long classId, String kind) {
        List<Lesson> out = new ArrayList<>();
        String filter = classId == null ? "campaign.class_id=is.null" : "campaign.class_id=eq." + classId;
        JsonValue rows = SupabaseClient.get("stages?select=id,campaign_id,order_index,title,base_difficulty,"
                + "adaptive_enabled,prompt,program,topics,outputs,campus_background,campaign:campaigns!inner(class_id,kind)&" + filter
                + "&campaign.kind=eq." + kind + "&order=order_index");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            out.add(stageFrom(r, out.size() + 1));
        }
        return out;
    }

    private static Lesson stageFrom(JsonValue r, int fallbackNumber) {
        JsonValue campaign = r.get("campaign");
        boolean test = campaign != null && "test".equals(campaign.getString("kind", "lesson"));
        return new Lesson(r.getLong("id"), r.getLong("campaign_id"), r.getInt("order_index", fallbackNumber),
                r.getString("title", test ? "Class test" : "Lesson"), r.getString("base_difficulty", "normal"),
                r.getBoolean("adaptive_enabled", true), r.getString("prompt", null), r.getString("program", null),
                r.getString("topics", null), r.getString("outputs", null), test,
                r.getString("campus_background", null));
    }

    /** The student's progress rows keyed by stage id. */
    public Map<Long, Progress> progress(long studentId) {
        Map<Long, Progress> out = new HashMap<>();
        JsonValue rows = SupabaseClient.get("campaign_progress?student_id=eq." + studentId
                + "&select=stage_id,status,score,rank,attempts,last_played_at");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.put(r.getLong("stage_id"), new Progress(r.getString("status", "locked"), r.getInt("score", 0),
                        r.getString("rank", null), r.getInt("attempts", 0), r.getString("last_played_at", null)));
            }
        }
        return out;
    }

    /** Lesson n is playable when it is the first one or the lesson before it has been completed. */
    public static boolean isUnlocked(List<Lesson> lessons, Map<Long, Progress> progress, int index) {
        if (index <= 0) {
            return true;
        }
        Progress previous = progress.get(lessons.get(index - 1).stageId());
        return previous != null && previous.completed();
    }

    public Optional<Lesson> lesson(long stageId) {
        JsonValue rows = SupabaseClient.get("stages?id=eq." + stageId
                + "&select=id,campaign_id,order_index,title,base_difficulty,adaptive_enabled,prompt,program,topics,outputs,campus_background,"
                + "campaign:campaigns(kind)&limit=1");
        if (rows == null || rows.size == 0) {
            return Optional.empty();
        }
        return Optional.of(stageFrom(rows.get(0), 1));
    }

    /** Adds a lesson at the end of the class's campaign, creating the campaign if the class has none yet. */
    public Optional<Lesson> createLesson(long classId, String sectionName, String title, String difficulty,
            boolean adaptive, String prompt) {
        return createLesson(classId, sectionName, title, difficulty, adaptive, prompt, java.util.Set.of());
    }

    /** Same, with the topics the lesson teaches (it may only use these and the topics of the lessons before it). */
    public Optional<Lesson> createLesson(long classId, String sectionName, String title, String difficulty,
            boolean adaptive, String prompt, java.util.Set<codequest.content.CTopic> topics) {
        return createStage(classId, sectionName, title, difficulty, adaptive, prompt, topics, false);
    }

    /**
     * Same, and the stage is either a lesson of the campaign or (test = true) a stage made only for class tests, kept in
     * the class's separate test campaign so that it never shows to students in the campaign.
     */
    public Optional<Lesson> createStage(long classId, String sectionName, String title, String difficulty,
            boolean adaptive, String prompt, java.util.Set<codequest.content.CTopic> topics, boolean test) {
        return createStage(classId, sectionName, title, difficulty, adaptive, prompt, topics, test,
                codequest.game.MapTheme.CAMPUS);
    }

    /** Same, with how the stage looks (the campus map or a school room), saved in campus_background. */
    public Optional<Lesson> createStage(long classId, String sectionName, String title, String difficulty,
            boolean adaptive, String prompt, java.util.Set<codequest.content.CTopic> topics, boolean test,
            codequest.game.MapTheme theme) {
        Long campaignId = campaignIdFor(classId, sectionName, test ? "test" : "lesson");
        if (campaignId == null) {
            return Optional.empty();
        }
        JsonValue last = SupabaseClient.get("stages?campaign_id=eq." + campaignId
                + "&select=order_index&order=order_index.desc&limit=1");
        if (last == null) {
            return Optional.empty();
        }
        int next = last.size > 0 ? last.get(0).getInt("order_index", 0) + 1 : 1;
        String body = "{\"campaign_id\":" + campaignId + ",\"order_index\":" + next + ",\"title\":\"" + escape(title)
                + "\",\"base_difficulty\":\"" + escape(difficulty) + "\",\"adaptive_enabled\":" + adaptive
                + (prompt == null || prompt.isBlank() ? "" : ",\"prompt\":\"" + escape(prompt) + "\"")
                + (topics.isEmpty() ? "" : ",\"topics\":\"" + codequest.content.ScopeCheck.formatTopics(topics) + "\"")
                + (theme.isSchool() ? ",\"campus_background\":\"" + theme.key() + "\"" : "") + "}";
        JsonValue inserted = SupabaseClient.post("stages", body);
        if (inserted == null || inserted.size == 0) {
            return Optional.empty();
        }
        JsonValue r = inserted.get(0);
        Lesson created = new Lesson(r.getLong("id"), campaignId, next, title, difficulty, adaptive, prompt, null, null, null, test,
                theme.isSchool() ? theme.key() : null);
        return Optional.of(created.withTopics(topics));
    }

    /** Changes a lesson's title, starting difficulty, adaptive flag and prompt. */
    public boolean updateLesson(long stageId, String title, String difficulty, boolean adaptive, String prompt) {
        String body = "{\"title\":\"" + escape(title) + "\",\"base_difficulty\":\"" + escape(difficulty)
                + "\",\"adaptive_enabled\":" + adaptive + ",\"prompt\":"
                + (prompt == null || prompt.isBlank() ? "null" : "\"" + escape(prompt) + "\"") + "}";
        return SupabaseClient.patch("stages?id=eq." + stageId, body) != null;
    }

    /** Saves the full program a lesson builds (blank removes it), with what each of its programs prints. */
    public boolean updateProgram(long stageId, String program, String outputs) {
        String body = "{\"program\":" + (program == null || program.isBlank() ? "null" : "\"" + escape(program) + "\"")
                + ",\"outputs\":" + (outputs == null || outputs.isBlank() ? "null" : "\"" + escape(outputs) + "\"") + "}";
        return SupabaseClient.patch("stages?id=eq." + stageId, body) != null;
    }

    /** Saves just the program, leaving the outputs as they are. */
    public boolean updateProgram(long stageId, String program) {
        String body = "{\"program\":" + (program == null || program.isBlank() ? "null" : "\"" + escape(program) + "\"") + "}";
        return SupabaseClient.patch("stages?id=eq." + stageId, body) != null;
    }

    /**
     * Deletes a lesson with its questions, student progress and certificate links, then renumbers the
     * lessons after it so the numbers (and the rewards tied to them) stay 1, 2, 3...
     */
    public boolean deleteLesson(Lesson lesson) {
        if (!SupabaseClient.delete("stages?id=eq." + lesson.stageId())) {
            return false;
        }
        JsonValue rest = SupabaseClient.get("stages?campaign_id=eq." + lesson.campaignId()
                + "&select=id,order_index&order=order_index");
        if (rest == null) {
            return true;
        }
        int number = 1;
        for (JsonValue r : rest) {
            if (r.getInt("order_index", number) != number) {
                SupabaseClient.patch("stages?id=eq." + r.getLong("id"), "{\"order_index\":" + number + "}");
            }
            number++;
        }
        return true;
    }

    /** The class's campaign id, creating the campaign on first use. */
    Long campaignIdFor(long classId, String sectionName) {
        return campaignIdFor(classId, sectionName, "lesson");
    }

    /** The class's campaign of this kind ("lesson" or "test"), creating it on first use. */
    Long campaignIdFor(long classId, String sectionName, String kind) {
        JsonValue existing = SupabaseClient.get("campaigns?class_id=eq." + classId + "&kind=eq." + kind + "&select=id&limit=1");
        if (existing == null) {
            return null;
        }
        if (existing.size > 0) {
            return existing.get(0).getLong("id");
        }
        JsonValue created = SupabaseClient.post("campaigns",
                "{\"class_id\":" + classId + ",\"kind\":\"" + kind + "\",\"title\":\""
                        + escape(sectionName + ("test".equals(kind) ? " class tests" : " lessons")) + "\"}");
        return created != null && created.size > 0 ? created.get(0).getLong("id") : null;
    }

    static String escape(String s) {
        return SupabaseClient.jsonEscape(s);
    }
}
