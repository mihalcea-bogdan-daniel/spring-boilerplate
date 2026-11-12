package com.polygis.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygis.auth.OtpPurpose;
import com.polygis.mail.MailService;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Boots the whole application against a real (embedded) PostgreSQL. One database for the JVM; tests use unique
 * tenant slugs so they do not interfere. Outgoing mail is mocked so tests can read OTP codes.
 */
@SpringBootTest(properties = {
        "app.security.jwt.secret=test-secret-test-secret-test-secret-test-secret",
        "app.bootstrap.super-admin-email=root@admin.example.com",
        "app.bootstrap.super-admin-password=SuperSecret123",
        "app.security.otp.resend-cooldown=0s",
        "app.security.password.bcrypt-strength=4",
        "app.rate-limit.auth.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String PLATFORM_HOST = "admin.example.com";
    public static final String ROOT_EMAIL = "root@admin.example.com";
    public static final String ROOT_PASSWORD = "SuperSecret123";

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @MockitoBean
    protected MailService mail;

    protected static RequestPostProcessor host(String host) {
        return request -> {
            request.setServerName(host);
            return request;
        };
    }

    protected static String hostOf(String slug) {
        return slug + ".example.com";
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected MvcResult login(String host, String email, String password) throws Exception {
        return mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", password)).with(host(host)))
                .andExpect(status().isOk()).andReturn();
    }

    protected String accessToken(MvcResult result) throws Exception {
        return body(result).get("accessToken").asText();
    }

    protected String superAdminToken() throws Exception {
        return accessToken(login(PLATFORM_HOST, ROOT_EMAIL, ROOT_PASSWORD));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Provisions a tenant as super-admin and returns its admin e-mail. */
    protected String provisionTenant(String slug) throws Exception {
        String adminEmail = "admin@" + slug + ".test";
        mvc.perform(jsonPost("/api/platform/tenants", Map.of("slug", slug, "name", slug + " Inc.",
                        "adminEmail", adminEmail))
                        .with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isCreated());
        return adminEmail;
    }

    /** Last OTP "sent" to the address for the given purpose. */
    protected String lastOtp(String email, OtpPurpose purpose) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendOtp(eq(email), any(), code.capture(), eq(purpose), any());
        return code.getValue();
    }

    /** Signs in through the passwordless e-mail OTP flow. */
    protected MvcResult otpLogin(String host, String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/otp/request", Map.of("email", email)).with(host(host)))
                .andExpect(status().isAccepted());
        return mvc.perform(jsonPost("/api/auth/otp/verify",
                        Map.of("email", email, "code", lastOtp(email, OtpPurpose.LOGIN))).with(host(host)))
                .andExpect(status().isOk()).andReturn();
    }
}
