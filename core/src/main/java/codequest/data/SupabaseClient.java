package codequest.data;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonReader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Thin PostgREST client: GET/POST/PATCH against a Supabase REST endpoint,
 * using plain java.net.HttpURLConnection (no extra HTTP dependency) and
 * LibGDX's own JsonReader (no extra JSON dependency — gdx core already
 * carries it). Every call blocks the calling thread; that's fine here since
 * DatabaseManager is only hit from discrete user actions (a login click, a
 * wave-victory save), not every frame.
 */
final class SupabaseClient {

    private static final JsonReader JSON_READER = new JsonReader();

    private SupabaseClient() {
    }

    static JsonValue get(String pathAndQuery) {
        return request("GET", pathAndQuery, null);
    }

    static JsonValue post(String path, String jsonBody) {
        return request("POST", path, jsonBody);
    }

    static JsonValue patch(String pathAndQuery, String jsonBody) {
        return request("PATCH", pathAndQuery, jsonBody);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonValue request(String method, String pathAndQuery, String jsonBody) {
        String base = SupabaseConfig.getUrl();
        String anonKey = SupabaseConfig.getAnonKey();
        if (base == null || anonKey == null) {
            System.err.println("Supabase not configured — see assets/supabase.properties.example");
            return null;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(base + pathAndQuery);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", anonKey);
            conn.setRequestProperty("Authorization", "Bearer " + anonKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (jsonBody != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "return=representation");
                conn.setDoOutput(true);
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = stream == null ? "" : readAll(stream);

            if (status < 200 || status >= 300) {
                System.err.println("Supabase " + method + " " + pathAndQuery + " -> HTTP " + status + ": " + body);
                return null;
            }
            if (body.isEmpty()) {
                return null;
            }
            return JSON_READER.parse(body);
        } catch (Exception e) {
            System.err.println("Supabase " + method + " " + pathAndQuery + " failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}
