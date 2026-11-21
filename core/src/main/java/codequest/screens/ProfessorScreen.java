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
        this.proKit = true; // buttons, panels and the title bar come from the professor's picture kit (assets/ui/pro)
        setBackground(Art.BG_PROFESSOR);

        Image bar = new Image(UiKit.PRO_HEADER.drawable());
        placeTopLeft(bar, -10, -14, VIRTUAL_WIDTH + 20, 138);
        stage.addActor(bar);
        bar.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        text("PROFESSOR", 50, 22, 330, 30, 1.3f, AMBER, Align.left);
        text(title, 50, 48, 1100, 50, 2.6f, Color.WHITE, Align.left);
        Image chip = new Image(UiKit.PRO_CHIP.drawable());
        placeTopLeft(chip, VIRTUAL_WIDTH - 740, 22, 380, 66);
        stage.addActor(chip);
        chip.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        text(username, VIRTUAL_WIDTH - 730, 30, 360, 50, 1.5f, Color.WHITE, Align.center);
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
        if (h < 56 || w < 100) {
            return rect(x, y, w, h, CARD);
        }
        boolean big = h >= 150 && w >= 200;
        Image panel = new Image((big ? UiKit.PRO_PANEL : UiKit.PRO_STRIP).drawable());
        float pad = big ? 6 : 4;
        placeTopLeft(panel, x - pad, y - pad, w + pad * 2, h + pad * 2);
        stage.addActor(panel);
        return panel;
    }

    /**
     * Asks the AI for a written analysis (the work runs in the background behind the loading cover) and shows it in a
     * panel over the screen with a Close button: a summary, number tiles, cards for who needs attention, who to watch and
     * who does well, then next steps and what the group struggled with. The panel is built last, so closing it just
     * removes what was added.
     */
    protected void showAiAnalysis(String message, java.util.function.Supplier<codequest.data.AiFeedback.Result> work) {
        load(message, work, result -> {
            int panel = mark();
            rect(60, 130, 1800, 900, new Color(0.07f, 0.08f, 0.13f, 1f));
            rect(60, 130, 1800, 6, AMBER);
            text("AI analysis", 100, 148, 1000, 50, 2.2f, AMBER, Align.left);
            codequest.data.AiReport report = result.ok() ? codequest.data.AiReport.parse(result.text()) : null;
            if (report != null) {
                drawReport(report);
            } else if (result.ok()) {
                text(result.text(), 100, 220, 1720, 650, 1.55f, Color.WHITE, Align.topLeft,
                        codequest.screens.PixelFonts.Face.SANS).setWrap(true);
            } else {
                text(result.error(), 100, 220, 1720, 200, 1.6f, new Color(1f, 0.55f, 0.5f, 1f), Align.topLeft,
                        codequest.screens.PixelFonts.Face.SANS).setWrap(true);
            }
            text("Written by AI from statistics only. It can make mistakes.",
                    100, 932, 1720, 26, 1.1f, MUTED, Align.left, codequest.screens.PixelFonts.Face.SANS);
            solidButton("Close", 760, 962, 400, 56, BUTTON, () -> clearFrom(panel));
        });
    }

    private static final Color RED = new Color(1f, 0.45f, 0.42f, 1f);
    private static final Color TEAL = new Color(0.3f, 0.85f, 0.75f, 1f);
    private static final Color TILE = new Color(0.12f, 0.15f, 0.22f, 1f);

    private void drawReport(codequest.data.AiReport r) {
        PixelFonts.Face sans = PixelFonts.Face.SANS;
        // summary
        rect(100, 205, 1720, 100, TILE);
        rect(100, 205, 6, 100, AMBER);
        text("SUMMARY", 125, 210, 400, 22, 1.0f, MUTED, Align.left, sans);
        text(r.summary(), 125, 234, 1670, 66, 1.55f, Color.WHITE, Align.topLeft, sans).setWrap(true);
        float y = 318;
        // number tiles
        if (!r.tiles().isEmpty()) {
            float w = (1720 - 20 * 3) / 4f;
            for (int i = 0; i < r.tiles().size(); i++) {
                float x = 100 + i * (w + 20);
                rect(x, y, w, 80, TILE);
                text(r.tiles().get(i).label(), x + 18, y + 6, w - 30, 26, 1.1f, MUTED, Align.left, sans).setEllipsis(true);
                text(r.tiles().get(i).value(), x + 18, y + 30, w - 30, 46, 2.3f, i == 0 ? Color.WHITE : AMBER, Align.left);
            }
            y += 96;
        }
        // who needs attention / to watch / doing well
        float cw = (1720 - 40) / 3f;
        noteCard(100, y, cw, "Needs attention", RED, r.attention());
        noteCard(100 + cw + 20, y, cw, "Watch", AMBER, r.watch());
        noteCard(100 + 2 * (cw + 20), y, cw, "Doing well", TEAL, r.doingWell());
        y += 392;
        // next steps, then what the group struggled with and what the data cannot tell
        rect(100, y, 1000, 126, TILE);
        text("Next steps", 124, y + 6, 600, 30, 1.4f, AMBER, Align.left);
        for (int i = 0; i < r.nextSteps().size(); i++) {
            rect(126, y + 46 + i * 29, 16, 16, BUTTON);
            text(r.nextSteps().get(i), 154, y + 40 + i * 29, 920, 28, 1.15f, Color.WHITE, Align.left, sans).setEllipsis(true);
        }
        rect(1120, y, 700, 126, TILE);
        text("Struggles and gaps", 1144, y + 6, 600, 30, 1.4f, AMBER, Align.left);
        int line = 0;
        for (String st : r.struggles()) {
            text(st, 1144, y + 40 + line++ * 27, 660, 26, 1.1f, Color.WHITE, Align.left, sans).setEllipsis(true);
        }
        for (String g : r.dataGaps()) {
            if (line < 3 || r.struggles().isEmpty()) {
                text("? " + g, 1144, y + 40 + line++ * 27, 660, 26, 1.1f, MUTED, Align.left, sans).setEllipsis(true);
            }
        }
    }

    private void noteCard(float x, float y, float w, String title, Color accent, java.util.List<codequest.data.AiReport.Note> notes) {
        PixelFonts.Face sans = PixelFonts.Face.SANS;
        rect(x, y, w, 380, TILE);
        rect(x, y, w, 5, accent);
        text(title, x + 20, y + 14, w - 40, 34, 1.5f, accent, Align.left);
        if (notes.isEmpty()) {
            text("Nothing to report.", x + 20, y + 62, w - 40, 30, 1.2f, MUTED, Align.left, sans);
            return;
        }
        for (int i = 0; i < notes.size(); i++) {
            codequest.data.AiReport.Note n = notes.get(i);
            float ey = y + 58 + i * 160;
            float fy = ey;
            if (!n.who().isBlank()) {
                float chip = Math.min(w - 40, 40 + n.who().length() * 14);
                rect(x + 20, ey, chip, 32, BUTTON);
                text(n.who(), x + 20, ey, chip, 32, 1.2f, Color.WHITE, Align.center, sans).setEllipsis(true);
                fy = ey + 38;
            }
            text(n.finding(), x + 20, fy, w - 40, 62, 1.2f, Color.WHITE, Align.topLeft, sans).setWrap(true);
            if (!n.next().isBlank()) {
                text("Next: " + n.next(), x + 20, fy + 64, w - 40, 50, 1.05f, MUTED, Align.topLeft, sans).setWrap(true);
            }
        }
    }
}
