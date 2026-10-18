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
            int lessonCount) {
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
                + "&select=class:classes(id,course_code,section_name,language)&order=enrolled_at");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue c = r.get("class");
            if (c != null) {
                out.add(new ClassInfo(c.getLong("id"), c.getString("course_code", "CC102"),
                        c.getString("section_name", "?"), c.getString("language", "C"), 0, 0));
            }
        }
        return out;
    }

    /** Lessons of a class's campaign in order; a null classId means the built-in default campaign. */
    public List<Lesson> lessons(Long classId) {
        List<Lesson> out = new ArrayList<>();
        String filter = classId == null ? "campaign.class_id=is.null" : "campaign.class_id=eq." + classId;
        JsonValue rows = SupabaseClient.get("stages?select=id,campaign_id,order_index,title,base_difficulty,"
                + "adaptive_enabled,prompt,campaign:campaigns!inner(class_id)&" + filter + "&order=order_index");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            out.add(new Lesson(r.getLong("id"), r.getLong("campaign_id"), r.getInt("order_index", out.size() + 1),
                    r.getString("title", "Lesson"), r.getString("base_difficulty", "normal"),
                    r.getBoolean("adaptive_enabled", true), r.getString("prompt", null)));
        }
        return out;
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
                + "&select=id,campaign_id,order_index,title,base_difficulty,adaptive_enabled,prompt&limit=1");
        if (rows == null || rows.size == 0) {
            return Optional.empty();
        }
        JsonValue r = rows.get(0);
        return Optional.of(new Lesson(r.getLong("id"), r.getLong("campaign_id"), r.getInt("order_index", 1),
                r.getString("title", "Lesson"), r.getString("base_difficulty", "normal"),
                r.getBoolean("adaptive_enabled", true), r.getString("prompt", null)));
    }

    /** Adds a lesson at the end of the class's campaign, creating the campaign if the class has none yet. */
    public Optional<Lesson> createLesson(long classId, String sectionName, String title, String difficulty,
            boolean adaptive, String prompt) {
        Long campaignId = campaignIdFor(classId, sectionName);
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
                + (prompt == null || prompt.isBlank() ? "" : ",\"prompt\":\"" + escape(prompt) + "\"") + "}";
        JsonValue inserted = SupabaseClient.post("stages", body);
        if (inserted == null || inserted.size == 0) {
            return Optional.empty();
        }
        JsonValue r = inserted.get(0);
        return Optional.of(new Lesson(r.getLong("id"), campaignId, next, title, difficulty, adaptive, prompt));
    }

    /** Changes a lesson's title, starting difficulty, adaptive flag and prompt. */
    public boolean updateLesson(long stageId, String title, String difficulty, boolean adaptive, String prompt) {
        String body = "{\"title\":\"" + escape(title) + "\",\"base_difficulty\":\"" + escape(difficulty)
                + "\",\"adaptive_enabled\":" + adaptive + ",\"prompt\":"
                + (prompt == null || prompt.isBlank() ? "null" : "\"" + escape(prompt) + "\"") + "}";
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
        JsonValue existing = SupabaseClient.get("campaigns?class_id=eq." + classId + "&select=id&limit=1");
        if (existing == null) {
            return null;
        }
        if (existing.size > 0) {
            return existing.get(0).getLong("id");
        }
        JsonValue created = SupabaseClient.post("campaigns",
                "{\"class_id\":" + classId + ",\"title\":\"" + escape(sectionName + " lessons") + "\"}");
        return created != null && created.size > 0 ? created.get(0).getLong("id") : null;
    }

    static String escape(String s) {
        return SupabaseClient.jsonEscape(s);
    }
}
