package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;

class AccountIT extends IntegrationTestBase {

    @Test
    void invitedAdminSetsPasswordThenMustProvideCurrentOneToChangeIt() throws Exception {
        String email = provisionTenant("acct-one");
        String host = hostOf("acct-one");
        String token = accessToken(otpLogin(host, email));

        mvc.perform(put("/api/me").with(host(host)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("firstName", "Ada", "lastName", "Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.hasPassword").value(false));

        // no password yet -> no current password needed
        mvc.perform(put("/api/me/password").with(host(host)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newPassword", "FirstPassw0rd!"))))
                .andExpect(status().isNoContent());

        String token2 = accessToken(login(host, email, "FirstPassw0rd!"));
        mvc.perform(put("/api/me/password").with(host(host)).header("Authorization", bearer(token2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "nope", "newPassword", "SecondPassw0rd!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("wrong_password"));
        mvc.perform(put("/api/me/password").with(host(host)).header("Authorization", bearer(token2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("currentPassword", "FirstPassw0rd!", "newPassword", "SecondPassw0rd!"))))
                .andExpect(status().isNoContent());
        login(host, email, "SecondPassw0rd!");
    }

    @Test
    void adminInvitesUserWhoActivatesWithEmailCode() throws Exception {
        String admin = provisionTenant("acct-invite");
        String host = hostOf("acct-invite");
        String adminToken = accessToken(otpLogin(host, admin));

        var created = mvc.perform(jsonPost("/api/admin/users", Map.of("email", "new@acct-invite.test"))
                        .with(host(host)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        org.mockito.Mockito.verify(mail).sendUserInvite(org.mockito.ArgumentMatchers.eq("new@acct-invite.test"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(host));

        String id = body(created).get("id").asText();
        mvc.perform(jsonPost("/api/admin/users/" + id + "/invite", Map.of())
                .with(host(host)).header("Authorization", bearer(adminToken))).andExpect(status().isAccepted());

        var session = otpLogin(host, "new@acct-invite.test");
        org.assertj.core.api.Assertions.assertThat(body(session).at("/user/roles/0").asText()).isEqualTo("USER");
        mvc.perform(jsonPost("/api/admin/users/" + id + "/invite", Map.of())
                        .with(host(host)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }
}
