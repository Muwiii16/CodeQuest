package codequest.screens;

import codequest.data.DatabaseManager.Cosmetic;
import com.badlogic.gdx.graphics.Color;

/**
 * Placeholder look for a cosmetic until its real art exists: a stable color
 * picked from the name, so each item is visibly different in the preview and in play.
 */
public final class CosmeticLook {

    private CosmeticLook() {
    }

    public static Color colorOf(Cosmetic cosmetic) {
        float hue = (Math.abs(cosmetic.name().hashCode()) % 360) / 360f;
        return hsv(hue, 0.55f, 1f);
    }

    private static Color hsv(float h, float s, float v) {
        float r;
        float g;
        float b;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return new Color(r, g, b, 1f);
    }

    public static String categoryLabel(String category) {
        return switch (category) {
            case "avatar_skin" -> "Skins";
            case "trail" -> "Trails";
            case "badge" -> "Badges";
            case "title" -> "Titles";
            default -> category;
        };
    }
}
