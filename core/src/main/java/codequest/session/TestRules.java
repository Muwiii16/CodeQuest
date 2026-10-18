package codequest.session;

import java.time.Duration;
import java.time.Instant;

/** When a class test ends by itself (a professor can always end it by hand too). */
public final class TestRules {

    /** A running test is closed this long after it started, whoever has not finished. */
    public static final Duration MAX_DURATION = Duration.ofMinutes(20);

    private TestRules() {
    }

    /**
     * True when a running test should be closed now: everyone who joined has finished, or the
     * time limit has passed. A lobby that has not started never ends on its own.
     */
    public static boolean shouldEnd(String status, int participants, int finished, Instant startedAt, Instant now) {
        if (!"active".equals(status)) {
            return false;
        }
        if (participants > 0 && finished >= participants) {
            return true;
        }
        return startedAt != null && Duration.between(startedAt, now).compareTo(MAX_DURATION) >= 0;
    }
}
