package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A screen that exists only to wait: it runs a slow job (fetching what the next screen needs) in the
 * background with the loading spinner on, then hands the result on. If the job fails it says so and
 * offers a way back, instead of leaving the player on a frozen window.
 */
public final class LoadingScreen extends BaseScreen {

    private <T> LoadingScreen(String message, Supplier<T> work, Consumer<T> next, Runnable onFail) {
        setBackground(Art.BG_MENU);
        load(message, () -> {
            try {
                return Optional.of(work.get());
            } catch (RuntimeException e) {
                System.err.println("LoadingScreen: " + message + " failed: " + e);
                return Optional.<T>empty();
            }
        }, result -> {
            if (result.isPresent()) {
                next.accept(result.get());
            } else {
                text("Could not reach the server. Check your connection and try again.", 0, 440, VIRTUAL_WIDTH, 60, 2f,
                        new Color(1f, 0.6f, 0.55f, 1f), Align.center);
                solidButton("Back", VIRTUAL_WIDTH / 2f - 200, 560, 400, 80, new Color(0.3f, 0.1f, 0.1f, 1f), onFail);
            }
        });
    }

    /** Shows the loading screen, runs {@code work} in the background and then calls {@code next} with its result. */
    public static <T> void open(Main game, String message, Supplier<T> work, Consumer<T> next, Runnable onFail) {
        game.setScreen(new LoadingScreen(message, work, next, onFail));
    }
}
