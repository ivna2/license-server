package licenseserver.signature;

import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;

/** Подписывает данные приватным ключом сервера и проверяет подписи открытым ключом из сертификата. */
@Service
public class SignatureService {

    private final SigningKeys keys;
    private final CanonicalJson canonicalJson;
    private final String algorithm;

    public SignatureService(SignatureProperties props, SigningKeyLoader loader, CanonicalJson canonicalJson) {
        this.keys = loader.load(props);
        this.canonicalJson = canonicalJson;
        this.algorithm = props.algorithm();
    }

    /** Подпись канонического JSON объекта, Base64. */
    public String sign(Object payload) {
        return Base64.getEncoder().encodeToString(signBytes(canonicalJson.toBytes(payload)));
    }

    public boolean verify(Object payload, String signatureBase64) {
        try {
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(keys.certificate().getPublicKey());
            verifier.update(canonicalJson.toBytes(payload));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    public String algorithm() {
        return algorithm;
    }

    /** Сертификат с открытым ключом в формате PEM — для проверки подписи на стороне клиента. */
    public String certificatePem() {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.certificate().getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] signBytes(byte[] data) {
        try {
            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(keys.privateKey());
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось подписать данные", e);
        }
    }
}
