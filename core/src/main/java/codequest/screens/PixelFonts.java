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

    /**
     * Sizes of the smooth, readable fonts (DejaVu Sans for text, DejaVu Sans Mono for code; make_fonts_read.py). They
     * are used where students read questions, answers and code, so they are anti-aliased instead of pixelated.
     */
    static final int[] READ_SIZES = { 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48 };

    /** The baked size that is closest to what the given scale asks for. */
    static int nearestSize(float scale) {
        return nearest(SIZES, scale);
    }

    static int nearestReadSize(float scale) {
        return nearest(READ_SIZES, scale);
    }

    private static int nearest(int[] sizes, float scale) {
        float target = BASE * scale;
        int best = sizes[0];
        for (int size : sizes) {
            if (Math.abs(size - target) <= Math.abs(best - target)) { // on a tie the larger size wins
                best = size;
            }
        }
        return best;
    }

    /** The look of a piece of text: the pixel font for labels, smooth sans for sentences, smooth mono for code. */
    public enum Face { PIXEL, SANS, MONO }

    /** The plain (no outline) font of a face at a scale. */
    public static BitmapFont font(Face face, float scale) {
        return switch (face) {
            case PIXEL -> forScale(scale, false);
            case SANS -> readable(scale);
            case MONO -> code(scale);
        };
    }

    /** Code reads best in the monospace face; sentences (and the statement or instruction of some kinds) in the sans face. */
    public static Face faceForPrompt(codequest.content.AnswerKind kind, String prompt) {
        if (kind == codequest.content.AnswerKind.TRUE_FALSE || kind == codequest.content.AnswerKind.ARRANGE) {
            return Face.SANS;
        }
        for (char ch : new char[] { ';', '{', '}', '=', '#', '[', ']', '<', '>', '(', ')', '\n' }) {
            if (prompt.indexOf(ch) >= 0) {
                return Face.MONO;
            }
        }
        return prompt.contains(codequest.content.QuestionDraft.BLANK) ? Face.MONO : Face.SANS;
    }

    /** Smooth sans-serif for text a student has to read (questions, hints, answers). */
    public static BitmapFont readable(float scale) {
        return smooth("read", nearestReadSize(scale));
    }

    /** Smooth monospace for code (code in questions, answer tokens, the code block). */
    public static BitmapFont code(float scale) {
        return smooth("code", nearestReadSize(scale));
    }

    private static BitmapFont smooth(String face, int size) {
        String path = "ui/fonts/" + face + "_" + size + ".fnt";
        BitmapFont font = CACHE.get(path);
        if (font == null) {
            font = new BitmapFont(Gdx.files.internal(path));
            font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            font.setUseIntegerPositions(true);
            CACHE.put(path, font);
        }
        return font;
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
