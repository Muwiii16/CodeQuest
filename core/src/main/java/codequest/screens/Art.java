package codequest.screens;

/**
 * Every art file the screens use, in one place. The art is true pixel art (see DESIGN.md): backgrounds are
 * 960x540 and are drawn at 2x, maps are 1640x902, sprites keep their own size. To swap a picture, drop a file over
 * the same path under assets/ or change the path here. (tools/GeneratePlaceholderAssets.java makes the old
 * placeholders and would overwrite this art, so do not run it any more.)
 */
public final class Art {

    private Art() {
    }

    /** Intro screen: the campus gate by day. */
    public static final String BG_INTRO = "ui/backgrounds/intro.png";
    /** Student menus: the campus plaza by day (menu_day.png is the same picture; menu.png is its dusk version). */
    public static final String BG_MENU = "ui/backgrounds/menu_day.png";
    public static final String BG_PROFESSOR = "ui/backgrounds/professor.png";
    /** Login screen: the campus gate at dusk. */
    public static final String BG_LOGIN_FOREST = "ui/backgrounds/login_forest.png";
    /** The other versions, not used yet: the dusk plaza and the day gate (intro.png is the same picture as login_day.png). */
    public static final String BG_MENU_DUSK = "ui/backgrounds/menu.png";
    public static final String BG_LOGIN_DAY = "ui/backgrounds/login_day.png";

    /** The hero's animation frames: {@code heroFrame("run_right", 3)} or {@code heroFrame("idle", 1)}, frames 1 to 5. */
    public static String heroFrame(String set, int frame) {
        return "game/hero/" + set + "_" + frame + ".png";
    }

    public static final String LOGO = "ui/titles/logo.png";
    public static final String LOGO_MARK = "ui/titles/logo_mark.png";

    public static final String LOGIN_CARD = "ui/login/card.png";

    public static final String LAB = "game/lab.png";

    /** Menu button textures: {@code def("campaign")} / {@code hover("campaign")}. */
    public static String def(String name) {
        return "ui/buttons/" + name + "_def.png";
    }

    public static String hover(String name) {
        return "ui/buttons/" + name + "_hover.png";
    }

    public static String map(int stage) {
        return "game/maps/map" + stage + ".png";
    }

    /**
     * A School Assets sheet (doors, floors, objects, wall, windows) at its native 16 px tile size. The 32 and 64 folders
     * hold enlarged copies; the school rooms are built from the 16 px ones and enlarged evenly (see SchoolRoomPainter).
     */
    public static String school(String sheet) {
        return "game/school/16/" + sheet + ".png";
    }

    /** The empty spots reserved for towers in a map picture (cell units, see TowerSlots). */
    public static String slots(int stage) {
        return "game/maps/map" + stage + "_slots.txt";
    }
}
