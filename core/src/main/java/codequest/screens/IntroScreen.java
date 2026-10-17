package codequest.screens;

import codequest.Main;
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

/**
 * First screen of the game: the logo fades in over the intro background, then
 * waits for a click or key press (or moves on by itself after a few seconds).
 */
public class IntroScreen extends BaseScreen {

    private static final float AUTO_ADVANCE_SECONDS = 7f;

    private final Main game;
    private float elapsed;
    private boolean leaving;

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

        Label prompt = new Label("Click anywhere or press any key to start",
                new Label.LabelStyle(defaultFont(), new Color(0.82f, 0.87f, 1f, 1f)));
        prompt.setFontScale(2.2f);
        prompt.pack();
        placeTopLeft(prompt, (VIRTUAL_WIDTH - prompt.getWidth()) / 2f, 800f, prompt.getWidth(), prompt.getHeight());
        prompt.getColor().a = 0f;
        prompt.addAction(Actions.sequence(Actions.delay(1.8f), Actions.forever(Actions.sequence(
                Actions.alpha(1f, 0.9f), Actions.alpha(0.25f, 0.9f)))));
        stage.addActor(prompt);

        Label note = new Label("placeholder art - final assets coming soon",
                new Label.LabelStyle(defaultFont(), new Color(1f, 1f, 1f, 0.35f)));
        note.setFontScale(1.3f);
        note.pack();
        placeTopLeft(note, (VIRTUAL_WIDTH - note.getWidth()) / 2f, 1010f, note.getWidth(), note.getHeight());
        stage.addActor(note);

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
        elapsed += delta;
        if (elapsed > AUTO_ADVANCE_SECONDS || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            advance();
        }
        if (leaving) {
            return; // the next screen has taken over and this one is disposed: do not draw it
        }
        super.render(delta);
    }

    private void advance() {
        if (leaving || elapsed < 0.5f) {
            return;
        }
        leaving = true;
        game.setScreen(new LoginScreen(game));
    }
}
