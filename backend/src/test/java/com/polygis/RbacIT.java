package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;

class RbacIT extends IntegrationTestBase {

    @Test
    void userManagersCannotEscalateOrTouchMorePrivilegedUsers() throws Exception {
        String admin = provisionTenant("rbac-one");
        String host = hostOf("rbac-one");
        var adminSession = otpLogin(host, admin);
        String adminToken = accessToken(adminSession);
        String adminId = body(adminSession).at("/user/id").asText();

        // a "user manager" role: can manage users but holds nothing else
        mvc.perform(jsonPost("/api/admin/roles", Map.of("name", "USER_MANAGER", "permissions", List.of("user:read", "user:write")))
                .with(host(host)).header("Authorization", bearer(adminToken))).andExpect(status().isCreated());
        // (also a USER: handing out USER requires holding its permissions, e.g. org:read and project:*)
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "mgr@rbac-one.test", "password", "Manager-Passw0rd",
                        "roles", List.of("USER_MANAGER", "USER")))
                .with(host(host)).header("Authorization", bearer(adminToken))).andExpect(status().isCreated());
        String mgr = accessToken(login(host, "mgr@rbac-one.test", "Manager-Passw0rd"));

        // cannot hand out TENANT_ADMIN (it carries permissions the manager lacks) ...
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "evil@rbac-one.test", "roles", List.of("TENANT_ADMIN")))
                        .with(host(host)).header("Authorization", bearer(mgr)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("permission_escalation"));
        // ... nor disable or delete the admin, who has more permissions
        mvc.perform(put("/api/admin/users/" + adminId).with(host(host)).header("Authorization", bearer(mgr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "DISABLED", "roles", List.of("USER")))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + adminId).with(host(host)).header("Authorization", bearer(mgr)))
                .andExpect(status().isForbidden());
        // regular users are fine
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "ok@rbac-one.test", "roles", List.of("USER")))
                .with(host(host)).header("Authorization", bearer(mgr))).andExpect(status().isCreated());
    }

    @Test
    void roleEditorsCannotGrantWhatTheyDoNotHoldAndAdminRoleIsFixed() throws Exception {
        String admin = provisionTenant("rbac-two");
        String host = hostOf("rbac-two");
        String adminToken = accessToken(otpLogin(host, admin));
        mvc.perform(jsonPost("/api/admin/roles", Map.of("name", "ROLE_EDITOR", "permissions", List.of("role:read", "role:write")))
                .with(host(host)).header("Authorization", bearer(adminToken))).andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "ed@rbac-two.test", "password", "Editor-Passw0rd",
                "roles", List.of("ROLE_EDITOR"))).with(host(host)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated());
        String editor = accessToken(login(host, "ed@rbac-two.test", "Editor-Passw0rd"));

        mvc.perform(jsonPost("/api/admin/roles", Map.of("name", "SNEAKY", "permissions", List.of("user:write")))
                        .with(host(host)).header("Authorization", bearer(editor)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("permission_escalation"));

        var roles = body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/roles")
                .with(host(host)).header("Authorization", bearer(adminToken))).andReturn());
        String tenantAdminRole = null;
        for (var r : roles) if (r.get("name").asText().equals("TENANT_ADMIN")) tenantAdminRole = r.get("id").asText();
        mvc.perform(put("/api/admin/roles/" + tenantAdminRole).with(host(host)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "TENANT_ADMIN", "permissions", List.of("user:read")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLastActiveAdminCannotBeRemoved() throws Exception {
        String admin = provisionTenant("rbac-last");
        String host = hostOf("rbac-last");
        var session = otpLogin(host, admin);
        String token = accessToken(session);
        String adminId = body(session).at("/user/id").asText();

        mvc.perform(put("/api/admin/users/" + adminId).with(host(host)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "ACTIVE", "roles", List.of("USER")))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("last_admin"));

        // with a second admin the first one may step down
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "admin2@rbac-last.test", "password", "Second-Passw0rd",
                "roles", List.of("TENANT_ADMIN"))).with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/admin/users/" + adminId).with(host(host)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "ACTIVE", "roles", List.of("USER")))))
                .andExpect(status().isOk());
    }
}
