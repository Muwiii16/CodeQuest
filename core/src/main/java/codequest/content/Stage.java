package codequest.content;

import java.util.List;
import java.util.Objects;

/**
 * Content metadata for one of the four campus-themed map sections —
 * sectionIndex, display info, and the ordered CTopics its towers cover.
 * Deliberately holds no path/waypoint pixel data; that stays a separate,
 * engine-aware concern owned by the actual gameplay screen, which is what
 * keeps this model engine-agnostic.
 */
public record Stage(
        int sectionIndex,
        String title,
        String campusBackground,
        List<CTopic> topics
) {
    public Stage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(campusBackground, "campusBackground");
        topics = List.copyOf(topics);
    }
}
