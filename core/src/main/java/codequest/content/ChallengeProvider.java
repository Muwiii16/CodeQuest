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
}
