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
import com.badlogic.gdx.scenes.scene2d.Group;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    protected BaseScreen() {
        stage = new Stage(new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT));
    }

    /** A picture from assets/, from the shared cache (see {@link Assets}): it stays loaded, so a screen must not dispose it. */
    protected Texture texture(String assetPath) {
        return Assets.texture(assetPath);
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
        ImageButton button = new ImageButton(style);
        // the picture fills whatever size the button is given (the pixel-art buttons are small and are shown enlarged)
        button.getImageCell().expand().fill();
        button.getImage().setScaling(com.badlogic.gdx.utils.Scaling.stretch);
        return button;
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

    /** The shared pixel font at its normal size (about 15 px). */
    protected BitmapFont defaultFont() {
        if (sharedFont == null) {
            sharedFont = PixelFonts.forScale(1f, false);
        }
        return sharedFont;
    }

    /** Index to pass to {@link #clearFrom(int)} later: everything added after this point is rebuildable. */
    protected int mark() {
        return stage.getActors().size;
    }

    protected void clearFrom(int mark) {
        // the loading cover (if one is up) stays: a rebuild that finishes while another load runs must not lift it
        for (int i = stage.getActors().size - 1; i >= mark; i--) {
            Actor a = stage.getActors().get(i);
            if (a != loadingCover) {
                a.remove();
            }
        }
    }

    // ── regions: groups of actors that can be cleared and redrawn on their own ──

    private Group target;

    /** Adds an actor to the region being drawn, or to the stage when no region is active. */
    private void put(com.badlogic.gdx.scenes.scene2d.Actor actor) {
        if (target != null) {
            target.addActor(actor);
        } else {
            stage.addActor(actor);
        }
    }

    /** An empty group on the stage (at the origin, so layout coordinates stay absolute) to draw a part of a screen into. */
    protected Group region() {
        Group g = new Group();
        stage.addActor(g);
        return g;
    }

    /** Clears the region and runs {@code build}, which draws into it with the usual helpers (rect, text, solidButton...). */
    protected void fill(Group region, Runnable build) {
        region.clearChildren();
        Group previous = target;
        target = region;
        try {
            build.run();
        } finally {
            target = previous;
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
        put(image);
        return image;
    }

    /** A text label inside the given box (top-left coordinates), added to the stage. Plain pixel text, for use on solid panels. */
    protected Label text(String text, float x, float yFromTop, float w, float h, float scale, Color color, int align) {
        return makeText(text, x, yFromTop, w, h, scale, color, align, false);
    }

    /** Like {@link #text} but with a dark outline, so it stays readable over a picture (backgrounds, the map). */
    protected Label outlinedText(String text, float x, float yFromTop, float w, float h, float scale, Color color, int align) {
        return makeText(text, x, yFromTop, w, h, scale, color, align, true);
    }

    /**
     * Like {@link #text} but in a smooth readable face: SANS for sentences, MONO for code (PIXEL is the same as
     * {@link #text}). Used where someone has to read questions, answers or code.
     */
    protected Label text(String text, float x, float yFromTop, float w, float h, float scale, Color color, int align,
            PixelFonts.Face face) {
        return makeLabel(text, x, yFromTop, w, h, PixelFonts.font(face, scale), color, align);
    }

    private Label makeText(String text, float x, float yFromTop, float w, float h, float scale, Color color, int align,
            boolean outlined) {
        return makeLabel(text, x, yFromTop, w, h, PixelFonts.forScale(scale, outlined), color, align);
    }

    private Label makeLabel(String text, float x, float yFromTop, float w, float h, BitmapFont font, Color color,
            int align) {
        Label label = new Label(text, new Label.LabelStyle(font, color));
        label.setAlignment(align);
        label.setTouchable(Touchable.disabled);
        placeTopLeft(label, x, yFromTop, w, h);
        put(label);
        return label;
    }

    /** A multi-line text box on a dark box with a hint shown while empty. */
    protected com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea(String hint, float x, float yFromTop, float w, float h) {
        return textArea(hint, x, yFromTop, w, h, PixelFonts.Face.PIXEL);
    }

    protected com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea(String hint, float x, float yFromTop, float w, float h,
            PixelFonts.Face face) {
        rect(x, yFromTop, w, h, new Color(0.04f, 0.05f, 0.1f, 1f));
        Texture cursor = solidTexture(Color.WHITE);
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = PixelFonts.font(face, 1.5f);
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
        put(area);
        return area;
    }

    // ── loading: run a slow (database) job off the render thread, with a "Loading..." cover meanwhile ──

    private static final ExecutorService IO = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "codequest-io");
        t.setDaemon(true);
        return t;
    });

    private boolean disposed;

    /** True once this screen was left: background work must not touch it any more. */
    protected boolean isDisposed() {
        return disposed;
    }
    private int pendingLoads;
    private Group loadingCover;

    /**
     * Runs {@code work} on a background thread (it may block on the network), then calls {@code done} with the
     * result on the render thread. A dimmed cover with a spinner blocks clicks until it finishes, so the
     * window never freezes. If the work throws, a message is shown and {@code done} is not called.
     */
    protected <T> void load(String message, Supplier<T> work, Consumer<T> done) {
        showLoadingCover(message);
        IO.submit(() -> {
            T result = null;
            Throwable error = null;
            try {
                result = work.get();
            } catch (Throwable t) {
                error = t;
            }
            T finalResult = result;
            Throwable finalError = error;
            Gdx.app.postRunnable(() -> {
                hideLoadingCover();
                if (disposed) {
                    return;
                }
                if (finalError != null) {
                    Gdx.app.error("CodeQuest", "Background load failed", finalError);
                    showError("Could not reach the server", "Check your internet connection and try again. Nothing was lost.");
                    return;
                }
                done.accept(finalResult);
            });
        });
    }

    /** Same, for work whose result is not needed. */
    protected void run(String message, Runnable work, Runnable done) {
        load(message, () -> {
            work.run();
            return Boolean.TRUE;
        }, ignored -> done.run());
    }

    private void showLoadingCover(String message) {
        pendingLoads++;
        if (loadingCover != null && loadingCover.hasParent()) {
            return;
        }
        loadingCover = new Group();
        Image dim = new Image(whiteDrawable());
        dim.setColor(0f, 0f, 0f, 0f);
        dim.setBounds(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        // fade in after a short delay so quick loads do not flash the cover
        dim.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.2f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0.55f, 0.15f)));
        loadingCover.addActor(dim);

        Image spinner = new Image(whiteDrawable());
        spinner.setColor(0.9f, 0.95f, 1f, 0f);
        spinner.setBounds(VIRTUAL_WIDTH / 2f - 24, VIRTUAL_HEIGHT / 2f + 10, 48, 48);
        spinner.setOrigin(24, 24);
        spinner.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.2f),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(1f, 0.15f)),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.rotateBy(-360f, 1.1f))));
        loadingCover.addActor(spinner);

        Label label = new Label(message, new Label.LabelStyle(PixelFonts.forScale(1.8f, true), Color.WHITE));
        label.setAlignment(Align.center);
        label.setBounds(0, VIRTUAL_HEIGHT / 2f - 70, VIRTUAL_WIDTH, 50);
        label.getColor().a = 0f;
        label.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.2f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(1f, 0.15f)));
        loadingCover.addActor(label);
        stage.addActor(loadingCover);
    }

    private void hideLoadingCover() {
        pendingLoads = Math.max(0, pendingLoads - 1);
        if (pendingLoads == 0 && loadingCover != null) {
            loadingCover.remove();
            loadingCover = null;
        }
    }

    /** True while a {@link #load} is still running. */
    protected boolean isLoading() {
        return pendingLoads > 0;
    }

    private Label toastLabel;
    private Group errorPopup;
    private Runnable popupOnClose;

    /**
     * A message the user has to acknowledge: the screen is dimmed and a box says what is wrong until OK is pressed (or Enter,
     * Space or Esc). Used for every error and every "something is missing" in the professor screens.
     */
    protected void showError(String title, String message) {
        showPopup(title, message, new Color(0.9f, 0.3f, 0.27f, 1f), null);
    }

    /** The same pop-up for good news or an instruction (a green accent); {@code onClose} runs when it is dismissed. */
    protected void showNotice(String title, String message, Runnable onClose) {
        showPopup(title, message, new Color(0.25f, 0.72f, 0.5f, 1f), onClose);
    }

    private void showPopup(String title, String message, Color accent, Runnable onClose) {
        closeErrorPopup();
        popupOnClose = onClose;
        if (toastLabel != null) {
            toastLabel.remove();
            toastLabel = null;
        }
        String shown = message == null || message.isBlank() ? "Something went wrong." : message.trim();
        if (shown.length() > 480) {
            shown = shown.substring(0, 477) + "...";
        }
        Group popup = region();
        errorPopup = popup;
        String body = shown;
        fill(popup, () -> {
            float w = 960f;
            float h = 440f;
            float x = (VIRTUAL_WIDTH - w) / 2f;
            float y = (VIRTUAL_HEIGHT - h) / 2f;
            Image dim = rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(0f, 0f, 0f, 0.65f)); // takes every click meanwhile
            rect(x - 4, y - 4, w + 8, h + 8, new Color(accent.r * 0.6f, accent.g * 0.6f, accent.b * 0.6f, 1f));
            rect(x, y, w, h, new Color(0.1f, 0.12f, 0.2f, 1f));
            rect(x, y, w, 10, accent);
            text(title, x + 44, y + 36, w - 88, 52, 2.1f, Color.WHITE, Align.left).setTouchable(Touchable.disabled);
            text(body, x + 44, y + 112, w - 88, 210, 1.55f, new Color(0.88f, 0.9f, 0.97f, 1f), Align.topLeft,
                    PixelFonts.Face.SANS).setWrap(true);
            solidButton("OK", x + w / 2f - 150f, y + h - 92f, 300, 62, new Color(0.2f, 0.42f, 0.78f, 1f), this::closeErrorPopup);
            dim.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.ENTER || keycode == com.badlogic.gdx.Input.Keys.ESCAPE
                            || keycode == com.badlogic.gdx.Input.Keys.SPACE) {
                        closeErrorPopup();
                        return true;
                    }
                    return false;
                }
            });
            stage.setKeyboardFocus(dim);
        });
    }

    protected void closeErrorPopup() {
        if (errorPopup != null) {
            errorPopup.remove();
            errorPopup = null;
            stage.setKeyboardFocus(null);
            Runnable after = popupOnClose;
            popupOnClose = null;
            if (after != null) {
                after.run();
            }
        }
    }

    /** A short status line at the bottom of the screen that fades away after a few seconds. */
    protected void toast(String message, boolean ok) {
        if (!ok) {
            // a problem the user must notice: a pop-up that stays until it is dismissed, not a line that fades after a few seconds
            showError("Please check this", message);
            return;
        }
        if (toastLabel != null) {
            toastLabel.remove();
        }
        toastLabel = text(message, 0, 1030, VIRTUAL_WIDTH, 40, 1.6f,
                ok ? new Color(0.55f, 0.9f, 0.65f, 1f) : new Color(1f, 0.55f, 0.5f, 1f), Align.center);
        toastLabel.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(4f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()));
    }

    /** A flat colored button with a darker outline (no glow, no hover color); returns the clickable fill. */
    protected Image solidButton(String text, float x, float yFromTop, float w, float h, Color fill, Runnable action) {
        return solidButton(text, x, yFromTop, w, h, fill, Color.WHITE, action);
    }

    protected Image solidButton(String text, float x, float yFromTop, float w, float h, Color fill, Color textColor,
            Runnable action) {
        return solidButton(text, x, yFromTop, w, h, fill, textColor, PixelFonts.Face.PIXEL, action);
    }

    /** Pass this as the action of a button that does nothing (locked, selected, a preview): it gets no hover effect. */
    protected static final Runnable INERT = () -> { };

    /** How far a button rises when the mouse is over it (pixels), and how long that takes. */
    private static final float HOVER_LIFT = 8f;
    private static final float HOVER_SECONDS = 0.07f;

    /**
     * A solid button whose label is in the given face (MONO for a button that shows code). Flat: the outline is a darker
     * shade of the button. When the mouse is over a clickable button it rises a few pixels over a soft shadow and the
     * cursor becomes a hand; the colors never change.
     */
    protected Image solidButton(String text, float x, float yFromTop, float w, float h, Color fill, Color textColor,
            PixelFonts.Face face, Runnable action) {
        boolean live = action != INERT;
        Image shadow = null;
        if (live) {
            shadow = rect(x + 5, yFromTop + 10, w - 10, h - 4, new Color(0f, 0f, 0f, 0f));
            shadow.setTouchable(Touchable.disabled);
        }
        Color border = new Color(fill.r * 0.7f, fill.g * 0.7f, fill.b * 0.7f, 1f);
        Image frame = rect(x, yFromTop, w, h, border);
        frame.setTouchable(Touchable.disabled);
        Image body = rect(x + 3, yFromTop + 3, w - 6, h - 6, fill);
        body.setTouchable(Touchable.disabled);
        Label label = text(text, x, yFromTop, w, h, Math.min(1.9f, h / 38f), textColor, Align.center, face);
        label.setTouchable(Touchable.disabled);
        // the part that takes the mouse stays where the button was, so a raised button never flickers at its lower edge
        Image hit = rect(x, yFromTop, w, h, new Color(0f, 0f, 0f, 0f));
        Image shade = shadow;
        // everything that rises with the button, with the y it rests at; rideAlong adds a screen's own icons and text
        java.util.Map<Actor, Float> movers = new java.util.LinkedHashMap<>();
        movers.put(frame, frame.getY());
        movers.put(body, body.getY());
        movers.put(label, label.getY());
        hit.setUserObject(movers);
        hit.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (!live || pointer != -1) {
                    return;
                }
                movers.forEach((a, restY) -> rise(a, restY + HOVER_LIFT));
                shade.clearActions();
                shade.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0.55f, HOVER_SECONDS));
                Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Hand);
            }

            @Override
            public void exit(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                if (!live || pointer != -1) {
                    return;
                }
                movers.forEach((a, restY) -> rise(a, restY));
                shade.clearActions();
                shade.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0f, HOVER_SECONDS));
                Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow);
            }

            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                action.run();
            }
        });
        return hit;
    }

    /**
     * Makes actors drawn on top of a {@link #solidButton} (an icon, extra text, a stripe) rise and fall with it.
     * Pass the Image that solidButton returned; the actors must already be placed where they rest.
     */
    @SuppressWarnings("unchecked")
    protected void rideAlong(Image button, Actor... actors) {
        if (!(button.getUserObject() instanceof java.util.Map<?, ?> movers)) {
            return;
        }
        for (Actor a : actors) {
            ((java.util.Map<Actor, Float>) movers).put(a, a.getY());
        }
    }

    private static void rise(com.badlogic.gdx.scenes.scene2d.Actor actor, float toY) {
        actor.clearActions();
        actor.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(actor.getX(), toY, HOVER_SECONDS,
                com.badlogic.gdx.math.Interpolation.fade));
    }

    /** A text input on a dark box with a hint shown while empty. */
    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h) {
        return textField(hint, password, x, yFromTop, w, h, new Color(0.04f, 0.05f, 0.1f, 1f),
                new Color(1f, 1f, 1f, 0.25f), Color.WHITE, new Color(0.6f, 0.62f, 0.7f, 1f));
    }

    /** Same input with custom colors (box, underline, typed text, hint text), e.g. for light panels. */
    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h,
            Color box, Color underline, Color textColor, Color hintColor) {
        return textField(hint, password, x, yFromTop, w, h, box, underline, textColor, hintColor, PixelFonts.Face.PIXEL);
    }

    /** The same input with default colors in the given face (SANS for sentences, MONO for code). */
    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h,
            PixelFonts.Face face) {
        return textField(hint, password, x, yFromTop, w, h, new Color(0.04f, 0.05f, 0.1f, 1f),
                new Color(1f, 1f, 1f, 0.25f), Color.WHITE, new Color(0.6f, 0.62f, 0.7f, 1f), face);
    }

    protected TextField textField(String hint, boolean password, float x, float yFromTop, float w, float h,
            Color box, Color underline, Color textColor, Color hintColor, PixelFonts.Face face) {
        rect(x, yFromTop, w, h, box);
        rect(x, yFromTop + h - 3, w, 3, underline);
        Texture cursor = solidTexture(Color.WHITE);
        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = PixelFonts.font(face, Math.min(1.8f, h / 34f));
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
        put(field);
        return field;
    }

    /** Changes the face a text box is typed in (for boxes whose content changes kind, e.g. prose then code). */
    protected void setFieldFace(TextField field, PixelFonts.Face face) {
        TextField.TextFieldStyle style = field.getStyle();
        float scale = field instanceof com.badlogic.gdx.scenes.scene2d.ui.TextArea ? 1.5f
                : Math.min(1.8f, field.getHeight() / 34f);
        style.font = PixelFonts.font(face, scale);
        field.setStyle(style);
    }

    /** The pixel font at the given scale (for TextFields, which can't scale per-widget). Shared; do not dispose it. */
    protected BitmapFont scaledFont(float scale) {
        return PixelFonts.forScale(scale, false);
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
        Label label = new Label(text, new Label.LabelStyle(PixelFonts.forScale(fontScale, true), color));
        label.pack();
        placeTopLeft(label, (VIRTUAL_WIDTH - label.getWidth()) / 2f, yFromTop - label.getHeight() / 2f,
                label.getWidth(), label.getHeight());
        put(label);
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
            // the batch keeps the color of the last thing it drew (a button's invisible hit layer is transparent), which
            // would tint or hide the background
            stage.getBatch().setColor(Color.WHITE);
            stage.getBatch().begin();
            stage.getBatch().draw(background, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            stage.getBatch().end();
        }

        if (loadingCover != null && loadingCover.hasParent()) {
            loadingCover.toFront(); // anything added since the cover went up must still sit under it
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
        disposed = true;
        Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow);
        stage.dispose();
        for (Texture t : ownedTextures) {
            t.dispose();
        }
        // the pixel fonts are shared by all screens (PixelFonts), so they are not disposed here
    }
}
