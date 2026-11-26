package codequest.screens;

import codequest.Main;
import codequest.update.Updater;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Downloads a newer version of the game behind a progress bar, hands it to the installer and closes the game, which the
 * installer then opens again. If anything goes wrong (no connection, a download that stalls, Windows refusing the
 * installer) the player carries on with the version they have.
 */
public final class UpdateScreen extends BaseScreen {

    private static final float BAR_X = 360f;
    private static final float BAR_Y = 760f;
    private static final float BAR_W = 1200f;
    private static final float BAR_H = 26f;
    /** A download that makes no progress for this long is given up. */
    private static final float STALL_SECONDS = 30f;
    /** How long the "could not update" message stays before the game starts. */
    private static final float FAILED_SECONDS = 3f;

    private final Main game;
    private final Updater updater;
    private final Updater.Release release;
    private final Image bar;
    private final Label status;
    private final Thread worker;

    private volatile double progress;
    private volatile Path installer;
    private volatile boolean failed;
    private double lastProgress = -1;
    private float sinceProgress;
    private float sinceFailed;
    private boolean finished;

    public UpdateScreen(Main game, Updater updater, Updater.Release release) {
        this.game = game;
        this.updater = updater;
        this.release = release;
        setBackground(Art.BG_INTRO);
        outlinedText("Updating CodeQuest", 0, 560, VIRTUAL_WIDTH, 70, 3.4f, Color.WHITE, Align.center);
        outlinedText("Version " + release.version() + "  -  the game will close and open again by itself.", 0, 655,
                VIRTUAL_WIDTH, 40, 1.7f, new Color(0.9f, 0.94f, 1f, 1f), Align.center);
        rect(BAR_X - 4f, BAR_Y - 4f, BAR_W + 8f, BAR_H + 8f, new Color(1f, 1f, 1f, 0.85f));
        rect(BAR_X, BAR_Y, BAR_W, BAR_H, new Color(0.05f, 0.08f, 0.2f, 1f));
        bar = rect(BAR_X, BAR_Y, 1f, BAR_H, new Color(0.35f, 0.85f, 0.75f, 1f));
        status = outlinedText("Downloading...  0%", 0, BAR_Y + 45f, VIRTUAL_WIDTH, 40, 1.9f,
                new Color(0.9f, 0.94f, 1f, 1f), Align.center);

        worker = new Thread(() -> {
            try {
                installer = updater.download(release, p -> progress = p);
            } catch (IOException | RuntimeException e) {
                System.err.println("Update failed: " + e);
                failed = true;
            } catch (InterruptedException e) {
                failed = true;
            }
        }, "codequest-update");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(float delta) {
        if (finished) {
            return;
        }
        if (failed) {
            sinceFailed += delta;
            status.setText("Could not update now. Starting the game...");
            if (sinceFailed > FAILED_SECONDS) {
                finished = true;
                game.setScreen(new LoginScreen(game));
                return;
            }
        } else if (installer != null) {
            try {
                updater.install(release, installer);
                finished = true;
                Gdx.app.exit();
                return;
            } catch (IOException e) {
                System.err.println("Update could not be started: " + e);
                failed = true;
            }
        } else {
            double now = progress;
            if (now != lastProgress) {
                lastProgress = now;
                sinceProgress = 0f;
            } else {
                sinceProgress += delta;
                if (sinceProgress > STALL_SECONDS) {
                    worker.interrupt();
                    failed = true;
                }
            }
            bar.setWidth(Math.max(1f, BAR_W * (float) now));
            status.setText(now >= 1 ? "Installing..." : "Downloading...  " + (int) (now * 100) + "%");
        }
        super.render(delta);
    }
}
