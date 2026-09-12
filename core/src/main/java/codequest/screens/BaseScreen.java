package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene2D analogue of the Swing prototype's BasePanel: a fixed 1920x1080 design
 * surface (matching the original ScreenUtils scaling base) that the FitViewport
 * scales to whatever window size is actually open, a shared background texture,
 * and a helper for building the def/hover image buttons the menus use.
 */
public abstract class BaseScreen implements Screen {

    public static final float VIRTUAL_WIDTH = 1920f;
    public static final float VIRTUAL_HEIGHT = 1080f;

    protected final Stage stage;
    protected Texture background;

    private final List<Texture> ownedTextures = new ArrayList<>();
    private BitmapFont sharedFont;

    protected BaseScreen() {
        stage = new Stage(new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT));
    }

    /** Loads a texture from assets/ and tracks it so dispose() cleans it up. */
    protected Texture texture(String assetPath) {
        Texture t = new Texture(Gdx.files.internal(assetPath));
        ownedTextures.add(t);
        return t;
    }

    protected void setBackground(String assetPath) {
        background = texture(assetPath);
    }

    /**
     * Positions an actor using the same top-left, y-grows-downward coordinates
     * the Swing panels used, so layout numbers can be copied over unchanged.
     */
    protected void placeTopLeft(Actor actor, float x, float yFromTop, float width, float height) {
        actor.setBounds(x, VIRTUAL_HEIGHT - yFromTop - height, width, height);
    }

    /** Builds an image button that swaps to a hover texture, mirroring BasePanel#createImageButton. */
    protected ImageButton imageButton(String normalPath, String hoverPath) {
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = new TextureRegionDrawable(new TextureRegion(texture(normalPath)));
        if (hoverPath != null) {
            style.imageOver = new TextureRegionDrawable(new TextureRegion(texture(hoverPath)));
        }
        return new ImageButton(style);
    }

    /** A tracked 1x1 solid-color texture, handy for cursors/selection drawables/toasts. */
    protected Texture solidTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture t = new Texture(pixmap);
        pixmap.dispose();
        ownedTextures.add(t);
        return t;
    }

    /** Lazily-created shared bitmap font used for all text on this screen. */
    protected BitmapFont defaultFont() {
        if (sharedFont == null) {
            sharedFont = new BitmapFont();
        }
        return sharedFont;
    }

    /** A non-interactive, semi-transparent image for a locked/disabled slot (e.g. a locked stage). */
    protected Image lockedImage(String assetPath) {
        Image image = new Image(new TextureRegionDrawable(new TextureRegion(texture(assetPath))));
        image.getColor().a = 0.55f;
        return image;
    }

    /**
     * Text title, added to the stage and centered horizontally, standing in for
     * BasePanel#drawTitle's text overload until the MedievalSharp font is wired up
     * via gdx-freetype. yFromTop is the same top-down coordinate placeTopLeft uses.
     */
    protected Label addCenteredTitle(String text, float yFromTop, float fontScale, Color color) {
        Label label = new Label(text, new Label.LabelStyle(defaultFont(), color));
        label.setFontScale(fontScale);
        label.pack();
        placeTopLeft(label, (VIRTUAL_WIDTH - label.getWidth()) / 2f, yFromTop - label.getHeight() / 2f,
                label.getWidth(), label.getHeight());
        stage.addActor(label);
        return label;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);

        stage.getViewport().apply();
        if (background != null) {
            stage.getBatch().begin();
            stage.getBatch().draw(background, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            stage.getBatch().end();
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        for (Texture t : ownedTextures) {
            t.dispose();
        }
        if (sharedFont != null) {
            sharedFont.dispose();
        }
    }
}
