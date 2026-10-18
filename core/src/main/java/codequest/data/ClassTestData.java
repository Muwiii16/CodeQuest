package codequest.data;

import com.badlogic.gdx.utils.JsonValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class Test Mode data (D6): a professor opens a lobby on one lesson of a class,
 * students join and click Ready, the professor starts it, everyone plays the same
 * lesson at a fixed difficulty, and scores land on the participants.
 *
 * status: scheduled (lobby open) -> active (playing) -> completed.
 */
public class ClassTestData {

    public record Session(long id, long classId, long stageId, String sectionName, String lessonTitle,
            int lessonNumber, String mode, String status, String startedAt, String endedAt) {
        public boolean isLobby() {
            return "scheduled".equals(status);
        }

        public boolean isActive() {
            return "active".equals(status);
        }

        public boolean isOpen() {
            return isLobby() || isActive();
        }
    }

    public record Participant(long studentId, String username, boolean ready, int score, boolean finished) {
    }

    private static final String SESSION_SELECT = "id,class_id,stage_id,session_mode,status,started_at,ended_at,"
            + "class:classes(section_name),stage:stages(order_index,title)";

    /** The class's lobby or running test, if there is one. */
    public Optional<Session> openSession(long classId) {
        JsonValue rows = SupabaseClient.get("class_test_sessions?class_id=eq." + classId
                + "&status=in.(scheduled,active)&select=" + SESSION_SELECT + "&order=created_at.desc&limit=1");
        return first(rows);
    }

