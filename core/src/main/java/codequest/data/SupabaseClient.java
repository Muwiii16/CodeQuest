package codequest.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thin PostgREST client: GET/POST/PATCH against a Supabase REST endpoint, using
 * the JDK's java.net.http client (the older HttpURLConnection cannot send
 * PATCH at all) and LibGDX's own JsonReader. Every call blocks the calling
 * thread; that's fine here since DatabaseManager is only hit from discrete
 * user actions, not every frame.
 */
final class SupabaseClient {

    private static final JsonReader JSON_READER = new JsonReader();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

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

    /** DELETE with a filter; true when the server accepted it. */
    static boolean delete(String pathAndQuery) {
        String base = SupabaseConfig.getUrl();
        String anonKey = SupabaseConfig.getAnonKey();
        if (base == null || anonKey == null) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .DELETE()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Supabase DELETE " + pathAndQuery + " -> HTTP " + response.statusCode() + ": "
                        + response.body());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Supabase DELETE " + pathAndQuery + " failed: " + e.getMessage());
            return false;
        }
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

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .header("Accept", "application/json");
            if (jsonBody != null) {
                builder.header("Content-Type", "application/json")
                        .header("Prefer", "return=representation")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = HTTP.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                System.err.println("Supabase " + method + " " + pathAndQuery + " -> HTTP " + status + ": " + body);
                return null;
            }
            if (body == null || body.isEmpty()) {
                return null;
            }
            return JSON_READER.parse(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Supabase " + method + " " + pathAndQuery + " interrupted");
            return null;
        } catch (Exception e) {
            System.err.println("Supabase " + method + " " + pathAndQuery + " failed: " + e.getMessage());
            return null;
        }
    }
}
