package codequest.screens;

import codequest.data.DatabaseManager.Cosmetic;
import com.badlogic.gdx.graphics.Color;

import java.util.List;
import java.util.Map;

/**
 * Placeholder look for a cosmetic until its real art exists: a stable color picked from the
 * name, so each item is visibly different in the preview and in play. Rarity decides the glow.
 */
public final class CosmeticLook {

    private static final Color NEUTRAL = new Color(0.82f, 0.86f, 1f, 1f);

    private CosmeticLook() {
    }

    public static Color colorOf(Cosmetic cosmetic) {
        if (cosmetic.isDefault()) {
            return new Color(NEUTRAL);
        }
        float hue = (Math.abs(cosmetic.name().hashCode()) % 360) / 360f;
        return hsv(hue, 0.55f, 1f);
    }

    /** The glow color of a rarity: grey-white, blue, purple, gold. */
    public static Color rarityColor(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "rare" -> new Color(0.35f, 0.65f, 1f, 1f);
            case "epic" -> new Color(0.74f, 0.45f, 1f, 1f);
            case "legendary" -> new Color(1f, 0.8f, 0.25f, 1f);
            default -> new Color(0.78f, 0.8f, 0.86f, 1f);
        };
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
            case "hat" -> "Hats";
            case "weapon" -> "Weapons";
            case "trail" -> "Trails";
            case "title" -> "Titles";
            default -> category;
        };
    }

    /** The singular name of a category: Hat, Weapon, Trail, Title. */
    public static String categoryName(String category) {
        return switch (category) {
            case "hat" -> "Hat";
            case "weapon" -> "Weapon";
            case "trail" -> "Trail";
            case "title" -> "Title";
            default -> category;
        };
    }

    /** What a student looks like in play: hat (or none), floating weapon, trail (or none). */
    public record Outfit(Cosmetic hat, Cosmetic weapon, Cosmetic trail) {
    }

    /**
     * The equipped item of each category; a category with nothing equipped gets its default item
     * (the plain wand for weapons) or nothing (hats and trails have a plain default that draws nothing).
     */
    public static Outfit outfit(Map<String, Long> equipped, List<Cosmetic> catalog) {
        return new Outfit(pick("hat", equipped, catalog), pick("weapon", equipped, catalog), pick("trail", equipped, catalog));
    }

    private static Cosmetic pick(String category, Map<String, Long> equipped, List<Cosmetic> catalog) {
        Long id = equipped.get(category);
        Cosmetic fallback = null;
        for (Cosmetic c : catalog) {
            if (!c.category().equals(category)) {
                continue;
            }
            if (id != null && c.id() == id) {
                return c;
            }
            if (fallback == null && c.isDefault()) {
                fallback = c;
            }
        }
        return fallback;
    }
}
