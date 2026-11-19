package codequest.data;

import com.badlogic.gdx.utils.JsonReader;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Written feedback from the AI (DFD 7.0), through the ai-feedback Edge Function, which holds the keys. Two uses: a student
 * asks for feedback after a lesson, and a professor asks for a written analysis of a class.
 *
 * <p>No name ever leaves the game: the student's feedback is built from question text and answers only, and the
 * professor's analysis labels students S1, S2, ... ; {@link #restoreNames} puts the real names back into the answer here.
 */
public class AiFeedback {

    /** Exactly one of text and error is set. */
    public record Result(String text, String error) {
        public boolean ok() {
            return text != null;
        }
    }

    /** One answered question of a lesson run. */
    public record Item(String topic, String question, String yourAnswer, String correctAnswer, boolean correct) {
    }

    private static final int TIMEOUT_SECONDS = 60;

    /** Feedback for one student on a lesson they just played. Blocks: call it from a background thread. */
    public Result forStudent(String lessonTitle, String rank, int accuracyPercent, List<Item> items) {
        List<Object> answers = new java.util.ArrayList<>();
        for (Item i : items) {
            answers.add(Map.of("topic", i.topic(), "question", i.question(), "yourAnswer", i.yourAnswer() == null ? "" : i.yourAnswer(),
                    "correctAnswer", i.correctAnswer(), "correct", i.correct()));
        }
        return call("student", Map.of("lesson", lessonTitle, "rank", rank, "accuracyPercent", accuracyPercent, "answers", answers));
    }

    /**
     * An analysis for a professor. {@code data} holds only statistics and the students under labels (S1, S2...); the real
     * names, in the same order as the labels, are put back into the answer.
     */
    public Result forClass(Map<String, Object> data, List<String> namesInLabelOrder) {
        Result r = call("class", data);
        return r.ok() ? new Result(restoreNames(r.text(), namesInLabelOrder), null) : r;
    }

    /** The label the n-th student (counting from 0) is sent under. */
    public static String label(int index) {
        return "S" + (index + 1);
    }

    /** Replaces the labels S1, S2... in the AI's text with the students' real names. A label with no name is left as it is. */
    public static String restoreNames(String text, List<String> names) {
        Matcher m = Pattern.compile("\\bS(\\d{1,3})\\b").matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1)) - 1;
            String replacement = index >= 0 && index < names.size() ? names.get(index) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private Result call(String mode, Object data) {
        String body = "{\"mode\":\"" + mode + "\",\"data\":" + toJson(data) + "}";
        SupabaseClient.FunctionResult r = SupabaseClient.callFunction("ai-feedback", body, TIMEOUT_SECONDS);
        String message = null;
        String text = null;
        try {
            var root = new JsonReader().parse(r.body());
            message = root.getString("error", null);
            text = root.getString("text", null);
        } catch (Exception ignored) {
            // not JSON: handled below
        }
        if (r.ok() && text != null && !text.isBlank()) {
            return new Result(text.trim(), null);
        }
        if (message != null) {
            return new Result(null, message);
        }
        return new Result(null, r.status() == 0 ? "Could not reach the server. Check your connection."
                : r.status() == 404 ? "The AI feedback function is not deployed yet."
                        : "The AI request failed (HTTP " + r.status() + ").");
    }

    /** A small JSON writer for maps, lists, strings, numbers, booleans and null. */
    static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + SupabaseClient.jsonEscape(s) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(first ? "" : ",").append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (value instanceof Iterable<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : list) {
                sb.append(first ? "" : ",").append(toJson(o));
                first = false;
            }
            return sb.append(']').toString();
        }
        return toJson(String.valueOf(value));
    }
}
