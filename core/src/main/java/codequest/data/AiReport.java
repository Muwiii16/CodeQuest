package codequest.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The AI's analysis for a professor, as parts the screen can draw: a summary, a few number tiles, cards for the
 * students who need attention, to watch and who do well, what the group struggled with, next steps and data gaps.
 * Every string is cut to a length that fits its box, so a long answer cannot spill out of a card.
 */
public record AiReport(String summary, List<Tile> tiles, List<Note> attention, List<Note> watch, List<Note> doingWell,
        List<String> struggles, List<String> nextSteps, List<String> dataGaps) {

    public record Tile(String label, String value) {
    }

    /** One finding: who it is about ("" for the whole group), what was found, and what to do. */
    public record Note(String who, String finding, String next) {
    }

    /** The report in the AI's JSON, or null when the text is not one (the screen then shows it as plain text). */
    public static AiReport parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonValue root = new JsonReader().parse(t.substring(start, end + 1));
            String summary = clip(root.getString("summary", ""), 170);
            if (summary.isBlank()) {
                return null;
            }
            List<Tile> tiles = new ArrayList<>();
            for (JsonValue v : array(root, "tiles", 4)) {
                tiles.add(new Tile(clip(v.getString("label", ""), 26), clip(v.getString("value", ""), 12)));
            }
            return new AiReport(summary, tiles, notes(root, "attention"), notes(root, "watch"), notes(root, "doingWell"),
                    strings(root, "struggles", 3, 70), strings(root, "nextSteps", 3, 90), strings(root, "dataGaps", 2, 90));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The same report with {@code fix} applied to every string (used to put real names back for S1, S2...). */
    public AiReport map(java.util.function.UnaryOperator<String> fix) {
        return new AiReport(fix.apply(summary),
                tiles.stream().map(t -> new Tile(fix.apply(t.label()), fix.apply(t.value()))).toList(),
                notes(attention, fix), notes(watch, fix), notes(doingWell, fix),
                struggles.stream().map(fix).toList(), nextSteps.stream().map(fix).toList(), dataGaps.stream().map(fix).toList());
    }

    private static List<Note> notes(List<Note> list, java.util.function.UnaryOperator<String> fix) {
        return list.stream().map(n -> new Note(fix.apply(n.who()), fix.apply(n.finding()), fix.apply(n.next()))).toList();
    }

    private static List<Note> notes(JsonValue root, String key) {
        List<Note> out = new ArrayList<>();
        for (JsonValue v : array(root, key, 2)) {
            String finding = clip(v.getString("finding", ""), 90);
            if (!finding.isBlank()) {
                out.add(new Note(clip(v.getString("who", ""), 24), finding, clip(v.getString("next", ""), 80)));
            }
        }
        return out;
    }

    private static List<String> strings(JsonValue root, String key, int max, int length) {
        List<String> out = new ArrayList<>();
        for (JsonValue v : array(root, key, max)) {
            String s = clip(v.isString() ? v.asString() : "", length);
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<JsonValue> array(JsonValue root, String key, int max) {
        List<JsonValue> out = new ArrayList<>();
        JsonValue a = root.get(key);
        if (a != null && a.isArray()) {
            for (JsonValue v = a.child; v != null && out.size() < max; v = v.next) {
                out.add(v);
            }
        }
        return out;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() <= max ? t : t.substring(0, max - 3).trim() + "...";
    }
}
