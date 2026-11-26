package codequest.screens;

import codequest.Main;
import codequest.update.Updater;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * First screen of the game: the logo fades in over the intro background while every picture and font is loaded into
 * the shared cache ({@link Assets}) behind a loading bar, so nothing has to be read from disk later and moving between
 * screens is smooth. When loading is done it waits for a click or key press (or moves on by itself after a few seconds).
 * Meanwhile an installed game asks whether a newer version was released; if so it goes straight to updating.
 */
public class IntroScreen extends BaseScreen {

    private static final float AUTO_ADVANCE_SECONDS = 7f;
    /** The longest the player waits for the update check after asking to start. */
    private static final float UPDATE_WAIT_SECONDS = 6f;
    /** How long one frame may spend loading before it draws again (so the bar keeps moving). */
    private static final long BUDGET_NANOS = 12_000_000L;

    private static final float BAR_X = 360f;
    private static final float BAR_Y = 905f;
    private static final float BAR_W = 1200f;
    private static final float BAR_H = 22f;

    private final Main game;
    private final List<Assets.Task> tasks;
    private int done;
    private boolean ready;
    private float sinceReady;
    private boolean leaving;
    private boolean wantsToStart;
    private float waitedForCheck;
    /** Empty when this copy cannot update itself (run from source, or not Windows). */
    private final Optional<Updater> updater = Updater.installed();
    private final CompletableFuture<Optional<Updater.Release>> newer = updater
            .map(u -> CompletableFuture.supplyAsync(u::check))
            .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));

    private final Image barFill;
    private final Image barTrack;
    private final Image barFrame;
    private final Label status;
    private final Label prompt;

    public IntroScreen(Main game) {
        this.game = game;
        setBackground(Art.BG_INTRO);

        Image logo = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO))));
        float logoWidth = 1300f;
        float logoHeight = logoWidth * (logo.getDrawable().getMinHeight() / logo.getDrawable().getMinWidth());
        placeTopLeft(logo, (VIRTUAL_WIDTH - logoWidth) / 2f, 300f, logoWidth, logoHeight);
        logo.setOrigin(logoWidth / 2f, logoHeight / 2f);
        logo.setScale(0.9f);
        logo.getColor().a = 0f;
        logo.addAction(Actions.parallel(Actions.fadeIn(1.4f), Actions.scaleTo(1f, 1f, 1.8f,
                com.badlogic.gdx.math.Interpolation.fade)));
        stage.addActor(logo);

        // the loading bar: a frame, a dark track and the part that fills
        barFrame = rect(BAR_X - 4f, BAR_Y - 4f, BAR_W + 8f, BAR_H + 8f, new Color(1f, 1f, 1f, 0.85f));
        barTrack = rect(BAR_X, BAR_Y, BAR_W, BAR_H, new Color(0.05f, 0.08f, 0.2f, 1f));
        barFill = rect(BAR_X, BAR_Y, 1f, BAR_H, new Color(0.35f, 0.85f, 0.75f, 1f));
        status = outlinedText("Loading...", 0, BAR_Y - 52f, VIRTUAL_WIDTH, 40, 1.9f, new Color(0.9f, 0.94f, 1f, 1f), Align.center);

        prompt = new Label("Click anywhere or press any key to start",
                new Label.LabelStyle(PixelFonts.forScale(2.2f, true), new Color(0.9f, 0.94f, 1f, 1f)));
        prompt.pack();
        placeTopLeft(prompt, (VIRTUAL_WIDTH - prompt.getWidth()) / 2f, 880f, prompt.getWidth(), prompt.getHeight());
        prompt.getColor().a = 0f;
        stage.addActor(prompt);

        tasks = Assets.tasks();

        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                advance();
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                advance();
                return true;
            }
        });
    }

    @Override
    public void show() {
        super.show();
        stage.setKeyboardFocus(stage.getRoot());
    }

    @Override
    public void render(float delta) {
        if (!ready) {
            loadSome();
        } else {
            sinceReady += delta;
            if (sinceReady > AUTO_ADVANCE_SECONDS || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                advance();
            }
            leaveWhenPossible(delta);
        }
        if (leaving) {
            return; // the next screen has taken over and this one is disposed: do not draw it
        }
        super.render(delta);
    }

    /** Runs loading steps until this frame's time is used up, then moves the bar. */
    private void loadSome() {
        long start = System.nanoTime();
        while (done < tasks.size() && System.nanoTime() - start < BUDGET_NANOS) {
            tasks.get(done).run().run();
            done++;
        }
        float progress = tasks.isEmpty() ? 1f : (float) done / tasks.size();
        barFill.setWidth(Math.max(1f, BAR_W * progress));
        if (done < tasks.size()) {
            status.setText("Loading " + tasks.get(done).label() + "...  " + Math.round(progress * 100f) + "%");
            return;
        }
        // everything is cached: swap the bar for the prompt
        ready = true;
        barFrame.remove();
        barTrack.remove();
        barFill.remove();
        status.remove();
        prompt.addAction(Actions.sequence(Actions.alpha(1f, 0.4f), Actions.forever(Actions.sequence(
                Actions.alpha(0.25f, 0.9f), Actions.alpha(1f, 0.9f)))));
    }

    private void advance() {
        if (!ready || leaving || sinceReady < 0.4f) {
            return;
        }
        wantsToStart = true;
    }

    /** Updates as soon as a newer version is known; otherwise starts the game when the player asked to. */
    private void leaveWhenPossible(float delta) {
        if (leaving) {
            return;
        }
        // a check that failed unexpectedly counts as "nothing new"
        Optional<Updater.Release> release = newer.isDone() && !newer.isCompletedExceptionally()
                ? newer.getNow(Optional.empty()) : Optional.empty();
        if (release.isPresent() && updater.isPresent()) {
            leaving = true;
            game.setScreen(new UpdateScreen(game, updater.get(), release.get()));
            return;
        }
        if (wantsToStart) {
            waitedForCheck += delta;
            if (newer.isDone() || waitedForCheck > UPDATE_WAIT_SECONDS) {
                leaving = true;
                game.setScreen(new LoginScreen(game));
            }
        }
    }
}
