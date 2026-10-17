package codequest.content;

import java.util.List;
import java.util.Objects;

/**
 * A professor-authored unit played across the campus map's four sections.
 * Holds content layout only — no path/waypoint pixel data, which stays with
 * the engine-aware gameplay screen, keeping this model engine-agnostic.
 */
public record Stage(
        String title,
        String campusBackground,
        List<Section> sections
) {
    public Stage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(campusBackground, "campusBackground");
        sections = List.copyOf(sections);
    }

    /** Total towers (= questions) in a full run. */
    public int towerCount() {
        return sections.stream().mapToInt(s -> s.towerTopics().size()).sum();
    }
}
