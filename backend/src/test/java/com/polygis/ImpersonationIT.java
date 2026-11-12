package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.support.IntegrationTestBase;

class ImpersonationIT extends IntegrationTestBase {

    @Test
    void superAdminSignsInAsTenantUserWithAnAuditedShortSession() throws Exception {
        String adminEmail = provisionTenant("imp-one");
        String host = hostOf("imp-one");
        String adminId = body(otpLogin(host, adminEmail)).at("/user/id").asText();
        String root = superAdminToken();

        var res = mvc.perform(post("/api/platform/tenants/imp-one/impersonate/" + adminId)
                .with(host(PLATFORM_HOST)).header("Authorization", bearer(root))).andExpect(status().isOk()).andReturn();
        var url = UriComponentsBuilder.fromUriString(body(res).get("url").asText()).build();
        assertThat(url.getHost()).isEqualTo(host);
        String code = url.getQueryParams().getFirst("impersonate");

        var session = mvc.perform(jsonPost("/api/auth/impersonation/exchange", Map.of("code", code)).with(host(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(adminEmail))
                .andExpect(jsonPath("$.user.impersonatedBy").value(ROOT_EMAIL)).andReturn();
        assertThat(session.getResponse().getCookie("refresh_token")).isNull();
        String token = accessToken(session);

        // one-time code
        mvc.perform(jsonPost("/api/auth/impersonation/exchange", Map.of("code", code)).with(host(host)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/auth/me").with(host(host)).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.impersonatedBy").value(ROOT_EMAIL));

        // actions under the impersonated session name the operator in the tenant's audit log
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "x@imp-one.test")).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/audit").param("q", "user.created").with(host(host))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].actorEmail").value(adminEmail + " (via " + ROOT_EMAIL + ")"));

        // credentials are off limits in an impersonated session
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/me/password")
                        .with(host(host)).header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newPassword", "Hijacked-12345"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("impersonated"));

        // tenant admins cannot impersonate, and platform accounts are off limits
        mvc.perform(post("/api/platform/tenants/imp-one/impersonate/" + adminId)
                .with(host(host)).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/tenants/platform/impersonate/" + adminId)
                .with(host(PLATFORM_HOST)).header("Authorization", bearer(root))).andExpect(status().isForbidden());
    }
}
