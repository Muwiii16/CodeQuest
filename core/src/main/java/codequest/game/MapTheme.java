package codequest.game;

/**
 * What a stage looks like. CAMPUS is the original outdoor map picture; the others are school rooms built from the
 * School Assets tiles (see {@link SchoolRoom}). The key is stored in the stage's {@code campus_background} column.
 */
public enum MapTheme {
    CAMPUS("campus", "Campus"),
    CLASSROOM("classroom", "Classroom"),
    LIBRARY("library", "Library"),
    HALLWAY("hallway", "Hallway");

    private final String key;
    private final String label;

    MapTheme(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** True for the themes drawn from the school tiles. */
    public boolean isSchool() {
        return this != CAMPUS;
    }

    /** The theme saved under this key; the campus for null, blank or unknown keys (older stages have none). */
    public static MapTheme fromKey(String key) {
        if (key != null) {
            for (MapTheme t : values()) {
                if (t.key.equalsIgnoreCase(key.trim())) {
                    return t;
                }
            }
        }
        return CAMPUS;
    }
}
