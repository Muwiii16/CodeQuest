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
                    () -> {
                        new codequest.data.DatabaseManager().logout();
                        game.setScreen(new LoginScreen(game));
                    });
        }
    }

    protected Image card(float x, float y, float w, float h) {
        return rect(x, y, w, h, CARD);
    }

    /**
     * Asks the AI for a written analysis (the work runs in the background behind the loading cover) and shows it in a
     * panel over the screen with a Close button. The panel is built last, so closing it just removes what was added.
     */
    protected void showAiAnalysis(String message, java.util.function.Supplier<codequest.data.AiFeedback.Result> work) {
        load(message, work, result -> {
            int panel = mark();
            rect(60, 130, 1800, 900, new Color(0.07f, 0.08f, 0.13f, 1f));
            rect(60, 130, 1800, 6, AMBER);
            text("AI analysis", 100, 150, 1000, 50, 2.2f, AMBER, Align.left);
            if (result.ok()) {
                text(result.text(), 100, 220, 1720, 650, 1.55f, Color.WHITE, Align.topLeft,
                        codequest.screens.PixelFonts.Face.SANS).setWrap(true);
            } else {
                text(result.error(), 100, 220, 1720, 200, 1.6f, new Color(1f, 0.55f, 0.5f, 1f), Align.topLeft,
                        codequest.screens.PixelFonts.Face.SANS).setWrap(true);
            }
            text("Written by AI from statistics only: no student name is sent. It can make mistakes, so check it before acting on it.",
                    100, 885, 1720, 40, 1.2f, MUTED, Align.left, codequest.screens.PixelFonts.Face.SANS).setWrap(true);
            solidButton("Close", 760, 945, 400, 66, BUTTON, () -> clearFrom(panel));
        });
    }
}
