package codequest.content;

import java.util.Optional;

/** Uses the primary provider, and only if it has nothing for a request, the fallback. */
public class FallbackChallengeProvider implements ChallengeProvider {

    private final ChallengeProvider primary;
    private final ChallengeProvider fallback;

    public FallbackChallengeProvider(ChallengeProvider primary, ChallengeProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<Challenge> getChallenge(CTopic topic, Difficulty difficulty) {
        Optional<Challenge> c = primary.getChallenge(topic, difficulty);
        return c.isPresent() ? c : fallback.getChallenge(topic, difficulty);
    }
}
