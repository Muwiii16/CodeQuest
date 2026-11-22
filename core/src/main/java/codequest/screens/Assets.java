package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's picture cache. Every picture is read from disk once and kept for as long as the game runs, so moving between
 * screens never loads or decodes anything again. The intro screen fills the cache up front, behind a loading bar
 * ({@link #tasks()}); a picture that was not preloaded is still loaded the first time a screen asks for it.
 *
 * <p>Screens must not dispose what they get from here: the textures are shared.
 */
public final class Assets {

    /** One step of the start-up loading: a short name for the bar and the work itself (it needs the GL thread). */
    public record Task(String label, Runnable run) {
    }

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private Assets() {
    }

    /**
     * A picture from assets/. Pixel art is drawn with the Nearest filter (never smoothed); the hero, towers, maps and the
     * soft glow pictures are shown smaller than they are, so those use Linear.
     */
    public static Texture texture(String path) {
        Texture t = CACHE.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.internal(path));
            Texture.TextureFilter filter = smooth(path) ? Texture.TextureFilter.Linear : Texture.TextureFilter.Nearest;
            t.setFilter(filter, filter);
            CACHE.put(path, t);
        }
        return t;
    }

    static boolean smooth(String path) {
        return path.startsWith("game/hero/") || path.startsWith("game/towers/") || path.startsWith("game/maps/")
                || path.startsWith("ui/menu/") || path.startsWith("ui/kit/") || path.startsWith("ui/pro/") || path.startsWith("ui/q/") || path.startsWith("ui/hud/")
                || path.equals("game/glow.png") || path.equals("game/ring.png");
    }

    /** How many pictures are cached now. */
    public static int cached() {
        return CACHE.size();
    }

    /** The pictures to preload from the text of assets.txt (one path per line): every .png except the font atlases. */
    static List<String> pictureFiles(String listText) {
        List<String> out = new ArrayList<>();
        for (String line : listText.split("\\r?\\n")) {
            String path = line.trim();
            if (path.endsWith(".png") && !path.startsWith("ui/fonts/")) {
                out.add(path);
            }
        }
        return out;
    }

    /**
     * Everything the game wants ready before the first screen: all pictures (from the build's assets.txt) and every font
     * size the game uses. Each task is small, so the loading bar can move between them. Empty when assets.txt is missing
     * (the game then simply loads things when it needs them).
     */
    public static List<Task> tasks() {
        List<Task> tasks = new ArrayList<>();
        try {
            if (Gdx.files.internal("assets.txt").exists()) {
                for (String path : pictureFiles(Gdx.files.internal("assets.txt").readString())) {
                    tasks.add(new Task("pictures", () -> texture(path)));
                }
            }
        } catch (RuntimeException e) {
            Gdx.app.error("Assets", "Could not read assets.txt: pictures load when needed", e);
        }
        tasks.addAll(PixelFonts.preloadTasks());
        return tasks;
    }

    /** Frees every cached picture when the game closes. */
    public static void disposeAll() {
        for (Texture t : CACHE.values()) {
            t.dispose();
        }
        CACHE.clear();
    }
}
