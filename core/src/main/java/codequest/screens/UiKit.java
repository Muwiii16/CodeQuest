package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/**
 * The picture kit of the student side of the game (assets/ui/kit): panels, header, name chip, tabs, input boxes and the
 * coloured buttons, each a small picture whose corners stay as drawn while the middle stretches to any size (a 9-patch).
 * The professor screens keep their own flat amber look.
 */
final class UiKit {

    /** A picture and how many pixels at each edge stay unstretched. */
    record Patch(String path, int left, int right, int top, int bottom) {
        NinePatchDrawable drawable() {
            return new NinePatchDrawable(new NinePatch(Assets.texture(path), left, right, top, bottom));
        }
    }

    static final Patch PANEL = new Patch("ui/kit/panel_big.png", 70, 70, 70, 70);
    static final Patch STRIP = new Patch("ui/kit/panel_strip.png", 40, 40, 26, 26);
    static final Patch HEADER = new Patch("ui/kit/header.png", 60, 60, 24, 24);
    static final Patch CHIP = new Patch("ui/kit/chip_user.png", 40, 40, 20, 20);
    static final Patch TAB_ON = new Patch("ui/kit/tab_active.png", 30, 30, 18, 14);
    static final Patch TAB_OFF = new Patch("ui/kit/tab_inactive.png", 30, 30, 18, 14);
    static final Patch INPUT = new Patch("ui/kit/input.png", 44, 44, 22, 22);
    static final Patch INPUT_FOCUS = new Patch("ui/kit/input_focus.png", 44, 44, 22, 22);

    // the professor's kit (assets/ui/pro): glowing navy panels and buttons in six colours, three states each
    static final Patch PRO_PANEL = new Patch("ui/pro/panel_big.png", 44, 44, 44, 44);
    static final Patch PRO_STRIP = new Patch("ui/pro/panel_strip.png", 36, 36, 24, 24);
    static final Patch PRO_HEADER = new Patch("ui/pro/header_bar.png", 120, 120, 0, 0);
    static final Patch PRO_CHIP = new Patch("ui/pro/chip_name.png", 40, 40, 24, 24);

    private UiKit() {
    }

    /** A picture of the question panel kit (assets/ui/q): left/right and top/bottom pixels that stay unstretched. */
    static Patch q(String name, int leftRight, int topBottom) {
        return new Patch("ui/q/" + name + ".png", leftRight, leftRight, topBottom, topBottom);
    }

    /** A picture of the game HUD kit (assets/ui/hud). */
    static Patch hud(String name, int leftRight, int topBottom) {
        return new Patch("ui/hud/" + name + ".png", leftRight, leftRight, topBottom, topBottom);
    }

    /** A picture of the professor analytics kit (assets/ui/analytics). */
    static Patch analytics(String name, int leftRight, int topBottom) {
        return new Patch("ui/analytics/" + name + ".png", leftRight, leftRight, topBottom, topBottom);
    }

    /** A professor button picture: color is blue, green, red, purple, gold or dark_grey_navy; state is idle, hover or pressed. */
    static Patch proButton(String color, String state) {
        return new Patch("ui/pro/btn_" + color + "_" + state + ".png", 28, 28, 0, 0);
    }

    /** A professor card picture (blue, purple or teal, by the hue asked for), with its title band across the top. */
    static Patch proCard(Color c) {
        float[] hsb = java.awt.Color.RGBtoHSB(Math.round(c.r * 255), Math.round(c.g * 255), Math.round(c.b * 255), null);
        String name = hsb[0] >= 0.72f && hsb[0] < 0.9f ? "purple" : hsb[0] >= 0.4f && hsb[0] < 0.57f ? "teal" : "blue";
        return new Patch("ui/pro/panel_card_" + name + ".png", 50, 50, 64, 44);
    }

    /** Which professor button colour a flat colour asked for: dull or dark colours become the dark navy button. */
    static String proColorFor(Color c) {
        float max = Math.max(c.r, Math.max(c.g, c.b));
        float min = Math.min(c.r, Math.min(c.g, c.b));
        if (max < 0.4f || (max - min) / Math.max(max, 0.001f) < 0.25f) {
            return "dark_grey_navy";
        }
        float[] hsb = java.awt.Color.RGBtoHSB(Math.round(c.r * 255), Math.round(c.g * 255), Math.round(c.b * 255), null);
        float h = hsb[0];
        if (h < 0.04f || h >= 0.9f) {
            return "red";
        }
        if (h < 0.2f) {
            return "gold";
        }
        if (h < 0.46f) {
            return "green";
        }
        if (h < 0.72f) {
            return "blue";
        }
        return "purple";
    }

    /** One of the coloured buttons: color is blue, teal, purple, orange, green or red; state is up, over or down. */
    static Patch button(String color, String state) {
        return new Patch("ui/kit/button_" + color + "_" + state + ".png", 32, 32, 22, 22);
    }

    /** The picture of an icon (assets/ui/kit/icon_NAME.png, 96 x 96). */
    static String icon(String name) {
        return "ui/kit/icon_" + name + ".png";
    }

    /**
     * Which of the six button colours a flat colour asked for becomes (the screens pick colours, the kit has pictures):
     * by hue, and "dark" for dull or very dark colours (navy, grey), which are the inactive tabs and locked buttons.
     */
    static String colorFor(Color c) {
        float max = Math.max(c.r, Math.max(c.g, c.b));
        float min = Math.min(c.r, Math.min(c.g, c.b));
        if (max < 0.5f || (max - min) / Math.max(max, 0.001f) < 0.28f) {
            return "dark";
        }
        float[] hsb = java.awt.Color.RGBtoHSB(Math.round(c.r * 255), Math.round(c.g * 255), Math.round(c.b * 255), null);
        float h = hsb[0];
        if (h < 0.04f || h >= 0.9f) {
            return "red";
        }
        if (h < 0.2f) {
            return "orange";
        }
        if (h < 0.46f) {
            return "green";
        }
        if (h < 0.57f) {
            return "teal";
        }
        if (h < 0.72f) {
            return "blue";
        }
        return "purple";
    }
}
