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
        // the dashboard also has an exit button in the corner, so its name chip and log out button sit a little further left
        float shift = backTarget == null ? 90f : 0f;
        placeTopLeft(chip, VIRTUAL_WIDTH - 740 - shift, 22, 380, 66);
        stage.addActor(chip);
        chip.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        text(username, VIRTUAL_WIDTH - 730 - shift, 30, 360, 50, 1.5f, Color.WHITE, Align.center);
        if (backTarget != null) {
            solidButton("Back", VIRTUAL_WIDTH - 330, 22, 280, 66, BUTTON, backTarget);
        } else {
            exitOnEscape();
            solidButton("Log out", VIRTUAL_WIDTH - 330 - shift, 22, 280, 66, new Color(0.45f, 0.18f, 0.18f, 1f),
                    () -> {
                        new codequest.data.DatabaseManager().logout();
                        game.setScreen(new LoginScreen(game));
                    });
            exitCornerButton(VIRTUAL_WIDTH - 116, 66);
        }
    }

    private static final Color HEADER_ROW = new Color(0.2f, 0.16f, 0.06f, 1f);

    /** Table rows and table headers (drawn as flat rectangles by the screens) become the picture rows of the kit. */
    @Override
    protected Image rect(float x, float yFromTop, float w, float h, Color color) {
        boolean row = color.equals(ROW);
        if ((row || color.equals(HEADER_ROW)) && w >= 300 && h >= 36 && h <= 120) {
            Image image = new Image(UiKit.b3(row ? "table_row" : "table_header_row", 40).drawable());
            placeTopLeft(image, x - 2, yFromTop - 2, w + 4, h + 4);
            stage.addActor(image);
            return image;
        }
        return super.rect(x, yFromTop, w, h, color);
    }

    /** A number tile of the kit, tinted by the colour of its value (red, green, gold or blue). */
    protected Image statTile(float x, float y, float w, float h, Color value) {
        String tint = switch (UiKit.proColorFor(value)) {
            case "red" -> "red";
            case "green" -> "green";
            case "gold" -> "gold";
            default -> "blue";
        };
        Image image = new Image(UiKit.b3("stat_tile_" + tint, 44).drawable());
        placeTopLeft(image, x, y, w, h);
        stage.addActor(image);
        image.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return image;
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
            picture(UiKit.analytics("analytics_main_panel", 26, 26), 60, 130, 1800, 900);
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
                    100, 938, 1720, 26, 1.1f, MUTED, Align.left, codequest.screens.PixelFonts.Face.SANS);
            stateButton("ui/analytics/close_button_idle.png", "ui/analytics/close_button_hover.png",
                    "ui/analytics/close_button_pressed.png", 760, 962, 400, 60, () -> clearFrom(panel));
        });
    }

    private static final Color RED = new Color(1f, 0.45f, 0.42f, 1f);
    private static final Color TEAL = new Color(0.3f, 0.85f, 0.75f, 1f);

    /** A picture (the analytics kit) at a position and size; it never takes the mouse. */
    private Image picture(UiKit.Patch patch, float x, float y, float w, float h) {
        Image image = new Image(patch.drawable());
        placeTopLeft(image, x, y, w, h);
        stage.addActor(image);
        image.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return image;
    }

    private void drawReport(codequest.data.AiReport r) {
        PixelFonts.Face sans = PixelFonts.Face.SANS;
        // summary
        picture(UiKit.analytics("summary_panel", 24, 24), 100, 205, 1720, 100);
        text("SUMMARY", 130, 214, 400, 22, 1.0f, MUTED, Align.left, sans);
        text(r.summary(), 130, 238, 1660, 62, 1.55f, Color.WHITE, Align.topLeft, sans).setWrap(true);
        float y = 318;
        // number tiles
        if (!r.tiles().isEmpty()) {
            float w = (1720 - 20 * 3) / 4f;
            for (int i = 0; i < r.tiles().size(); i++) {
                float x = 100 + i * (w + 20);
                picture(UiKit.analytics("stat_card", 24, 24), x, y, w, 80);
                text(r.tiles().get(i).label(), x + 22, y + 8, w - 40, 26, 1.1f, MUTED, Align.left, sans).setEllipsis(true);
                text(r.tiles().get(i).value(), x + 22, y + 32, w - 40, 44, 2.3f, i == 0 ? Color.WHITE : AMBER, Align.left);
            }
            y += 94;
        }
        // who needs attention / to watch / doing well
        float cw = (1720 - 40) / 3f;
        noteCard(100, y, cw, "Needs attention", "student_card_attention", "status_attention", 230, RED, r.attention());
        noteCard(100 + cw + 20, y, cw, "Watch", "student_card_watch", "status_watch", 150, AMBER, r.watch());
        noteCard(100 + 2 * (cw + 20), y, cw, "Doing well", "student_card_success", "status_success", 190, TEAL, r.doingWell());
        y += 392;
        // next steps, then what the group struggled with and what the data cannot tell
        picture(UiKit.analytics("next_steps_panel", 24, 24), 100, y, 1000, 134);
        text("Next steps", 128, y + 8, 600, 30, 1.4f, AMBER, Align.left);
        for (int i = 0; i < r.nextSteps().size(); i++) {
            picture(UiKit.analytics("analytics_checkbox", 6, 6), 130, y + 46 + i * 28, 20, 20);
            text(r.nextSteps().get(i), 162, y + 42 + i * 28, 910, 28, 1.15f, Color.WHITE, Align.left, sans).setEllipsis(true);
        }
        picture(UiKit.analytics("learning_gaps_panel", 24, 24), 1120, y, 700, 134);
        text("Struggles and gaps", 1148, y + 8, 600, 30, 1.4f, AMBER, Align.left);
        int line = 0;
        for (String st : r.struggles()) {
            text(st, 1148, y + 42 + line++ * 27, 650, 26, 1.1f, Color.WHITE, Align.left, sans).setEllipsis(true);
        }
        for (String g : r.dataGaps()) {
            if (line < 3 || r.struggles().isEmpty()) {
                text("? " + g, 1148, y + 42 + line++ * 27, 650, 26, 1.1f, MUTED, Align.left, sans).setEllipsis(true);
            }
        }
    }

    private void noteCard(float x, float y, float w, String title, String card, String status, float pillW, Color accent,
            java.util.List<codequest.data.AiReport.Note> notes) {
        PixelFonts.Face sans = PixelFonts.Face.SANS;
        picture(UiKit.analytics(card, 24, 24), x, y, w, 380);
        picture(UiKit.analytics(status, 20, 0), x + 22, y + 16, pillW, 42);
        text(title, x + 22, y + 22, pillW, 30, 1.3f, Color.WHITE, Align.center);
        if (notes.isEmpty()) {
            text("Nothing to report.", x + 24, y + 80, w - 48, 30, 1.2f, MUTED, Align.left, sans);
            return;
        }
        for (int i = 0; i < notes.size(); i++) {
            codequest.data.AiReport.Note n = notes.get(i);
            float ey = y + 72 + i * 150;
            float fy = ey;
            if (!n.who().isBlank()) {
                picture(UiKit.analytics("student_name_tag", 20, 0), x + 22, ey, Math.min(w - 44, 40 + n.who().length() * 14), 40);
                text(n.who(), x + 22, ey + 4, Math.min(w - 44, 40 + n.who().length() * 14), 32, 1.2f, Color.WHITE, Align.center,
                        sans).setEllipsis(true);
                fy = ey + 46;
            }
            text(n.finding(), x + 24, fy, w - 48, 52, 1.2f, Color.WHITE, Align.topLeft, sans).setWrap(true);
            if (!n.next().isBlank()) {
                text("Next: " + n.next(), x + 24, fy + 54, w - 48, 46, 1.05f, MUTED, Align.topLeft, sans).setWrap(true);
            }
        }
    }
}
