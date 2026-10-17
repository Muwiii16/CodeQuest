package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;

/** Shared frame for the student's menu screens: navy placeholder look, a title bar, and a back button. */
public abstract class StudentScreen extends BaseScreen {

    protected static final Color TEAL = new Color(0x2EC4B6FF);
    protected static final Color BAR = new Color(0.04f, 0.06f, 0.15f, 0.97f);
    protected static final Color CARD = new Color(0.08f, 0.11f, 0.24f, 0.94f);
    protected static final Color ROW = new Color(0.06f, 0.08f, 0.19f, 0.96f);
    protected static final Color BUTTON = new Color(0.16f, 0.22f, 0.46f, 1f);
    protected static final Color GOOD = new Color(0.14f, 0.42f, 0.34f, 1f);
    protected static final Color MUTED = new Color(0.62f, 0.68f, 0.88f, 1f);
    protected static final Color GOLD = new Color(1f, 0.8f, 0.3f, 1f);

    protected final Main game;
    protected final String username;

    protected StudentScreen(Main game, String username, String title, Runnable back) {
        this.game = game;
        this.username = username;
        setBackground(Art.BG_MENU);

        rect(0, 0, VIRTUAL_WIDTH, 110, BAR);
        rect(0, 107, VIRTUAL_WIDTH, 3, TEAL);
        text("STUDENT", 50, 22, 330, 30, 1.3f, TEAL, Align.left);
        text(title, 50, 48, 1100, 50, 2.6f, Color.WHITE, Align.left);
        text(username, VIRTUAL_WIDTH - 720, 40, 360, 30, 1.5f, MUTED, Align.right);
        solidButton("Back", VIRTUAL_WIDTH - 330, 22, 280, 66, BUTTON, back);
    }

    protected Image card(float x, float y, float w, float h) {
        return rect(x, y, w, h, CARD);
    }
}
