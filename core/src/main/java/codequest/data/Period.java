package codequest.data;

import java.util.List;
import java.util.Locale;

/**
 * The four grading periods of a semester. A lesson belongs to one of them (stages.period); students see their lessons
 * grouped by period and the professor can analyse the class one period at a time.
 */
public enum Period {
    PRELIMS("prelims", "Prelims"),
    MIDTERMS("midterms", "Midterms"),
    SEMIS("semis", "Semis"),
    FINALS("finals", "Finals");

    private final String key;
    private final String label;

    Period(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** What is stored in the database. */
    public String key() {
        return key;
    }

    /** What people read. */
    public String label() {
        return label;
    }

    /** The period for a stored key; anything unknown (or missing) is Prelims, which is also the default of a new lesson. */
    public static Period fromKey(String key) {
        if (key != null) {
            String k = key.trim().toLowerCase(Locale.ROOT);
            for (Period p : values()) {
                if (p.key.equals(k)) {
                    return p;
                }
            }
        }
        return PRELIMS;
    }

    /** The lessons that belong to a period (null = all of them), in their order. */
    public static List<Lesson> filter(List<Lesson> lessons, Period period) {
        if (period == null) {
            return lessons;
        }
        return lessons.stream().filter(l -> l.periodEnum() == period).toList();
    }
}
