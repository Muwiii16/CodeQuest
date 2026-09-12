package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared texture cache for gameplay entities. The Swing prototype reloaded a
 * fresh BufferedImage per spawned enemy/tower/projectile — wasteful but
 * harmless there since Java GCs unused BufferedImages. A LibGDX Texture is a
 * GPU resource that isn't freed until disposed, so doing that 1:1 would leak
 * VRAM every wave (dozens of enemies x a dozen directional frames each).
 * Every entity loader below asks this cache for a texture instead of
 * constructing one directly, so same-type entities share one GPU texture.
 */
public final class TextureCache {

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private TextureCache() {
    }

    public static Texture get(String assetPath) {
        return CACHE.computeIfAbsent(assetPath, p -> new Texture(Gdx.files.internal(p)));
    }

    /** Call when leaving gameplay so cached textures don't outlive the session. */
    public static void disposeAll() {
        for (Texture t : CACHE.values()) {
            t.dispose();
        }
        CACHE.clear();
    }
}
