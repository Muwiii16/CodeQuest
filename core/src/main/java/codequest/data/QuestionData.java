package codequest.data;

import codequest.content.CTopic;
import codequest.content.Difficulty;
import codequest.content.QuestionDraft;
import codequest.content.StaticChallengeProvider;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The questions of a lesson as stored in D4 (challenges and their hints), for the professor's
 * question editor: list, add, rewrite and delete. The game picks a random question matching each
 * tower's topic and difficulty, so a lesson can hold several questions per topic.
 */
public class QuestionData {

    /** A stored question; {@code id} is the challenges row id. */
    public record Question(long id, QuestionDraft draft) {
    }

    /** Questions of a lesson, grouped as they were added. */
    public List<Question> list(long stageId) {
        List<Question> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("challenges?stage_id=eq." + stageId
                + "&select=id,topic,difficulty,prompt,correct_token,distractors,hints(hint_text,hint_order)"
                + "&order=id");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            try {
                CTopic topic = CTopic.valueOf(r.getString("topic", "").toUpperCase());
                Difficulty difficulty = Difficulty.valueOf(r.getString("difficulty", "").toUpperCase());
                String prompt = r.getString("prompt", null);
                String correct = r.getString("correct_token", null);
                if (prompt == null || correct == null) {
                    continue;
                }
                List<String> wrong = new ArrayList<>();
                JsonValue d = r.get("distractors");
                if (d != null) {
                    for (JsonValue x : d) {
                        wrong.add(x.asString());
                    }
                }
                String hint = null;
                int best = Integer.MAX_VALUE;
                JsonValue hints = r.get("hints");
                if (hints != null) {
                    for (JsonValue h : hints) {
                        int order = h.getInt("hint_order", Integer.MAX_VALUE);
                        if (order < best) {
                            best = order;
                            hint = h.getString("hint_text", null);
                        }
                    }
                }
                out.add(new Question(r.getLong("id"), new QuestionDraft(topic, difficulty, prompt, correct, wrong, hint)));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed question row " + r.getString("id", "?") + ": " + e);
            }
        }
        return out;
    }

    /** Saves a new question at the end of the lesson. Empty if the server refused it. */
    public boolean add(long stageId, QuestionDraft draft) {
        QuestionDraft q = draft.cleaned();
        JsonValue last = SupabaseClient.get("challenges?stage_id=eq." + stageId
                + "&select=sequence_index&order=sequence_index.desc&limit=1");
        if (last == null) {
            return false;
        }
        int next = last.size > 0 ? last.get(0).getInt("sequence_index", 0) + 1 : 1;
        String body = "{\"stage_id\":" + stageId + ",\"sequence_index\":" + next + ",\"section_index\":1"
                + ",\"language\":\"C\",\"ai_generated\":false" + fields(q) + "}";
        JsonValue inserted = SupabaseClient.post("challenges", body);
        if (inserted == null || inserted.size == 0) {
            return false;
        }
        long id = inserted.get(0).getLong("id");
        return q.hint() == null || SupabaseClient.post("hints", "{\"challenge_id\":" + id
                + ",\"hint_order\":1,\"generated_by\":\"manual\",\"hint_text\":\"" + esc(q.hint()) + "\"}") != null;
    }

    /** Rewrites a question: topic, difficulty, text, answers and hint. */
    public boolean update(long questionId, QuestionDraft draft) {
        QuestionDraft q = draft.cleaned();
        if (SupabaseClient.patch("challenges?id=eq." + questionId, "{" + fields(q).substring(1) + "}") == null) {
            return false;
        }
        if (q.hint() == null) {
            return SupabaseClient.delete("hints?challenge_id=eq." + questionId);
        }
        JsonValue existing = SupabaseClient.get("hints?challenge_id=eq." + questionId + "&select=id&order=hint_order&limit=1");
        if (existing == null) {
            return false;
        }
        if (existing.size > 0) {
            return SupabaseClient.patch("hints?id=eq." + existing.get(0).getLong("id"),
                    "{\"hint_text\":\"" + esc(q.hint()) + "\",\"generated_by\":\"manual\"}") != null;
        }
        return SupabaseClient.post("hints", "{\"challenge_id\":" + questionId
                + ",\"hint_order\":1,\"generated_by\":\"manual\",\"hint_text\":\"" + esc(q.hint()) + "\"}") != null;
    }

    public boolean delete(long questionId) {
        return SupabaseClient.delete("challenges?id=eq." + questionId);
    }

    /**
     * Copies the built-in starter questions into the lesson so the professor can rewrite them.
     * Returns how many were added.
     */
    public int importBuiltIn(long stageId) {
        StaticChallengeProvider builtIn = new StaticChallengeProvider();
        int added = 0;
        for (CTopic topic : CTopic.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                var c = builtIn.getChallenge(topic, difficulty);
                if (c.isPresent() && c.get().topic() == topic && c.get().difficulty() == difficulty) {
                    var q = c.get();
                    if (add(stageId, new QuestionDraft(topic, difficulty, q.prompt(), q.correctToken(),
                            q.distractors(), q.hint()))) {
                        added++;
                    }
                }
            }
        }
        return added;
    }

    /** The JSON fields every question write shares, starting with a comma so it can follow other fields. */
    private static String fields(QuestionDraft q) {
        StringBuilder wrong = new StringBuilder("[");
        for (int i = 0; i < q.distractors().size(); i++) {
            wrong.append(i == 0 ? "" : ",").append('"').append(esc(q.distractors().get(i))).append('"');
        }
        wrong.append(']');
        return ",\"topic\":\"" + q.topic().name() + "\",\"difficulty\":\"" + q.difficulty().name().toLowerCase()
                + "\",\"prompt\":\"" + esc(q.prompt()) + "\",\"correct_token\":\"" + esc(q.correct())
                + "\",\"distractors\":" + wrong;
    }

    private static String esc(String s) {
        return CampaignData.escape(s);
    }
}
