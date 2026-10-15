package licenseserver.signature;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Настройки ЭЦП: хранилище PKCS12 с приватным ключом и сертификатом.
 * Значения задаются переменными окружения SIGNING_KEYSTORE, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS.
 */
@ConfigurationProperties(prefix = "signature")
public record SignatureProperties(
        Resource keyStore,
        String keyStorePassword,
        String keyAlias,
        String algorithm) {
}
