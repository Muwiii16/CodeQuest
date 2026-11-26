package codequest.update;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.DoubleConsumer;

/**
 * Keeps the installed game up to date. A release on GitHub carries the installer and a small manifest
 * ({@code update.json}: version, size, SHA-256 and a signature). The game accepts an installer only when the manifest is
 * signed with the team's private key (made by tools/release/ReleaseTool.java, never in the repository), the version is
 * newer than the one running, and the downloaded file has exactly the signed size and SHA-256. Anything else is ignored
 * and the player simply goes on with the version they have.
 */
public final class Updater {

    /** The public half of the release signing key. */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEALZGMtJ02i14th2g3e/ZOhEUF8BTw71gPWSu1D6f2NPw=";

    private static final String RELEASES = "https://github.com/Muwiii16/CodeQuest/releases/";
    private static final String INSTALLER = "CodeQuest-Setup.exe";
    /** An update that keeps failing (blocked, no disk space) is tried this often, then the game stops trying it. */
    private static final int MAX_ATTEMPTS = 2;
    private static final long MAX_BYTES = 400L * 1024 * 1024;

    /** A newer version that is signed and ready to be downloaded. */
    public record Release(String version, long size, String sha256) {
    }

    private final String base;
    private final String running;
    private final PublicKey key;
    private final Path state;
    private final Path downloads;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5)).build();

    Updater(String base, String running, PublicKey key, Path state, Path downloads) {
        this.base = base.endsWith("/") ? base : base + "/";
        this.running = running;
        this.key = key;
        this.state = state;
        this.downloads = downloads;
    }

    /**
     * The updater of an installed Windows copy; empty when the game runs from source (no packaged version number) or on
     * another system, which have nothing to update. The {@code codequest.update.*} properties exist for testing; a manifest
     * from anywhere still has to carry a valid signature.
     */
    public static Optional<Updater> installed() {
        String version = System.getProperty("jpackage.app-version");
        if (version == null || !System.getProperty("os.name", "").startsWith("Windows")) {
            return Optional.empty();
        }
        try {
            Path home = Path.of(System.getProperty("user.home"), ".codequest");
            return Optional.of(new Updater(System.getProperty("codequest.update.base", RELEASES), version, publicKey(PUBLIC_KEY),
                    Path.of(System.getProperty("codequest.update.state", home.resolve("update-attempt.txt").toString())),
                    Path.of(System.getProperty("java.io.tmpdir"), "codequest-update")));
        } catch (GeneralSecurityException | RuntimeException e) {
            System.err.println("Updater: not available: " + e);
            return Optional.empty();
        }
    }

    public String runningVersion() {
        return running;
    }

    /** Asks for the newest release. Empty when there is nothing newer, it cannot be trusted, or there is no connection. */
    public Optional<Release> check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "latest/download/update.json"))
                    .timeout(Duration.ofSeconds(6)).header("User-Agent", "CodeQuest").build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return read(response.body(), key).filter(r -> newer(r.version(), running) && attempts(r.version()) < MAX_ATTEMPTS);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException e) {
            System.err.println("Updater: check failed: " + e);
            return Optional.empty();
        }
    }

    /** The release a manifest describes, if it is well formed and its signature is the team's. */
    static Optional<Release> read(String manifest, PublicKey key) {
        try {
            JsonValue root = new JsonReader().parse(manifest);
            String version = root.getString("version");
            long size = root.getLong("size");
            String sha = root.getString("sha256").toLowerCase();
            if (!version.matches("\\d{1,4}(\\.\\d{1,4}){1,3}") || !sha.matches("[0-9a-f]{64}") || size <= 0 || size > MAX_BYTES) {
                return Optional.empty();
            }
            Signature verify = Signature.getInstance("Ed25519");
            verify.initVerify(key);
            verify.update(signedText(version, size, sha));
            return verify.verify(Base64.getDecoder().decode(root.getString("signature")))
                    ? Optional.of(new Release(version, size, sha)) : Optional.empty();
        } catch (GeneralSecurityException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** What the signature covers. ReleaseTool builds the same text. */
    static byte[] signedText(String version, long size, String sha256) {
        return ("codequest-update\n" + version + "\n" + size + "\n" + sha256).getBytes(StandardCharsets.UTF_8);
    }

    static PublicKey publicKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** True when {@code a} is a later version than {@code b}, comparing number by number ("1.0.10" is after "1.0.9"). */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length ? number(x[i]) : 0;
            int right = i < y.length ? number(y[i]) : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Downloads the release's installer, reporting progress from 0 to 1, and returns it only if it is exactly the signed
     * file. A wrong or broken download is deleted and reported as an error.
     */
    public Path download(Release release, DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(downloads);
        Path file = downloads.resolve("CodeQuest-Setup-" + release.version() + ".exe");
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "download/v" + release.version() + "/" + INSTALLER))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "CodeQuest").build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        boolean good = false;
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("The update could not be downloaded (HTTP " + response.statusCode() + ").");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (OutputStream out = Files.newOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) >= 0; ) {
                    total += n;
                    if (total > release.size()) {
                        throw new IOException("The downloaded update is bigger than the signed file.");
                    }
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                    progress.accept((double) total / release.size());
                }
            }
            if (total != release.size() || !HexFormat.of().formatHex(digest.digest()).equals(release.sha256())) {
                throw new IOException("The downloaded update does not match the signed file.");
            }
            good = true;
            return file;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        } finally {
            if (!good) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * Starts the downloaded installer without any questions; it replaces the game and opens it again. The caller must exit
     * the game right after. Throws if Windows refuses to start it.
     */
    public void install(Release release, Path installer) throws IOException {
        recordAttempt(release.version());
        new ProcessBuilder(installer.toString(), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS", "/UPDATE")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    int attempts(String version) {
        try {
            String[] saved = Files.readString(state).trim().split(" ");
            return saved.length == 2 && saved[0].equals(version) ? number(saved[1]) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    void recordAttempt(String version) {
        try {
            int next = attempts(version) + 1;
            if (state.getParent() != null) {
                Files.createDirectories(state.getParent());
            }
            Files.writeString(state, version + " " + next);
        } catch (IOException e) {
            System.err.println("Updater: could not note the attempt: " + e);
        }
    }
}
