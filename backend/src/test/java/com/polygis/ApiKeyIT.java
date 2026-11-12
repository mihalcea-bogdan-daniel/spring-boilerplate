package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.support.IntegrationTestBase;

class ApiKeyIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void apiKeyWorksOnlyInItsTenantWithItsPermissionsUntilRevoked() throws Exception {
        String admin = provisionTenant("key-a");
        provisionTenant("key-b");
        String host = hostOf("key-a");
        String token = accessToken(otpLogin(host, admin));
        var body = Map.of("name", "reader", "permissions", List.of("user:read"));

        // not part of the free plan
        mvc.perform(jsonPost("/api/admin/api-keys", body).with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("feature_unavailable"));
        jdbc.update("update public.tenants set plan = 'pro' where slug = 'key-a'");

        // cannot mint a key with permissions the creator does not hold
        mvc.perform(jsonPost("/api/admin/api-keys", Map.of("name", "x", "permissions", List.of("tenant:manage")))
                .with(host(host)).header("Authorization", bearer(token))).andExpect(status().isForbidden());

        var created = body(mvc.perform(jsonPost("/api/admin/api-keys", body).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isCreated()).andReturn());
        String key = created.get("key").asText();
        String id = created.at("/apiKey/id").asText();

        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        // only the granted permission
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "no@key-a.test")).with(host(host))
                .header("X-API-Key", key)).andExpect(status().isForbidden());
        // another tenant's host does not know this key, and X-Tenant-ID cannot redirect it
        mvc.perform(get("/api/admin/users").with(host(hostOf("key-b"))).header("X-API-Key", key))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key).header("X-Tenant-ID", "key-b"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].email").value(admin));
        // personal endpoints need a real user
        mvc.perform(put("/api/me").with(host(host)).header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // the list never contains the secret
        mvc.perform(get("/api/admin/api-keys").with(host(host)).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].keyPrefix").isNotEmpty()).andExpect(jsonPath("$[0].key").doesNotExist());

        mvc.perform(delete("/api/admin/api-keys/" + id).with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keysDieWithTheirOwnerAndPauseOnPlanDowngrade() throws Exception {
        String admin = provisionTenant("key-life");
        String host = hostOf("key-life");
        jdbc.update("update public.tenants set plan = 'pro' where slug = 'key-life'");
        String adminToken = accessToken(otpLogin(host, admin));
        String ownerId = body(mvc.perform(jsonPost("/api/admin/users", Map.of("email", "o@key-life.test",
                        "password", "Owners-Passw0rd", "roles", List.of("TENANT_ADMIN")))
                .with(host(host)).header("Authorization", bearer(adminToken))).andReturn()).get("id").asText();
        String ownerToken = accessToken(login(host, "o@key-life.test", "Owners-Passw0rd"));
        String key = body(mvc.perform(jsonPost("/api/admin/api-keys", Map.of("name", "k", "permissions", List.of("user:read")))
                .with(host(host)).header("Authorization", bearer(ownerToken))).andReturn()).get("key").asText();
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key)).andExpect(status().isOk());

        // plan downgrade pauses the key, upgrade resumes it
        jdbc.update("update public.tenants set plan = 'free' where slug = 'key-life'");
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key)).andExpect(status().isUnauthorized());
        jdbc.update("update public.tenants set plan = 'pro' where slug = 'key-life'");
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key)).andExpect(status().isOk());

        // deleting the owner revokes the key for good
        mvc.perform(delete("/api/admin/users/" + ownerId).with(host(host)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users").with(host(host)).header("X-API-Key", key)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPlansGrantNothingAndCannotBeAssigned() throws Exception {
        String admin = provisionTenant("key-plan");
        String host = hostOf("key-plan");
        String id = jdbc.queryForObject("select id::text from public.tenants where slug = 'key-plan'", String.class);
        mvc.perform(put("/api/platform/tenants/" + id).with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "x", "status", "ACTIVE", "plan", "platinum"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_plan"));

        jdbc.update("update public.tenants set plan = 'retired-plan' where slug = 'key-plan'");
        String token = accessToken(otpLogin(host, admin));
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "n@key-plan.test")).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isPaymentRequired());
    }
}
