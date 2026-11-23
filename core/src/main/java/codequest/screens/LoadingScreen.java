package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A screen that exists only to wait: it runs a slow job (fetching what the next screen needs) in the
 * background with the loading spinner on, then hands the result on. A short connection drop is retried
 * quietly; if the job still fails it says why, offers Try again and a way back, and writes the reason to
 * the error log, instead of leaving the player on a frozen window.
 */
public final class LoadingScreen extends BaseScreen {

    /** A failure with a message that is safe and useful to show the player as it is (it is not retried). */
    public static final class Failure extends RuntimeException {
        public Failure(String message) {
            super(message);
        }
    }

    private static final int ATTEMPTS = 3;
    private static final long PAUSE_MILLIS = 1200L;

    /** Why the last try failed: set in the background, read when the result arrives. */
    private volatile String reason;

    private <T> LoadingScreen(Main game, String message, Supplier<T> work, Consumer<T> next, Runnable onFail) {
        setBackground(Art.BG_MENU);
        load(message, () -> {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    return Optional.of(work.get());
                } catch (Failure e) {
                    reason = e.getMessage();
                    log(message, e);
                    return Optional.<T>empty();
                } catch (RuntimeException e) {
                    System.err.println("LoadingScreen: " + message + " failed (try " + attempt + "): " + e);
                    log(message + " (try " + attempt + ")", e);
                    if (attempt < ATTEMPTS) {
                        try {
                            Thread.sleep(PAUSE_MILLIS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return Optional.<T>empty();
                        }
                    }
                }
            }
            return Optional.<T>empty();
        }, result -> {
            if (result.isPresent()) {
                next.accept(result.get());
                return;
            }
            String why = reason;
            // a dark panel keeps the words readable over the busy picture behind
            rect(430, 330, 1060, 400, new Color(0.04f, 0.06f, 0.15f, 0.93f));
            text(why != null ? "Could not open this." : "Could not reach the server.", 470, 360, 980, 70, 2.4f,
                    new Color(1f, 0.72f, 0.66f, 1f), Align.center);
            text(why != null ? why : "Check your internet connection, then try again.\nYour progress is safe.", 470, 440,
                    980, 120, 1.6f, Color.WHITE, Align.center, PixelFonts.Face.SANS).setWrap(true);
            solidButton("Try again", 480, 590, 450, 80, new Color(0.12f, 0.42f, 0.86f, 1f),
                    () -> game.setScreen(new LoadingScreen(game, message, work, next, onFail)));
            solidButton("Back", 990, 590, 450, 80, new Color(0.3f, 0.1f, 0.1f, 1f), onFail);
        });
    }

    /** Shows the loading screen, runs {@code work} in the background and then calls {@code next} with its result. */
    public static <T> void open(Main game, String message, Supplier<T> work, Consumer<T> next, Runnable onFail) {
        game.setScreen(new LoadingScreen(game, message, work, next, onFail));
    }

    /** Adds one line to ~/.codequest/error.log, so a tester can send it with a bug report. Never throws. */
    private static void log(String what, Throwable error) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".codequest");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("error.log"), Instant.now() + "  " + what + "  " + error + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // logging must never be the thing that fails
        }
    }
}
