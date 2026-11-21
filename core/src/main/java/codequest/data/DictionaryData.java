package codequest.data;

import codequest.content.CTopic;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The dictionary of a programming language: terms with a plain meaning and an example, read-only for the app (the
 * entries are added by the project owner through the database). One shared set per language; a student sees the
 * languages of the classes they are in, and a class shows only the language it teaches.
 */
public class DictionaryData {

    /** One term of one language. The example may have several lines. */
    public record Entry(String language, String term, CTopic topic, String meaning, String example) {
        /** A term of C, the language the game started with. */
        public Entry(String term, CTopic topic, String meaning, String example) {
            this("C", term, topic, meaning, example);
        }
    }

    /**
     * The languages a student's classes teach, each once, in the order of the classes. A student who is in no class yet plays
     * the built-in lessons, which are C, so they get C.
     */
    public static List<String> languagesOf(List<CampaignData.ClassInfo> classes) {
        Set<String> languages = new LinkedHashSet<>();
        for (CampaignData.ClassInfo c : classes) {
            if (c.language() != null && !c.language().isBlank()) {
                languages.add(c.language());
            }
        }
        return languages.isEmpty() ? List.of("C") : new ArrayList<>(languages);
    }

    /** All the entries of a language in their order; empty if the server cannot be reached. */
    public List<Entry> entries(String language) {
        return entries(List.of(language));
    }

    /** The entries of several languages together (language by language, each in its own order). */
    public List<Entry> entries(List<String> languages) {
        List<Entry> out = new ArrayList<>();
        if (languages.isEmpty()) {
            return out;
        }
        StringBuilder in = new StringBuilder();
        for (String l : languages) {
            if (l != null && !l.isBlank() && !l.matches(".*[,()\"].*")) { // those characters would break the list in the address
                in.append(in.length() == 0 ? "" : ",").append(SupabaseClient.encode(l));
            }
        }
        if (in.length() == 0) {
            return out;
        }
        JsonValue rows = SupabaseClient.get("dictionary_entries?language=in.(" + in
                + ")&select=language,term,topic,meaning,example&order=language,sort_order,term");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            try {
                out.add(new Entry(r.getString("language", "C"), r.getString("term"),
                        CTopic.valueOf(r.getString("topic").toUpperCase()), r.getString("meaning"),
                        r.getString("example", "")));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed dictionary row: " + e);
            }
        }
        return out;
    }

    /** The entries of one topic (null = every topic) whose term or meaning contains the search text. */
    public static List<Entry> filter(List<Entry> all, CTopic topic, String query) {
        return filter(all, null, topic, query);
    }

    /** Same, narrowed to one language too (null = every language). */
    public static List<Entry> filter(List<Entry> all, String language, CTopic topic, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            boolean languageOk = language == null || language.equalsIgnoreCase(e.language());
            boolean topicOk = topic == null || e.topic() == topic;
            boolean textOk = q.isEmpty() || e.term().toLowerCase(Locale.ROOT).contains(q)
                    || e.meaning().toLowerCase(Locale.ROOT).contains(q);
            if (languageOk && topicOk && textOk) {
                out.add(e);
            }
        }
        return out;
    }
}
