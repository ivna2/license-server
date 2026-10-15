package licenseserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Общая настройка интеграционных тестов: реальная БД, временные ключи и случайные учётные данные администратора. */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-at-least-32-bytes-long!!",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc
abstract class IntegrationTest {

    // Учётные данные администратора генерируются на каждый запуск и не хранятся в коде
    static final String ADMIN_EMAIL = "admin-" + UUID.randomUUID() + "@test.local";
    static final String ADMIN_PASSWORD = "Aa1!" + UUID.randomUUID();
    static final String STRONG_PASSWORD = "Str0ng!Pass";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("admin.email", () -> ADMIN_EMAIL);
        registry.add("admin.password", () -> ADMIN_PASSWORD);
        registry.add("signature.key-store", () -> "file:" + TestSigningKeys.keyStore());
        registry.add("signature.key-store-password", () -> TestSigningKeys.PASSWORD);
        registry.add("signature.key-alias", () -> TestSigningKeys.ALIAS);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String register() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", STRONG_PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    protected JsonNode login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", password)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected String accessToken(String email, String password) throws Exception {
        return login(email, password).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected String body(Object... keyValues) throws Exception {
        var node = json.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.set((String) keyValues[i], json.valueToTree(keyValues[i + 1]));
        }
        return json.writeValueAsString(node);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }
}
