package codequest.content;

import java.util.List;
import java.util.Objects;

/** One of a stage's four map sections: the topic of each tower along it, in walking order. */
public record Section(int index, List<CTopic> towerTopics) {
    public Section {
        Objects.requireNonNull(towerTopics, "towerTopics");
        towerTopics = List.copyOf(towerTopics);
    }
}
