package codequest.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {

    private static final byte[] INSTALLER = "pretend this is an installer".repeat(5000).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temp;
    private HttpServer server;
    private KeyPair team;
    private volatile String manifest = "";
    private volatile byte[] served = INSTALLER;

    @BeforeEach
    void start() throws Exception {
        team = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest/download/update.json", x -> {
            byte[] body = manifest.getBytes(StandardCharsets.UTF_8);
            x.sendResponseHeaders(200, body.length);
            x.getResponseBody().write(body);
            x.close();
        });
        server.createContext("/download/v1.0.2/CodeQuest-Setup.exe", x -> {
            byte[] body = served;
            x.sendResponseHeaders(200, body.length);
            x.getResponseBody().write(body);
            x.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Updater updater(String running) {
        return new Updater("http://127.0.0.1:" + server.getAddress().getPort(), running, team.getPublic(),
                temp.resolve("attempt.txt"), temp.resolve("downloads"));
    }

    private static String manifest(KeyPair key, String version, byte[] file) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update(Updater.signedText(version, file.length, sha));
        return "{\"version\":\"" + version + "\",\"size\":" + file.length + ",\"sha256\":\"" + sha + "\",\"signature\":\""
                + Base64.getEncoder().encodeToString(signer.sign()) + "\"}";
    }

    @Test
    void versionsAreComparedNumberByNumber() {
        assertTrue(Updater.newer("1.0.10", "1.0.9"));
        assertTrue(Updater.newer("1.1", "1.0.9"));
        assertFalse(Updater.newer("1.0.1", "1.0.1"));
        assertFalse(Updater.newer("1.0.0", "1.0.1"));
        assertFalse(Updater.newer("1.0", "1.0.0"));
    }

    @Test
    void theEmbeddedKeyIsAUsableKey() throws Exception {
        assertEquals("EdDSA", Updater.publicKey(Updater.PUBLIC_KEY).getAlgorithm());
    }

    @Test
    void aSignedNewerReleaseIsDownloadedExactly() throws Exception {
        manifest = manifest(team, "1.0.2", INSTALLER);
        Updater u = updater("1.0.1");
        Updater.Release release = u.check().orElseThrow();
        double[] last = new double[1];
        Path file = u.download(release, p -> last[0] = p);
        assertArrayEquals(INSTALLER, Files.readAllBytes(file));
        assertEquals(1.0, last[0], 1e-9);
    }

    @Test
    void theSameOrAnOlderVersionIsIgnored() throws Exception {
        manifest = manifest(team, "1.0.2", INSTALLER);
        assertTrue(updater("1.0.2").check().isEmpty());
        assertTrue(updater("1.0.3").check().isEmpty());
    }

    @Test
    void aManifestSignedBySomeoneElseIsIgnored() throws Exception {
        manifest = manifest(KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), "1.0.2", INSTALLER);
        assertTrue(updater("1.0.1").check().isEmpty());
    }

    @Test
    void aChangedManifestIsIgnored() throws Exception {
        manifest = manifest(team, "1.0.2", INSTALLER).replace("\"1.0.2\"", "\"9.9.9\"");
        assertTrue(updater("1.0.1").check().isEmpty());
        manifest = "<html>not a manifest</html>";
        assertTrue(updater("1.0.1").check().isEmpty());
    }

    @Test
    void aSwappedInstallerIsRefusedAndDeleted() throws Exception {
        manifest = manifest(team, "1.0.2", INSTALLER);
        Updater u = updater("1.0.1");
        Updater.Release release = u.check().orElseThrow();
        byte[] other = INSTALLER.clone();
        other[100] ^= 1;
        served = other;
        assertThrows(IOException.class, () -> u.download(release, p -> { }));
        assertFalse(Files.exists(temp.resolve("downloads").resolve("CodeQuest-Setup-1.0.2.exe")));
    }

    @Test
    void anUpdateThatKeepsFailingIsNotTriedForever() throws Exception {
        manifest = manifest(team, "1.0.2", INSTALLER);
        Updater u = updater("1.0.1");
        u.recordAttempt("1.0.2");
        assertTrue(u.check().isPresent());
        u.recordAttempt("1.0.2");
        assertTrue(u.check().isEmpty());
        assertEquals(0, u.attempts("1.0.3"));
    }

    @Test
    void noConnectionMeansNoUpdate() {
        server.stop(0);
        assertTrue(updater("1.0.1").check().isEmpty());
    }
}
