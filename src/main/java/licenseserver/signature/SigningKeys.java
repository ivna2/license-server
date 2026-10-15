package licenseserver.signature;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** Ключевая пара для подписи: приватный ключ и сертификат с открытым ключом. */
public record SigningKeys(PrivateKey privateKey, X509Certificate certificate) {
}
