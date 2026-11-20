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

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /**
     * The session token the server gave at sign-in. It is sent with every request (header x-session); the database and the
     * Edge Functions use it to know who is asking, so nobody can read or change what is not theirs. Only its hash is stored
     * on the server, and it is never written to disk here.
     */
    private static volatile String sessionToken;

    private SupabaseClient() {
    }

    static void setSession(String token) {
        sessionToken = token == null || token.isBlank() ? null : token;
    }

    static boolean hasSession() {
        return sessionToken != null;
    }

    /** Adds the session header when someone is signed in. */
    private static HttpRequest.Builder withSession(HttpRequest.Builder builder, String token) {
        return token == null ? builder : builder.header("x-session", token);
    }

    /**
     * Signs out: forgets the token at once and tells the server to end the session (in the background, so the screen does
     * not wait). A server that cannot be reached just lets the session expire by itself.
     */
    static void endSession() {
        final String token = sessionToken;
        sessionToken = null;
        if (token == null) {
            return;
        }
        Thread t = new Thread(() -> {
            String base = SupabaseConfig.getUrl();
            String anonKey = SupabaseConfig.getAnonKey();
            if (base == null || anonKey == null) {
                return;
            }
            try {
                HttpRequest request = withSession(HttpRequest.newBuilder(URI.create(base + "rpc/logout_user"))
                        .timeout(Duration.ofSeconds(8))
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer " + anonKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")), token).build();
                HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // the session expires by itself
            }
        }, "codequest-logout");
        t.setDaemon(true);
        t.start();
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
            HttpRequest request = withSession(HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .DELETE(), sessionToken)
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

    /** What an Edge Function answered: the HTTP status (0 when it could not be reached) and the raw body. */
    record FunctionResult(int status, String body) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Calls an Edge Function (the app key is sent as for any call; the function's own secrets stay on the server). */
    static FunctionResult callFunction(String name, String jsonBody, int timeoutSeconds) {
        String base = SupabaseConfig.getUrl();
        String anonKey = SupabaseConfig.getAnonKey();
        if (base == null || anonKey == null) {
            return new FunctionResult(0, "Supabase is not configured.");
        }
        String url = base.replace("/rest/v1/", "/functions/v1/") + name;
        try {
            HttpRequest request = withSession(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)), sessionToken)
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new FunctionResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FunctionResult(0, "Interrupted.");
        } catch (Exception e) {
            return new FunctionResult(0, "Could not reach the server: " + e.getMessage());
        }
    }

    /** Escapes text for use inside a JSON string literal (quotes, backslashes and every control character). */
    static String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
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
            HttpRequest.Builder builder = withSession(HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .header("Accept", "application/json"), sessionToken);
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
            return new JsonReader().parse(body); // a reader keeps state while parsing, so each call gets its own (calls run on several threads)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // a screen closed while its poll was in flight: nothing to report
            return null;
        } catch (Exception e) {
            System.err.println("Supabase " + method + " " + pathAndQuery + " failed: " + e.getMessage());
            return null;
        }
    }
}
