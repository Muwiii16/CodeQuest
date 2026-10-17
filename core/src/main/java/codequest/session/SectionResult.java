package codequest.session;

import codequest.content.Difficulty;

/** How one map section went, and what difficulty the next section will use. */
public record SectionResult(
        int sectionIndex,
        int correct,
        int total,
        double accuracy,
        Difficulty playedAt,
        Difficulty nextDifficulty
) {
}
