package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;

class TokenRevocationIT extends IntegrationTestBase {

    @Test
    void passwordChangeKillsExistingAccessTokensImmediately() throws Exception {
        String admin = provisionTenant("rev-pw");
        String host = hostOf("rev-pw");
        String token = accessToken(otpLogin(host, admin));
        Thread.sleep(1100); // tokens are second-precision; make sure the old one predates the cutoff

        mvc.perform(put("/api/me/password").with(host(host)).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("newPassword", "Brand-New-Passw0rd")))).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("token_revoked"));
        // a fresh sign-in works right away
        String fresh = accessToken(login(host, admin, "Brand-New-Passw0rd"));
        mvc.perform(get("/api/auth/me").with(host(host)).header("Authorization", bearer(fresh))).andExpect(status().isOk());
    }

    @Test
    void roleChangeForcesARefreshThatPicksUpTheNewPermissions() throws Exception {
        String admin = provisionTenant("rev-role");
        String host = hostOf("rev-role");
        String adminToken = accessToken(otpLogin(host, admin));
        String id = body(mvc.perform(jsonPost("/api/admin/users", Map.of("email", "b@rev-role.test",
                        "password", "Bobs-Passw0rd-1", "roles", List.of("TENANT_ADMIN")))
                .with(host(host)).header("Authorization", bearer(adminToken))).andReturn()).get("id").asText();
        var bob = login(host, "b@rev-role.test", "Bobs-Passw0rd-1");
        String bobToken = accessToken(bob);
        mvc.perform(get("/api/admin/users").with(host(host)).header("Authorization", bearer(bobToken))).andExpect(status().isOk());
        Thread.sleep(1100);

        mvc.perform(put("/api/admin/users/" + id).with(host(host)).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "ACTIVE", "roles", List.of("USER"))))).andExpect(status().isOk());

        mvc.perform(get("/api/admin/users").with(host(host)).header("Authorization", bearer(bobToken)))
                .andExpect(status().isUnauthorized());
        // still signed in: the refresh token is valid and the new access token carries the reduced rights
        String refreshed = accessToken(mvc.perform(post("/api/auth/refresh").with(host(host))
                .cookie(bob.getResponse().getCookie("refresh_token"))).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/admin/users").with(host(host)).header("Authorization", bearer(refreshed)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disablingAUserEndsTheirSession() throws Exception {
        String admin = provisionTenant("rev-off");
        String host = hostOf("rev-off");
        String adminToken = accessToken(otpLogin(host, admin));
        String id = body(mvc.perform(jsonPost("/api/admin/users", Map.of("email", "c@rev-off.test", "password", "Carols-Passw0rd"))
                .with(host(host)).header("Authorization", bearer(adminToken))).andReturn()).get("id").asText();
        String carol = accessToken(login(host, "c@rev-off.test", "Carols-Passw0rd"));
        Thread.sleep(1100);

        mvc.perform(put("/api/admin/users/" + id).with(host(host)).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "DISABLED", "roles", List.of("USER"))))).andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").with(host(host)).header("Authorization", bearer(carol))).andExpect(status().isUnauthorized());
    }
}
