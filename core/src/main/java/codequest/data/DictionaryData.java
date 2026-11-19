package codequest.data;

import codequest.content.CTopic;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dictionary of a programming language: terms with a plain meaning and an example, read-only for the app (the
 * entries are added by the project owner through the database). One shared set per language.
 */
public class DictionaryData {

    /** One term. The example may have several lines. */
    public record Entry(String term, CTopic topic, String meaning, String example) {
    }

    /** All the entries of a language in their order; empty if the server cannot be reached. */
    public List<Entry> entries(String language) {
        List<Entry> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("dictionary_entries?language=eq." + SupabaseClient.encode(language)
                + "&select=term,topic,meaning,example&order=sort_order,term");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            try {
                out.add(new Entry(r.getString("term"), CTopic.valueOf(r.getString("topic").toUpperCase()),
                        r.getString("meaning"), r.getString("example", "")));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed dictionary row: " + e);
            }
        }
        return out;
    }

    /** The entries of one topic (null = every topic) whose term or meaning contains the search text. */
    public static List<Entry> filter(List<Entry> all, CTopic topic, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            boolean topicOk = topic == null || e.topic() == topic;
            boolean textOk = q.isEmpty() || e.term().toLowerCase(Locale.ROOT).contains(q)
                    || e.meaning().toLowerCase(Locale.ROOT).contains(q);
            if (topicOk && textOk) {
                out.add(e);
            }
        }
        return out;
    }
}
