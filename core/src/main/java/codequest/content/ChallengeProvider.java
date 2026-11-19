package codequest.content;

import java.util.Optional;

/**
 * Source of Challenges by topic and difficulty. Gameplay code depends only on
 * this interface, not on where challenges actually come from — StaticChallengeProvider
 * is the first implementation; an AI-backed one (Phase 5) drops in behind the
 * same interface with no changes needed elsewhere.
 */
public interface ChallengeProvider {
    Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty);

    /**
     * True when the questions are the chunks of one lesson program: every answer then adds its chunk of the program
     * to the code block (green when right, red when wrong) instead of a single token.
     */
    default boolean buildsProgram() {
        return false;
    }
}
