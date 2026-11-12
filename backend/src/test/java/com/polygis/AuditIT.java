package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.polygis.support.IntegrationTestBase;

class AuditIT extends IntegrationTestBase {

    @Test
    void superAdminActionsInATenantAreVisibleInThatTenantsAuditLog() throws Exception {
        String admin = provisionTenant("audit-one");
        String host = hostOf("audit-one");

        // platform operator creates a user inside the tenant via X-Tenant-ID
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "byroot@audit-one.test"))
                        .with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken()))
                        .header("X-Tenant-ID", "audit-one"))
                .andExpect(status().isCreated());

        String adminToken = accessToken(otpLogin(host, admin));
        mvc.perform(get("/api/admin/audit").param("q", "user.created").with(host(host))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("user.created"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(ROOT_EMAIL))
                .andExpect(jsonPath("$.content[0].actorTenant").value("platform"));

        // the tenant admin's own sign-in was recorded too
        mvc.perform(get("/api/admin/audit").param("q", "auth.login").with(host(host))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content[0].actorEmail").value(admin));

        // provisioning itself is logged in the platform tenant
        mvc.perform(get("/api/admin/audit").param("q", "audit-one").with(host(PLATFORM_HOST))
                        .header("Authorization", bearer(superAdminToken())))
                .andExpect(jsonPath("$.content[0].action").value("tenant.provisioned"));
    }
}
