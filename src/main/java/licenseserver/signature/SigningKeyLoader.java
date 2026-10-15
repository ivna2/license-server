package licenseserver.signature;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/** Загружает ключ подписи из PKCS12-хранилища и проверяет, что ключ и сертификат образуют пару. */
@Component
public class SigningKeyLoader {

    private static final byte[] PROBE = "signing-key-self-check".getBytes();

    public SigningKeys load(SignatureProperties props) {
        if (props.keyStore() == null || !props.keyStore().exists()) {
            throw new IllegalStateException("Хранилище ключа подписи не найдено: задайте SIGNING_KEYSTORE");
        }
        if (props.keyStorePassword() == null || props.keyStorePassword().isEmpty()) {
            throw new IllegalStateException("Пароль хранилища ключа подписи не задан: задайте SIGNING_KEYSTORE_PASSWORD");
        }
        char[] password = props.keyStorePassword().toCharArray();
        try (InputStream in = props.keyStore().getInputStream()) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, password);

            if (!(keyStore.getKey(props.keyAlias(), password) instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("В хранилище нет приватного ключа с алиасом " + props.keyAlias());
            }
            Certificate cert = keyStore.getCertificate(props.keyAlias());
            if (!(cert instanceof X509Certificate certificate)) {
                throw new IllegalStateException("В хранилище нет сертификата X.509 с алиасом " + props.keyAlias());
            }
            certificate.checkValidity();
            checkPair(props.algorithm(), privateKey, certificate);
            return new SigningKeys(privateKey, certificate);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить ключ подписи: " + e.getMessage(), e);
        }
    }

    private static void checkPair(String algorithm, PrivateKey privateKey, X509Certificate certificate) throws Exception {
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(privateKey);
        signer.update(PROBE);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(PROBE);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("Приватный ключ не соответствует сертификату в хранилище");
        }
    }
}
