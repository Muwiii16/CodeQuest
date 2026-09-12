package codequest;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import codequest.screens.LoginScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {

    @Override
    public void create() {
        setScreen(new LoginScreen(this));
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
