package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

import java.util.HashMap;
import java.util.Map;

/**
 * The game's pixel font (Pixelify Sans, SIL Open Font License, see assets/ui/fonts). The font is baked into bitmap
 * fonts at fixed pixel sizes, so every text is drawn at a size the font was made for instead of being stretched,
 * which is what made the old text blurry. Each size comes plain and with a dark outline; the outlined one stays
 * readable over pictures. The fonts are shared by every screen and live as long as the game does.
 */
public final class PixelFonts {

    /** The sizes that were baked (make_fonts.py in the art tools). */
    static final int[] SIZES = { 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64, 80, 96 };

    /** The text height, in pixels, that a font scale of 1.0 stands for (the size the old default font had). */
    static final float BASE = 15f;

    private static final Map<String, BitmapFont> CACHE = new HashMap<>();

    private PixelFonts() {
    }

    /** The baked size that is closest to what the given scale asks for. */
    static int nearestSize(float scale) {
        float target = BASE * scale;
        int best = SIZES[0];
        for (int size : SIZES) {
            if (Math.abs(size - target) <= Math.abs(best - target)) { // on a tie the larger size wins
                best = size;
            }
        }
        return best;
    }

    /** The font for a scale (1.0 is about 15 px tall, like the old default); outlined = dark outline for text over pictures. */
    public static BitmapFont forScale(float scale, boolean outlined) {
        int size = nearestSize(scale);
        String path = "ui/fonts/pixel_" + (outlined ? "o_" : "") + size + ".fnt";
        BitmapFont font = CACHE.get(path);
        if (font == null) {
            font = new BitmapFont(Gdx.files.internal(path));
            // sharp when enlarged, smooth when the window shrinks the game
            font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Nearest);
            font.setUseIntegerPositions(true);
            CACHE.put(path, font);
        }
        return font;
    }
}
