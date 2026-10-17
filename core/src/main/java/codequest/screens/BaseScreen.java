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
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
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
    private Texture whiteTexture;
    private final List<BitmapFont> extraFonts = new ArrayList<>();

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

    /** Index to pass to {@link #clearFrom(int)} later: everything added after this point is rebuildable. */
    protected int mark() {
        return stage.getActors().size;
    }

    protected void clearFrom(int mark) {
        while (stage.getActors().size > mark) {
            Actor a = stage.getActors().peek();
            a.remove();
        }
    }

    // ── plain-shape UI helpers (flat colour rects, text, buttons, inputs) ──

    private TextureRegionDrawable whiteDrawable() {
        if (whiteTexture == null) {
            whiteTexture = solidTexture(Color.WHITE);
        }
        return new TextureRegionDrawable(new TextureRegion(whiteTexture));
    }

    /** A flat colored rectangle (a tinted 1x1 white texture), added to the stage. */
    protected Image rect(float x, float yFromTop, float w, float h, Color color) {
        Image image = new Image(whiteDrawable());
        image.setColor(color);
        placeTopLeft(image, x, yFromTop, w, h);
        stage.addActor(image);
        return image;
    }

    /** A text label inside the given box (top-left coordinates), added to the stage. */
    protected Label text(String text, float x, float yFromTop, float w, float h, float scale, Color color, int align) {
        Label label = new Label(text, new Label.LabelStyle(defaultFont(), color));
        label.setFontScale(scale);
        label.setAlignment(align);
        label.setTouchable(Touchable.disabled);
        placeTopLeft(label, x, yFromTop, w, h);
        stage.addActor(label);
        return label;
    }

    /** A multi-line text box on a dark box with a hint shown while empty. */
    protected com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea(String hint, float x, float yFromTop, float w, float h) {
        rect(x, yFromTop, w, h, new Color(0.04f, 0.05f, 0.1f, 1f));
        Texture cursor = solidTexture(Color.WHITE);
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = scaledFont(1.5f);
        style.fontColor = Color.WHITE;
        style.messageFontColor = new Color(0.6f, 0.62f, 0.7f, 1f);
        TextureRegionDrawable pad = new TextureRegionDrawable(new TextureRegion(solidTexture(new Color(0, 0, 0, 0))));
        pad.setLeftWidth(16f);
        pad.setTopHeight(12f);
        style.background = pad;
        style.cursor = new TextureRegionDrawable(new TextureRegion(cursor));
        style.selection = new TextureRegionDrawable(new TextureRegion(cursor));
        com.badlogic.gdx.scenes.scene2d.ui.TextArea area = new com.badlogic.gdx.scenes.scene2d.ui.TextArea("", style);
        area.setMessageText(hint);
        placeTopLeft(area, x, yFromTop, w, h);
        stage.addActor(area);
        return area;
    }

    private Label toastLabel;

    /** A short status line at the bottom of the screen that fades away after a few seconds. */
    protected void toast(String message, boolean ok) {
        if (toastLabel != null) {
            toastLabel.remove();
        }
        toastLabel = text(message, 0, 1030, VIRTUAL_WIDTH, 40, 1.6f,
                ok ? new Color(0.55f, 0.9f, 0.65f, 1f) : new Color(1f, 0.55f, 0.5f, 1f), Align.center);
        toastLabel.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(4f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()));
    }

    /** A flat colored button with a border and a hover color; returns the clickable fill. */
    protected Image solidButton(String text, float x, float yFromTop, float w, float h, Color fill, Runnable action) {
        return solidButton(text, x, yFromTop, w, h, fill, Color.WHITE, action);
    }

    protected Image solidButton(String text, float x, float yFromTop, float w, float h, Color fill, Color textColor,
            Runnable action) {
        Color border = new Color(Math.min(1f, fill.r + 0.25f), Math.min(1f, fill.g + 0.25f),
                Math.min(1f, fill.b + 0.25f), 1f);
        Color hover = new Color(Math.min(1f, fill.r + 0.12f), Math.min(1f, fill.g + 0.12f),
                Math.min(1f, fill.b + 0.12f), 1f);
        rect(x, yFromTop, w, h, border).setTouchable(Touchable.disabled);
        Image body = rect(x + 3, yFromTop + 3, w - 6, h - 6, fill);
        body.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                body.setColor(hover);
            }

            @Override
            public void exit(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                body.setColor(fill);
            }

            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                action.run();
            }
        });
        text(text, x, yFromTop, w, h, Math.min(1.9f, h / 38f), textColor, Align.center);
        return body;
    }

    /** A text input on a dark box with a hint shown while empty. */
    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h) {
        return textField(hint, password, x, yFromTop, w, h, new Color(0.04f, 0.05f, 0.1f, 1f),
                new Color(1f, 1f, 1f, 0.25f), Color.WHITE, new Color(0.6f, 0.62f, 0.7f, 1f));
    }

    /** Same input with custom colors (box, underline, typed text, hint text), e.g. for light panels. */
    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h,
            Color box, Color underline, Color textColor, Color hintColor) {
        rect(x, yFromTop, w, h, box);
        rect(x, yFromTop + h - 3, w, 3, underline);
        Texture cursor = solidTexture(Color.WHITE);
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = scaledFont(Math.min(1.8f, h / 34f));
        style.fontColor = textColor;
        style.messageFontColor = hintColor;
        TextureRegionDrawable pad = new TextureRegionDrawable(new TextureRegion(solidTexture(new Color(0, 0, 0, 0))));
        pad.setLeftWidth(16f);
        style.background = pad;
        style.cursor = new TextureRegionDrawable(new TextureRegion(cursor));
        style.selection = new TextureRegionDrawable(new TextureRegion(cursor));
        TextField field = new TextField("", style);
        field.setMessageText(hint);
        if (password) {
            field.setPasswordCharacter('*');
            field.setPasswordMode(true);
        }
        placeTopLeft(field, x, yFromTop, w, h);
        stage.addActor(field);
        return field;
    }

    /** A separate default-style font at the given scale (for TextFields, which can't scale per-widget). */
    protected BitmapFont scaledFont(float scale) {
        BitmapFont font = new BitmapFont();
        font.getData().setScale(scale);
        extraFonts.add(font);
        return font;
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
        extraFonts.forEach(BitmapFont::dispose);
    }
}
