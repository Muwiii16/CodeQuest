package codequest;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import codequest.screens.IntroScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {

    /** The window the game returns to when full screen is switched off. */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    @Override
    public void create() {
        setScreen(new IntroScreen(this));
    }

    /** F11, or Alt+Enter, switches between full screen and a window (the game starts full screen). */
    @Override
    public void render() {
        boolean altEnter = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                && (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT));
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11) || altEnter) {
            toggleFullscreen();
        }
        super.render();
    }

    private static void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        } else {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        codequest.screens.Assets.disposeAll();
        codequest.game.SchoolRoomPainter.disposeAll();
    }

    /** Disposes the outgoing screen's textures once the new one has taken over. */
    @Override
    public void setScreen(Screen screen) {
        Screen previous = getScreen();
        super.setScreen(screen);
        if (previous != null) {
            previous.dispose();
        }
    }
}
