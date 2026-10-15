package licenseserver;

import java.nio.file.Files;
import java.nio.file.Path;

/** Временное хранилище ключа ЭЦП для тестов: создаётся keytool при первом обращении и не хранится в репозитории. */
final class TestSigningKeys {

    static final String ALIAS = "license-signing";
    static final String PASSWORD = "test-" + java.util.UUID.randomUUID();
    private static Path keyStore;

    private TestSigningKeys() {
    }

    static synchronized Path keyStore() {
        if (keyStore == null) {
            try {
                Path dir = Files.createTempDirectory("signing-test");
                Path file = dir.resolve("signing.p12");
                String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
                Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA",
                        "-keysize", "2048", "-validity", "2", "-dname", "CN=Test Signing",
                        "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", PASSWORD)
                        .redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes());
                if (p.waitFor() != 0) {
                    throw new IllegalStateException("keytool failed: " + output);
                }
                file.toFile().deleteOnExit();
                dir.toFile().deleteOnExit();
                keyStore = file;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return keyStore;
    }
}
