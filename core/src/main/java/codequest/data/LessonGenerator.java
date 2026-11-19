package codequest.data;

import codequest.content.AnswerKind;
import codequest.content.ScopeCheck;
import codequest.content.CTopic;
import codequest.content.Difficulty;
import codequest.content.GeneratedLesson;
import codequest.content.QuestionDraft;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Asks Gemini (through the generate-lesson Edge Function, which holds the key) to draft a lesson: one C program and the
 * questions that build it. Used when a professor creates a stage, never during play. If the first draft breaks a rule,
 * the problems are sent back once and the AI writes it again.
 */
public class LessonGenerator {

    /** Exactly one of lesson and error is set. */
    public record Result(GeneratedLesson lesson, String error) {
        public boolean ok() {
            return lesson != null;
        }
    }

    private static final int TIMEOUT_SECONDS = 90;

    /** A lesson with no topic limit. Blocks for up to a minute or two: call it from a background thread. */
    public Result generate(String prompt, String difficulty) {
        return generate(prompt, difficulty, Set.of(), Set.of(), "");
    }

    /**
     * @param focus the topics this lesson teaches (empty: no limit)
     * @param taught the topics of the lessons before it, which it may reuse
     * @param taughtNotes what those lessons were about, in the professor's words, for the AI
     */
    public Result generate(String prompt, String difficulty, Set<CTopic> focus, Set<CTopic> taught, String taughtNotes) {
        Set<CTopic> allowed = focus.isEmpty() ? null : ScopeCheck.allowed(focus, taught);
        String problems = null;
        String lastProblem = "The AI did not give a usable lesson.";
        for (int attempt = 0; attempt < 2; attempt++) {
            String body = "{\"prompt\":\"" + SupabaseClient.jsonEscape(prompt) + "\",\"difficulty\":\""
                    + SupabaseClient.jsonEscape(difficulty) + "\",\"focus\":\"" + ScopeCheck.formatTopics(focus)
                    + "\",\"taught\":\"" + ScopeCheck.formatTopics(taught) + "\",\"taughtNotes\":\""
                    + SupabaseClient.jsonEscape(taughtNotes) + "\""
                    + (problems == null ? "" : ",\"problems\":\"" + SupabaseClient.jsonEscape(problems) + "\"") + "}";
            SupabaseClient.FunctionResult r = SupabaseClient.callFunction("generate-lesson", body, TIMEOUT_SECONDS);
            if (!r.ok()) {
                return new Result(null, errorMessage(r));
            }
            Optional<GeneratedLesson> parsed = parse(r.body());
            if (parsed.isEmpty()) {
                problems = "The answer did not follow the format.";
                lastProblem = problems;
                continue;
            }
            Optional<String> p = parsed.get().problems(allowed);
            if (p.isEmpty()) {
                return new Result(parsed.get(), null);
            }
            problems = p.get();
            lastProblem = problems;
        }
        return new Result(null, "The AI's draft broke a rule twice: " + lastProblem);
    }

    private static String errorMessage(SupabaseClient.FunctionResult r) {
        if (r.status() == 0) {
            return "Could not reach the server. Check your connection.";
        }
        try {
            String message = new JsonReader().parse(r.body()).getString("error", null);
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // not JSON: fall through
        }
        return r.status() == 404 ? "The AI function is not deployed yet." : "The AI request failed (HTTP " + r.status() + ").";
    }

    /** Reads the function's JSON; empty when it is not the expected shape. */
    public static Optional<GeneratedLesson> parse(String json) {
        try {
            JsonValue root = new JsonReader().parse(json);
            JsonValue qs = root.get("questions");
            if (qs == null) {
                return Optional.empty();
            }
            List<QuestionDraft> drafts = new ArrayList<>();
            for (JsonValue q : qs) {
                drafts.add(new QuestionDraft(
                        CTopic.valueOf(q.getString("topic").toUpperCase()),
                        Difficulty.valueOf(q.getString("difficulty", "normal").toUpperCase()),
                        q.getString("prompt"), q.getString("correct"), strings(q.get("wrong")),
                        q.getString("hint", null), AnswerKind.fromDb(q.getString("kind", null)),
                        strings(q.get("accepted")), q.getString("chunk", null), q.getString("context", null),
                        q.getString("explanation", null), q.getString("error_text", null)).cleaned());
            }
            return Optional.of(new GeneratedLesson(root.getString("title", "Generated lesson"), root.getString("program"),
                    root.getString("outputs", null), drafts));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static List<String> strings(JsonValue array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            for (JsonValue v : array) {
                out.add(v.asString());
            }
        }
        return out;
    }
}
