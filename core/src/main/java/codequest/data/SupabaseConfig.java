package codequest.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.Properties;
import java.io.StringReader;

/** Loads Supabase connection details from assets/supabase.properties (gitignored — see
 *  supabase.properties.example for the template every teammate needs to copy and fill in). */
public final class SupabaseConfig {

    private static String url;
    private static String anonKey;
    private static boolean loaded = false;

    private SupabaseConfig() {
    }

    private static void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;

        FileHandle file = Gdx.files.internal("supabase.properties");
        if (!file.exists()) {
            Gdx.app.error("SupabaseConfig",
                    "assets/supabase.properties not found — copy supabase.properties.example and fill in your project's URL/anon key.");
            return;
        }

        try {
            Properties props = new Properties();
            props.load(new StringReader(file.readString("UTF-8")));
            url = props.getProperty("supabase.url");
            anonKey = props.getProperty("supabase.anonKey");
        } catch (Exception e) {
            Gdx.app.error("SupabaseConfig", "Failed to read supabase.properties", e);
        }
    }

    public static String getUrl() {
        ensureLoaded();
        return url;
    }

    public static String getAnonKey() {
        ensureLoaded();
        return anonKey;
    }

    public static boolean isConfigured() {
        ensureLoaded();
        return url != null && anonKey != null;
    }
}
