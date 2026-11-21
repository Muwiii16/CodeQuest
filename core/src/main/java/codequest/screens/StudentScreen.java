package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;

/**
 * Shared frame for the student's menu screens: a navy title bar with a cyan outline, a name chip, a Back button, and
 * panels, all from the picture kit (assets/ui/kit).
 */
public abstract class StudentScreen extends BaseScreen {

    /** The cyan of the outlines and the active tabs. */
    protected static final Color CYAN = new Color(0.18f, 0.78f, 0.95f, 1f);
    protected static final Color TEAL = new Color(0x2EC4B6FF);
    protected static final Color BAR = new Color(0.03f, 0.07f, 0.19f, 0.98f);
    protected static final Color CARD = new Color(0.05f, 0.1f, 0.26f, 0.95f);
    protected static final Color ROW = new Color(0.05f, 0.09f, 0.22f, 0.96f);
    protected static final Color BUTTON = new Color(0.16f, 0.22f, 0.46f, 1f);
    protected static final Color GOOD = new Color(0.1f, 0.56f, 0.4f, 1f);
    protected static final Color MUTED = new Color(0.66f, 0.74f, 0.92f, 1f);
    protected static final Color GOLD = new Color(1f, 0.8f, 0.3f, 1f);
    /** The active tab: bright blue with a cyan feel. */
    protected static final Color TAB_ON = new Color(0.12f, 0.42f, 0.86f, 1f);

    protected final Main game;
    protected final String username;

    protected StudentScreen(Main game, String username, String title, Runnable back) {
        this.game = game;
        this.username = username;
        setBackground(Art.BG_MENU);
        // a soft dark veil over the bright campus picture, so the text that sits on it can be read
        rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(0.01f, 0.03f, 0.12f, 0.6f)).setTouchable(Touchable.disabled);

        // the title bar: the header picture across the top, a graduation cap, the screen name
        Image bar = new Image(UiKit.HEADER.drawable());
        placeTopLeft(bar, -10, -14, VIRTUAL_WIDTH + 20, 138);
        stage.addActor(bar);
        bar.setTouchable(Touchable.disabled);
        icon("cap", 34, 12, 84);
        text("STUDENT", 140, 20, 330, 30, 1.3f, CYAN, Align.left);
        text(title, 140, 46, 1000, 50, 2.6f, Color.WHITE, Align.left);
        // the name sits in the chip picture, like the one on the main menu
        Image chip = new Image(UiKit.CHIP.drawable());
        placeTopLeft(chip, VIRTUAL_WIDTH - 720, 22, 360, 66);
        stage.addActor(chip);
        chip.setTouchable(Touchable.disabled);
        text(username, VIRTUAL_WIDTH - 710, 30, 340, 54, 1.5f, Color.WHITE, Align.center);
        solidButton("<  Back", VIRTUAL_WIDTH - 330, 22, 280, 66, new Color(0.12f, 0.42f, 0.86f, 1f), back);
    }

    /** An icon picture (assets/ui/kit/icon_NAME.png) at the given top-left position and size. */
    protected Image icon(String name, float x, float yFromTop, float size) {
        Image image = new Image(texture(UiKit.icon(name)));
        placeTopLeft(image, x, yFromTop, size, size);
        image.setTouchable(Touchable.disabled);
        stage.addActor(image);
        return image;
    }

    /** A panel: the navy picture with the cyan outline (a thinner one for short panels). Returns the picture. */
    protected Image card(float x, float y, float w, float h) {
        if (h < 56 || w < 100) {
            return rect(x, y, w, h, CARD);
        }
        boolean big = h >= 150 && w >= 200;
        Image panel = new Image((big ? UiKit.PANEL : UiKit.STRIP).drawable());
        float pad = big ? 6 : 4;
        placeTopLeft(panel, x - pad, y - pad, w + pad * 2, h + pad * 2);
        stage.addActor(panel);
        return panel;
    }
}
