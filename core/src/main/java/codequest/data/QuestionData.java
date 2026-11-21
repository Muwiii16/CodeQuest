package codequest.data;

import codequest.content.AnswerKind;
import codequest.content.CTopic;
import codequest.content.Challenge;
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

    private final codequest.content.Language language;

    /** Questions of a C lesson (every class made before C++ was added). */
    public QuestionData() {
        this(codequest.content.Language.C);
    }

    /** Questions of a lesson of this class language: what is written to challenges.language and imported as starters. */
    public QuestionData(codequest.content.Language language) {
        this.language = language;
    }

    /**
     * A stored question; {@code id} is the challenges row id. Questions with the same {@code slot} are the same chunk of
     * the lesson's program asked at different difficulties.
     */
    public record Question(long id, QuestionDraft draft, int slot) {

        public Question(long id, QuestionDraft draft) {
            this(id, draft, 0);
        }
    }

    /**
     * The questions grouped by slot, in program order, each group easiest first. A question with no slot is a group of
     * its own.
     */
    public static List<List<Question>> bySlot(List<Question> questions) {
        java.util.Map<Integer, List<Question>> groups = new java.util.LinkedHashMap<>();
        int alone = -1;
        for (Question q : questions) {
            groups.computeIfAbsent(q.slot() > 0 ? q.slot() : alone--, k -> new ArrayList<>()).add(q);
        }
        List<List<Question>> out = new ArrayList<>(groups.values());
        for (List<Question> g : out) {
            g.sort(java.util.Comparator.comparingInt(q -> q.draft().difficulty().ordinal()));
        }
        return out;
    }

    /** Whether the lesson's questions, slot by slot, rebuild its program exactly (see ProgramCoverage). */
    public static codequest.content.ProgramCoverage.Report coverage(String program, List<Question> questions) {
        List<List<String>> slots = new ArrayList<>();
        for (List<Question> group : bySlot(questions)) {
            List<String> chunks = new ArrayList<>();
            for (Question q : group) {
                chunks.add(q.draft().codeChunk());
            }
            slots.add(chunks);
        }
        return codequest.content.ProgramCoverage.checkSlots(program, slots);
    }

    /** Questions of a lesson, grouped as they were added. */
    public List<Question> list(long stageId) {
        List<Question> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("challenges?stage_id=eq." + stageId
                + "&select=id,topic,difficulty,prompt,correct_token,distractors,kind,accepted_answers,code_chunk,context,sequence_index,explanation,error_text,"
                + "hints(hint_text,hint_order)&order=sequence_index,id");
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
                List<String> accepted = new ArrayList<>();
                JsonValue other = r.get("accepted_answers");
                if (other != null) {
                    for (JsonValue x : other) {
                        accepted.add(x.asString());
                    }
                }
                out.add(new Question(r.getLong("id"), new QuestionDraft(topic, difficulty, prompt, correct, wrong, hint,
                        AnswerKind.fromDb(r.getString("kind", null)), accepted, r.getString("code_chunk", null), r.getString("context", null),
                        r.getString("explanation", null), r.getString("error_text", null)),
                        r.getInt("sequence_index", 0)));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed question row " + r.getString("id", "?") + ": " + e);
            }
        }
        return out;
    }

    /** Saves a new question at the end of the lesson. False if the server refused it. */
    public boolean add(long stageId, QuestionDraft draft) {
        return add(stageId, draft, 0);
    }

    /**
     * Saves a question. Slot 0 puts it at the end of the lesson; a slot number makes it another difficulty of that
     * chunk (the server refuses it if that slot already has a question at that difficulty).
     */
    public boolean add(long stageId, QuestionDraft draft, int slot) {
        QuestionDraft q = draft.cleaned();
        int next = slot;
        if (next <= 0) {
            JsonValue last = SupabaseClient.get("challenges?stage_id=eq." + stageId
                    + "&select=sequence_index&order=sequence_index.desc&limit=1");
            if (last == null) {
                return false;
            }
            next = last.size > 0 ? last.get(0).getInt("sequence_index", 0) + 1 : 1;
        }
        String body = "{\"stage_id\":" + stageId + ",\"sequence_index\":" + next + ",\"section_index\":1"
                + ",\"language\":\"" + language.key() + "\",\"ai_generated\":false" + fields(q) + "}";
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
        int added = 0;
        for (Challenge q : new StaticChallengeProvider(language).all()) {
            if (add(stageId, new QuestionDraft(q.topic(), q.difficulty(), q.prompt(), q.correctToken(),
                    q.distractors(), q.hint(), q.kind(), q.accepted(), q.chunk(), q.context(), q.explanation(), q.errorText()))) {
                added++;
            }
        }
        return added;
    }

    /**
     * Adds the questions of a built-in program to the lesson, in program order (one per chunk), so the professor can
     * rewrite them. Returns how many were added.
     */
    public int importProgram(long stageId, codequest.content.BuiltInPrograms.Program program) {
        int added = 0;
        for (Challenge q : program.slots()) {
            if (add(stageId, new QuestionDraft(q.topic(), q.difficulty(), q.prompt(), q.correctToken(), q.distractors(),
                    q.hint(), q.kind(), q.accepted(), q.chunk(), q.context(), q.explanation(), q.errorText()))) {
                added++;
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
        StringBuilder other = new StringBuilder("[");
        for (int i = 0; i < q.accepted().size(); i++) {
            other.append(i == 0 ? "" : ",").append('"').append(esc(q.accepted().get(i))).append('"');
        }
        other.append(']');
        return ",\"topic\":\"" + q.topic().name() + "\",\"difficulty\":\"" + q.difficulty().name().toLowerCase()
                + "\",\"prompt\":\"" + esc(q.prompt()) + "\",\"correct_token\":\"" + esc(q.correct())
                + "\",\"distractors\":" + wrong + ",\"kind\":\"" + q.kind().dbName() + "\",\"accepted_answers\":" + other
                + ",\"code_chunk\":" + (q.chunk() == null ? "null" : "\"" + esc(q.chunk()) + "\"")
                + ",\"context\":" + (q.context() == null ? "null" : "\"" + esc(q.context()) + "\"")
                + ",\"explanation\":" + (q.explanation() == null ? "null" : "\"" + esc(q.explanation()) + "\"")
                + ",\"error_text\":" + (q.errorText() == null ? "null" : "\"" + esc(q.errorText()) + "\"");
    }

    private static String esc(String s) {
        return CampaignData.escape(s);
    }
}
