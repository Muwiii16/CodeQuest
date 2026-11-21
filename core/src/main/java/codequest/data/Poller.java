package codequest.data;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Re-runs a (blocking) database read on a background thread so a screen can show
 * live data (class-test lobby, scoreboard) without freezing a frame. The screen
 * just reads {@link #latest()} when it draws; close() it when the screen goes away.
 */
public final class Poller<T> implements AutoCloseable {

    /**
     * How often the class-test screens (lobby, test, results; student and professor) ask the server for the latest. Every
     * student in a test polls, so this sets the load: a load test of 80 students (4 sections of 20) at 2 s gave about 100
     * requests a second and was fine; 3 s brings that to about two thirds, which leaves more room on a small database.
     */
    public static final long CLASS_TEST_PERIOD_MS = 3000;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "codequest-poller");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledFuture<?> task;
    private volatile T latest;

    public Poller(Supplier<T> read, long periodMillis) {
        task = executor.scheduleWithFixedDelay(() -> {
            try {
                T value = read.get();
                if (value != null) {
                    latest = value;
                }
            } catch (RuntimeException e) {
                System.err.println("Poller: read failed: " + e);
            }
        }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }

    /** The newest successful read, or null until the first one finishes. */
    public T latest() {
        return latest;
    }

    @Override
    public void close() {
        task.cancel(true);
        executor.shutdownNow();
    }
}
