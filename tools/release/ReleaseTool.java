import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Signs releases for the game's updater. Run with the JDK, no build needed:
 *
 * <pre>
 *   java tools/release/ReleaseTool.java keygen KEYFILE
 *   java tools/release/ReleaseTool.java sign KEYFILE INSTALLER VERSION OUTPUT_JSON
 * </pre>
 *
 * KEYFILE is the private key. Keep it outside the repository and back it up: without it no update can be published that
 * installed games accept.
 */
public class ReleaseTool {

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("keygen")) {
            keygen(Path.of(args[1]));
        } else if (args.length == 5 && args[0].equals("sign")) {
            sign(Path.of(args[1]), Path.of(args[2]), args[3], Path.of(args[4]));
        } else {
            System.err.println("usage: keygen KEYFILE | sign KEYFILE INSTALLER VERSION OUTPUT_JSON");
            System.exit(2);
        }
    }

    private static void keygen(Path keyFile) throws Exception {
        if (Files.exists(keyFile)) {
            throw new IllegalStateException(keyFile + " already exists: a new key would lock out every installed game.");
        }
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(keyFile.toAbsolutePath().getParent());
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        // the public key goes into Updater.PUBLIC_KEY; it is not secret
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static void sign(Path keyFile, Path installer, String version, Path out) throws Exception {
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(keyFile).trim())));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(installer)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, n);
            }
        }
        long size = Files.size(installer);
        String sha = HexFormat.of().formatHex(digest.digest());
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        // must match Updater.signedText
        signer.update(("codequest-update\n" + version + "\n" + size + "\n" + sha).getBytes(StandardCharsets.UTF_8));
        Files.writeString(out, "{\n  \"version\": \"" + version + "\",\n  \"size\": " + size + ",\n  \"sha256\": \"" + sha
                + "\",\n  \"signature\": \"" + Base64.getEncoder().encodeToString(signer.sign()) + "\"\n}\n");
        System.out.println("signed " + installer.getFileName() + " as version " + version);
    }
}
