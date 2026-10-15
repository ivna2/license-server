package licenseserver;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import licenseserver.model.SessionStatus;
import licenseserver.model.UserSession;
import licenseserver.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Перенесённые из прошлого проекта проверки аутентификации и авторизации. */
class SecurityFlowTests extends IntegrationTest {

    @Autowired
    private UserSessionRepository sessionRepository;

    @Test
    void registrationRejectsWeakPassword() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", uniqueEmail(), "password", "12345678")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRejectsDuplicateEmail() throws Exception {
        String email = register();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", STRONG_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateProductButAdminCan() throws Exception {
        String userToken = accessToken(register(), STRONG_PASSWORD);
        String product = body("name", "Product " + UUID.randomUUID());

        mvc.perform(bearer(get("/api/products"), userToken)).andExpect(status().isOk());
        mvc.perform(bearer(post("/api/products"), userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isForbidden());
        mvc.perform(bearer(post("/api/products"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
    }

    @Test
    void basicAuthWriteRequiresCsrfToken() throws Exception {
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString((ADMIN_EMAIL + ":" + ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
        String product = body("name", "Basic " + UUID.randomUUID());

        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, basic))
                .andExpect(status().isOk());
        mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isForbidden());

        MvcResult csrf = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = read(csrf).get("token").asText();
        assertThat(cookie).isNotNull();

        mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, basic)
                        .cookie(cookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshTokenCannotBeReused() throws Exception {
        String email = register();
        JsonNode first = login(email, STRONG_PASSWORD);
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode second = refresh(oldRefresh, 200);
        mvc.perform(bearer(get("/api/products"), second.get("accessToken").asText()))
                .andExpect(status().isOk());

        // Повторное использование старого refresh-токена -> 401, и все активные сессии отзываются
        refresh(oldRefresh, 401);
        refresh(second.get("refreshToken").asText(), 401);

        List<UserSession> sessions = sessionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(email)).toList();
        assertThat(sessions).hasSize(2);
        assertThat(sessions).allMatch(s -> s.getStatus() == SessionStatus.REVOKED);
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", refreshToken)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return read(result);
    }
}
