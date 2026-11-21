package codequest.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * The browser half of "Continue with Google": the sign-in page opens in the person's own browser (the game never sees a
 * password), Google sends the browser back to a tiny listener on this computer (127.0.0.1) with a one-time code, and that
 * code is all this class returns. The code is useless without the random verifier kept here (PKCE), and only the server
 * (Edge Function google-login, which holds the Google secret) can swap it for the signed-in identity.
 */
final class GoogleSignIn {

    /** How long the browser may take before the sign-in is given up. */
    private static final int WAIT_SECONDS = 90;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** The one-time code and what the server needs to swap it, or a message when it did not work. */
    record Code(String code, String verifier, String redirectUri, String error) {
        static Code failed(String message) {
            return new Code(null, null, null, message);
        }
    }

    private GoogleSignIn() {
    }

    /** Opens Google in the browser and waits for the answer. Blocks (up to {@value #WAIT_SECONDS} s): call it off the render thread. */
    static Code obtainCode() {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            String redirect = "http://127.0.0.1:" + server.getLocalPort();
            String verifier = randomUrlSafe(64);
            String state = randomUrlSafe(32);
            String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

            SupabaseClient.FunctionResult start = SupabaseClient.callFunction("google-login", "{\"action\":\"url\",\"redirect_uri\":\""
                    + redirect + "\",\"code_challenge\":\"" + challenge + "\",\"state\":\"" + state + "\"}", 15);
            JsonValue answer = parse(start.body());
            if (!start.ok() || answer == null || answer.getString("url", null) == null) {
                return Code.failed(messageOf(answer, "Google sign-in is not available right now."));
            }
            Gdx.net.openURI(answer.getString("url"));

            long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
            while (System.currentTimeMillis() < deadline) {
                server.setSoTimeout((int) Math.max(1000, deadline - System.currentTimeMillis()));
                Map<String, String> query;
                try (Socket socket = server.accept()) {
                    query = readQuery(socket);
                    if (query.containsKey("code") || query.containsKey("error")) {
                        reply(socket, query.containsKey("code")
                                ? "You are signed in. You can close this tab and go back to CodeQuest."
                                : "Sign-in was cancelled. You can close this tab and go back to CodeQuest.");
                    } else {
                        reply(socket, "Waiting for the sign-in...");
                        continue; // a favicon request or similar
                    }
                } catch (java.net.SocketTimeoutException timeout) {
                    break;
                }
                if (query.containsKey("error")) {
                    return Code.failed("Google sign-in was cancelled.");
                }
                if (!state.equals(query.get("state"))) {
                    return Code.failed("Google sign-in could not be verified. Try again.");
                }
                return new Code(query.get("code"), verifier, redirect, null);
            }
            return Code.failed("Google sign-in took too long. Try again.");
        } catch (Exception e) {
            return Code.failed("Could not start Google sign-in: " + e.getMessage());
        }
    }

    static JsonValue parse(String body) {
        try {
            return body == null || body.isBlank() ? null : new JsonReader().parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    static String messageOf(JsonValue answer, String fallback) {
        String message = answer == null ? null : answer.getString("error", null);
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Reads the first request line ("GET /?code=...&state=... HTTP/1.1") and returns its query parameters. */
    private static Map<String, String> readQuery(Socket socket) throws IOException {
        socket.setSoTimeout(5000);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String line = in.readLine();
        Map<String, String> out = new HashMap<>();
        if (line == null) {
            return out;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return out;
        }
        int q = parts[1].indexOf('?');
        if (q < 0) {
            return out;
        }
        for (String pair : parts[1].substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static void reply(Socket socket, String message) throws IOException {
        byte[] body = ("<!doctype html><meta charset=utf-8><title>CodeQuest</title>"
                + "<body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>" + message + "</h2></body>")
                .getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
