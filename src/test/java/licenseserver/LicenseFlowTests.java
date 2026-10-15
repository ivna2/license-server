package licenseserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Создание, активация, проверка и продление лицензии; подпись тикета проверяется как на стороне клиента. */
class LicenseFlowTests extends IntegrationTest {

    private static final int DURATION_DAYS = 30;

    private String admin;
    private long productId;
    private long typeId;

    @BeforeEach
    void catalog() throws Exception {
        admin = adminToken();
        productId = read(send(post("/api/products"), admin, body("name", "Product " + UUID.randomUUID()))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        typeId = read(send(post("/api/license-types"), admin,
                body("name", "Month " + UUID.randomUUID(), "defaultDurationInDays", DURATION_DAYS))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void fullLicenseLifecycleWithValidSignatures() throws Exception {
        String email = register();
        String user = accessToken(email, STRONG_PASSWORD);
        long userId = userId(email);

        // Создание
        JsonNode license = createLicense(userId, 2);
        String key = license.get("code").asText();
        assertThat(key).matches("^([A-Z2-9]{5}-){4}[A-Z2-9]{5}$");
        assertThat(license.get("userId").isNull()).isTrue();
        assertThat(license.get("endingDate").isNull()).isTrue();

        // Активация
        JsonNode activated = read(send(post("/api/licenses/activate"), user,
                body("activationKey", key, "deviceMac", "aa-bb-cc-dd-ee-01", "deviceName", "Laptop"))
                .andExpect(status().isOk()).andReturn());
        JsonNode ticket = activated.get("ticket");
        assertSignatureValid(activated);
        assertThat(ticket.get("userId").asLong()).isEqualTo(userId);
        assertThat(ticket.get("blocked").asBoolean()).isFalse();
        assertThat(ticket.get("ticketLifetimeSeconds").asLong()).isEqualTo(300);
        Instant activation = Instant.parse(ticket.get("activationDate").asText());
        Instant expiration = Instant.parse(ticket.get("expirationDate").asText());
        assertThat(Duration.between(activation, expiration)).isEqualTo(Duration.ofDays(DURATION_DAYS));
        long deviceId = ticket.get("deviceId").asLong();

        // Проверка: MAC в другом регистре и формате — то же устройство
        JsonNode checked = read(send(post("/api/licenses/check"), user,
                body("deviceMac", "AA:BB:CC:DD:EE:01", "productId", productId))
                .andExpect(status().isOk()).andReturn());
        assertSignatureValid(checked);
        assertThat(checked.get("ticket").get("deviceId").asLong()).isEqualTo(deviceId);
        assertThat(checked.get("ticket").get("expirationDate").asText()).isEqualTo(expiration.toString());

        // Продление: +30 дней от текущей даты окончания
        JsonNode renewed = read(send(post("/api/licenses/renew"), user, body("activationKey", key))
                .andExpect(status().isOk()).andReturn());
        assertSignatureValid(renewed);
        assertThat(Instant.parse(renewed.get("ticket").get("expirationDate").asText()))
                .isEqualTo(expiration.plus(Duration.ofDays(DURATION_DAYS)));

        // История
        JsonNode history = read(send(get("/api/licenses/" + license.get("id").asLong() + "/history"), admin, null)
                .andExpect(status().isOk()).andReturn());
        List<String> statuses = history.findValues("status").stream().map(JsonNode::asText).toList();
        assertThat(statuses).containsExactly("CREATED", "ACTIVATED", "RENEWED");
    }

    @Test
    void tamperedTicketFailsVerification() throws Exception {
        String email = register();
        String user = accessToken(email, STRONG_PASSWORD);
        String key = createLicense(userId(email), 1).get("code").asText();

        JsonNode response = read(send(post("/api/licenses/activate"), user,
                body("activationKey", key, "deviceMac", randomMac(), "deviceName", "PC"))
                .andExpect(status().isOk()).andReturn());
        assertSignatureValid(response);

        ObjectNode forged = response.get("ticket").deepCopy();
        forged.put("expirationDate", "2099-01-01T00:00:00Z");
        assertThat(verify(forged, response.get("signature").asText())).isFalse();
    }

    @Test
    void activationRespectsDeviceLimitAndOwnership() throws Exception {
        String email = register();
        String user = accessToken(email, STRONG_PASSWORD);
        String key = createLicense(userId(email), 1).get("code").asText();
        String mac = randomMac();

        send(post("/api/licenses/activate"), user, body("activationKey", key, "deviceMac", mac, "deviceName", "A"))
                .andExpect(status().isOk());
        // То же устройство повторно
        send(post("/api/licenses/activate"), user, body("activationKey", key, "deviceMac", mac, "deviceName", "A"))
                .andExpect(status().isConflict());
        // Лимит устройств = 1
        send(post("/api/licenses/activate"), user, body("activationKey", key, "deviceMac", randomMac(), "deviceName", "B"))
                .andExpect(status().isConflict());
        // Другой пользователь с тем же ключом
        String stranger = accessToken(register(), STRONG_PASSWORD);
        send(post("/api/licenses/activate"), stranger, body("activationKey", key, "deviceMac", randomMac(), "deviceName", "C"))
                .andExpect(status().isForbidden());
        send(post("/api/licenses/renew"), stranger, body("activationKey", key))
                .andExpect(status().isForbidden());
        // Чужое устройство при проверке не находится
        send(post("/api/licenses/check"), stranger, body("deviceMac", mac, "productId", productId))
                .andExpect(status().isNotFound());
        // Несуществующий ключ
        send(post("/api/licenses/activate"), user, body("activationKey", "NOPE", "deviceMac", randomMac(), "deviceName", "D"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blockedLicenseIsFlaggedInTicketAndCannotBeActivated() throws Exception {
        String email = register();
        String user = accessToken(email, STRONG_PASSWORD);
        JsonNode license = createLicense(userId(email), 2);
        String key = license.get("code").asText();
        String mac = randomMac();
        send(post("/api/licenses/activate"), user, body("activationKey", key, "deviceMac", mac, "deviceName", "A"))
                .andExpect(status().isOk());

        send(put("/api/licenses/" + license.get("id").asLong() + "/blocked"), admin, body("blocked", true))
                .andExpect(status().isOk());

        JsonNode checked = read(send(post("/api/licenses/check"), user, body("deviceMac", mac, "productId", productId))
                .andExpect(status().isOk()).andReturn());
        assertSignatureValid(checked);
        assertThat(checked.get("ticket").get("blocked").asBoolean()).isTrue();

        send(post("/api/licenses/activate"), user, body("activationKey", key, "deviceMac", randomMac(), "deviceName", "B"))
                .andExpect(status().isForbidden());
        send(post("/api/licenses/renew"), user, body("activationKey", key))
                .andExpect(status().isForbidden());
    }

    @Test
    void licenseRulesForCreationAndRenewal() throws Exception {
        String email = register();
        String user = accessToken(email, STRONG_PASSWORD);
        long userId = userId(email);

        // Только администратор создаёт лицензии
        send(post("/api/licenses"), user, body("productId", productId, "typeId", typeId, "ownerId", userId, "deviceCount", 1))
                .andExpect(status().isForbidden());
        // Неактивированную лицензию нельзя продлить
        String key = createLicense(userId, 1).get("code").asText();
        send(post("/api/licenses/renew"), user, body("activationKey", key)).andExpect(status().isConflict());
        // На заблокированный продукт лицензия не выдаётся
        send(put("/api/products/" + productId + "/blocked"), admin, body("blocked", true)).andExpect(status().isOk());
        send(post("/api/licenses"), admin, body("productId", productId, "typeId", typeId, "ownerId", userId, "deviceCount", 1))
                .andExpect(status().isConflict());
    }

    @Test
    void certificateIsPublic() throws Exception {
        JsonNode cert = read(mvc.perform(get("/api/signature/certificate")).andExpect(status().isOk()).andReturn());
        assertThat(cert.get("algorithm").asText()).isEqualTo("SHA256withRSA");
        assertThat(cert.get("certificate").asText()).startsWith("-----BEGIN CERTIFICATE-----");
    }

    private JsonNode createLicense(long ownerId, int deviceCount) throws Exception {
        return read(send(post("/api/licenses"), admin,
                body("productId", productId, "typeId", typeId, "ownerId", ownerId, "deviceCount", deviceCount))
                .andExpect(status().isCreated()).andReturn());
    }

    /** id пользователя берётся из ответа /auth/register. */
    private long userId(String email) {
        return registeredIds.get(email);
    }

    private final Map<String, Long> registeredIds = new java.util.HashMap<>();

    @Override
    protected String register() throws Exception {
        String email = uniqueEmail();
        JsonNode response = read(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", STRONG_PASSWORD)))
                .andExpect(status().isCreated()).andReturn());
        registeredIds.put(email, response.get("userId").asLong());
        return email;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        MockHttpServletRequestBuilder builder = bearer(request, token);
        if (content != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(builder);
    }

    private void assertSignatureValid(JsonNode ticketResponse) throws Exception {
        assertThat(verify(ticketResponse.get("ticket"), ticketResponse.get("signature").asText()))
                .as("подпись тикета проверяется открытым сертификатом сервера").isTrue();
    }

    /** Проверка подписи так, как её делает клиент: сертификат с сервера + канонический JSON полученного тикета. */
    private boolean verify(JsonNode ticket, String signatureBase64) throws Exception {
        JsonNode certInfo = read(mvc.perform(get("/api/signature/certificate")).andExpect(status().isOk()).andReturn());
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(certInfo.get("certificate").asText().getBytes(StandardCharsets.US_ASCII)));

        ObjectMapper canonical = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Map<String, Object> fields = canonical.convertValue(ticket, new TypeReference<>() {});
        byte[] data = canonical.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8);

        Signature verifier = Signature.getInstance(certInfo.get("algorithm").asText());
        verifier.initVerify(cert.getPublicKey());
        verifier.update(data);
        return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private static String randomMac() {
        byte[] b = new byte[6];
        new java.security.SecureRandom().nextBytes(b);
        b[0] = (byte) (b[0] & 0xFC);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", b[i]));
        }
        return sb.toString();
    }
}
