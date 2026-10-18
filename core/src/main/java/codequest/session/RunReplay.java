package codequest.session;

import java.util.List;

/**
 * Rebuilds a class-test run from the answers given so far. Because the questions of
 * a class test are served from a seeded pool, answering the same way in the same
 * order always lands on the same state, so saving just the answers is enough to
 * resume a test after the game was closed.
 */
public final class RunReplay {

    /** One answered tower: the token picked (null = ran out of time) and the share of time that was left. */
    public record Answer(String token, float timeLeft) {
    }

    private RunReplay() {
    }

    /** Feeds the answers to a fresh run; false if the run ended before they were used up (a stale save). */
    public static boolean replay(StageRun run, List<Answer> answers) {
        for (Answer a : answers) {
            if (run.nextChallenge().isEmpty()) {
                return false;
            }
            run.answer(a.token(), a.timeLeft());
        }
        return true;
    }
}