    /** Open sessions for any of the classes (what a student's Class Test screen looks for). */
    public List<Session> openSessions(List<Long> classIds) {
        List<Session> out = new ArrayList<>();
        if (classIds.isEmpty()) {
            return out;
        }
        StringBuilder ids = new StringBuilder();
        for (long id : classIds) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        JsonValue rows = SupabaseClient.get("class_test_sessions?class_id=in.(" + ids
                + ")&status=in.(scheduled,active)&select=" + SESSION_SELECT + "&order=created_at.desc");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.add(sessionFrom(r));
            }
        }
        return out;
    }

    public Optional<Session> session(long sessionId) {
        return first(SupabaseClient.get("class_test_sessions?id=eq." + sessionId + "&select=" + SESSION_SELECT
                + "&limit=1"));
    }

    /** Opens a lobby for the lesson; empty if the class already has one open or the server failed. */
    public Optional<Session> openLobby(long classId, long stageId) {
        if (openSession(classId).isPresent()) {
            return Optional.empty();
        }
        JsonValue inserted = SupabaseClient.post("class_test_sessions", "{\"class_id\":" + classId + ",\"stage_id\":"
                + stageId + ",\"session_mode\":\"competitive\",\"status\":\"scheduled\"}");
        if (inserted == null || inserted.size == 0) {
            return Optional.empty();
        }
        return session(inserted.get(0).getLong("id"));
    }

    public boolean setStatus(long sessionId, String status) {
        String stamp = Instant.now().toString();
        String body = "{\"status\":\"" + status + "\""
                + ("active".equals(status) ? ",\"started_at\":\"" + stamp + "\"" : "")
                + ("completed".equals(status) ? ",\"ended_at\":\"" + stamp + "\"" : "") + "}";
        return SupabaseClient.patch("class_test_sessions?id=eq." + sessionId, body) != null;
    }

    /** Puts the student in the lobby (idempotent). */
    public boolean join(long sessionId, long studentId) {
        JsonValue existing = SupabaseClient.get("class_test_participants?session_id=eq." + sessionId
                + "&student_id=eq." + studentId + "&select=student_id");
        if (existing == null) {
            return false;
        }
        if (existing.size > 0) {
            return true;
        }
        return SupabaseClient.post("class_test_participants",
                "{\"session_id\":" + sessionId + ",\"student_id\":" + studentId + "}") != null;
    }

    public boolean setReady(long sessionId, long studentId, boolean ready) {
        return SupabaseClient.patch("class_test_participants?session_id=eq." + sessionId + "&student_id=eq."
                + studentId, "{\"ready\":" + ready + "}") != null;
    }

    /** Updates the running score while the student is still playing (so the live standings move). */
    public boolean submitProgress(long sessionId, long studentId, int score) {
        return SupabaseClient.patch("class_test_participants?session_id=eq." + sessionId + "&student_id=eq."
                + studentId, "{\"score\":" + score + "}") != null;
    }

    /** Stores the finished run's points and finish time. */
    public boolean submitResult(long sessionId, long studentId, int score) {
        return SupabaseClient.patch("class_test_participants?session_id=eq." + sessionId + "&student_id=eq."
                + studentId, "{\"score\":" + score + ",\"finished_at\":\"" + Instant.now() + "\"}") != null;
    }

    /** Everyone who joined, best score first (ties: whoever finished earlier). */
    public List<Participant> participants(long sessionId) {
        List<Participant> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("class_test_participants?session_id=eq." + sessionId
                + "&select=student_id,ready,score,finished_at,student:profiles(username)&order=joined_at");
        if (rows == null) {
            return out;
        }
        List<String> finishedAt = new ArrayList<>();
        for (JsonValue r : rows) {
            JsonValue s = r.get("student");
            out.add(new Participant(r.getLong("student_id"), s != null ? s.getString("username", "?") : "?",
                    r.getBoolean("ready", false), r.getInt("score", 0), r.getString("finished_at", null) != null));
            finishedAt.add(r.getString("finished_at", "9999"));
        }
        // Sort by score desc, then finish time asc, keeping the two lists in step.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            int byScore = Integer.compare(out.get(b).score(), out.get(a).score());
            return byScore != 0 ? byScore : finishedAt.get(a).compareTo(finishedAt.get(b));
        });
        List<Participant> sorted = new ArrayList<>();
        for (int i : order) {
            sorted.add(out.get(i));
        }
        return sorted;
    }

    /** Finished tests of a class, newest first. */
    public List<Session> history(long classId, int limit) {
        List<Session> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("class_test_sessions?class_id=eq." + classId + "&status=eq.completed&started_at=not.is.null&select="
                + SESSION_SELECT + "&order=ended_at.desc.nullslast&limit=" + limit);
        if (rows != null) {
            for (JsonValue r : rows) {
                out.add(sessionFrom(r));
            }
        }
        return out;
    }

    /**
     * Closes a running test if everyone who joined has finished or the time limit has passed.
     * Safe to call from any screen watching the test; the result is the (possibly new) status.
     */
    public String endIfOver(Session session, List<Participant> participants) {
        if (!session.isActive()) {
            return session.status();
        }
        java.time.Instant started = null;
        try {
            started = session.startedAt() == null ? null : java.time.Instant.parse(session.startedAt());
        } catch (RuntimeException ignored) {
            // an unreadable start time just means the time limit cannot apply
        }
        int finished = (int) participants.stream().filter(Participant::finished).count();
        if (codequest.session.TestRules.shouldEnd(session.status(), participants.size(), finished, started,
                java.time.Instant.now())) {
            setStatus(session.id(), "completed");
            return "completed";
        }
        return session.status();
    }

    private static Optional<Session> first(JsonValue rows) {
        return rows == null || rows.size == 0 ? Optional.empty() : Optional.of(sessionFrom(rows.get(0)));
    }

    private static Session sessionFrom(JsonValue r) {
        JsonValue cls = r.get("class");
        JsonValue stage = r.get("stage");
        return new Session(r.getLong("id"), r.getLong("class_id"), r.getLong("stage_id", 0),
                cls != null ? cls.getString("section_name", "?") : "?",
                stage != null ? stage.getString("title", "Lesson") : "Lesson",
                stage != null ? stage.getInt("order_index", 0) : 0,
                r.getString("session_mode", "competitive"), r.getString("status", "scheduled"),
                r.getString("started_at", null), r.getString("ended_at", null));
    }
}
