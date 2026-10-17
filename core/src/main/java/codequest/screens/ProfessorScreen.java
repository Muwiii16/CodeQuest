package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

/**
 * Shared frame for every professor screen: the professor look (charcoal and
 * amber, so it can't be mistaken for the student menus), a top bar with the
 * screen title and account, a back/log-out button, and a status toast.
 */
public abstract class ProfessorScreen extends BaseScreen {

    protected static final Color AMBER = new Color(0xF5A524FF);
    protected static final Color BAR = new Color(0.07f, 0.08f, 0.11f, 1f);
    protected static final Color CARD = new Color(0.13f, 0.15f, 0.2f, 0.96f);
    protected static final Color ROW = new Color(0.1f, 0.12f, 0.17f, 0.96f);
    protected static final Color BUTTON = new Color(0.2f, 0.24f, 0.33f, 1f);
    protected static final Color GOOD = new Color(0.16f, 0.45f, 0.3f, 1f);
    protected static final Color MUTED = new Color(0.62f, 0.66f, 0.76f, 1f);

    protected final Main game;
    protected final String username;

    /** backTarget null means this is the dashboard: the top-right button logs out instead. */
    protected ProfessorScreen(Main game, String username, String title, Runnable backTarget) {
        this.game = game;
        this.username = username;
        setBackground(Art.BG_PROFESSOR);

        rect(0, 0, VIRTUAL_WIDTH, 110, BAR);
        rect(0, 107, VIRTUAL_WIDTH, 3, AMBER);
        text("PROFESSOR", 50, 22, 330, 30, 1.3f, AMBER, Align.left);
        text(title, 50, 48, 1100, 50, 2.6f, Color.WHITE, Align.left);
        text(username, VIRTUAL_WIDTH - 720, 40, 360, 30, 1.5f, MUTED, Align.right);
        if (backTarget != null) {
            solidButton("Back", VIRTUAL_WIDTH - 330, 22, 280, 66, BUTTON, backTarget);
        } else {
            solidButton("Log out", VIRTUAL_WIDTH - 330, 22, 280, 66, new Color(0.45f, 0.18f, 0.18f, 1f),
                    () -> game.setScreen(new LoginScreen(game)));
        }
    }

    protected Image card(float x, float y, float w, float h) {
        return rect(x, y, w, h, CARD);
    }
}
