package codequest.game;

import codequest.screens.Art;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@link SchoolRoom} into a texture. The room is put together from the 16 px sheets and enlarged 4x with
 * nearest-neighbour (every art pixel stays a sharp block), so the picture is 2560 x 1408 like a campus map; it is shown
 * smaller with mipmaps, so the stage preview stays clean too. Each theme and layout is built once and kept for as long
 * as the game runs (same rule as {@code Assets}: screens must not dispose it).
 */
public final class SchoolRoomPainter {

    public static final int SCALE = 4;

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private SchoolRoomPainter() {
    }

    public static Texture texture(MapTheme theme, int layout) {
        String key = theme.key() + "/" + layout;
        Texture t = CACHE.get(key);
        if (t == null) {
            t = paint(theme, layout);
            CACHE.put(key, t);
        }
        return t;
    }

    /** The tower spots of a layout in cell units, from the same slot files the campus maps use. */
    public static List<float[]> towerSpots(int layout) {
        List<float[]> out = new ArrayList<>();
        try {
            FileHandle file = Gdx.files.internal(Art.slots(layout));
            if (file.exists()) {
                for (TowerSlots.Slot s : TowerSlots.parse(file.readString(), 1f, 0f)) {
                    out.add(new float[] { s.x(), s.y() });
                }
            }
        } catch (RuntimeException e) {
            Gdx.app.error("SchoolRoom", "Could not read the tower slots", e);
        }
        return out;
    }

    private static Texture paint(MapTheme theme, int layout) {
        Map<SchoolRoom.Sheet, Pixmap> sheets = new EnumMap<>(SchoolRoom.Sheet.class);
        for (SchoolRoom.Sheet s : SchoolRoom.Sheet.values()) {
            sheets.put(s, new Pixmap(Gdx.files.internal(Art.school(s.file()))));
        }
        Pixmap out = new Pixmap(SchoolRoom.WIDTH * SCALE, SchoolRoom.HEIGHT * SCALE, Pixmap.Format.RGBA8888);
        out.setFilter(Pixmap.Filter.NearestNeighbour);
        out.setBlending(Pixmap.Blending.SourceOver);
        for (SchoolRoom.Piece p : SchoolRoom.build(theme, layout, towerSpots(layout))) {
            SchoolRoom.R r = p.src();
            if (p.sheet() == null) {
                out.setColor(p.color());
                out.fillRectangle(p.dx() * SCALE, p.dy() * SCALE, r.w() * SCALE, r.h() * SCALE);
            } else {
                out.drawPixmap(sheets.get(p.sheet()), r.x(), r.y(), r.w(), r.h(), p.dx() * SCALE, p.dy() * SCALE,
                        r.w() * SCALE, r.h() * SCALE);
            }
        }
        Texture t = new Texture(out, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        out.dispose();
        for (Pixmap p : sheets.values()) {
            p.dispose();
        }
        return t;
    }

    /** Frees every room picture when the game closes. */
    public static void disposeAll() {
        for (Texture t : CACHE.values()) {
            t.dispose();
        }
        CACHE.clear();
    }
}
