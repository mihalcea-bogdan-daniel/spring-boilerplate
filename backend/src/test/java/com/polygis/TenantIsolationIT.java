package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.auth.OtpPurpose;
import com.polygis.support.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

class TenantIsolationIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisioningCreatesAnIsolatedSchemaWithAnAdmin() throws Exception {
        String adminEmail = provisionTenant("iso-one");

        assertThat(jdbc.queryForObject("select count(*) from t_iso_one.users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from public.tenants where slug = 'iso-one'", String.class))
                .isEqualTo("ACTIVE");

        String token = accessToken(otpLogin(hostOf("iso-one"), adminEmail));
        mvc.perform(get("/api/admin/users").with(host(hostOf("iso-one"))).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(adminEmail));
    }

    @Test
    void tokenOfOneTenantIsRejectedOnAnotherHostAndCannotSwitchByHeader() throws Exception {
        String adminA = provisionTenant("iso-a");
        provisionTenant("iso-b");
        String tokenA = accessToken(otpLogin(hostOf("iso-a"), adminA));

        mvc.perform(get("/api/admin/users").with(host(hostOf("iso-b"))).header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(host(hostOf("iso-a"))).header("Authorization", bearer(tokenA))
                        .header("X-Tenant-ID", "iso-b"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/tenants").with(host(hostOf("iso-a"))).header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminManagesTenantThroughHeader() throws Exception {
        String adminEmail = provisionTenant("iso-managed");
        String root = superAdminToken();

        mvc.perform(get("/api/admin/users").with(host(PLATFORM_HOST)).header("Authorization", bearer(root))
                        .header("X-Tenant-ID", "iso-managed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(adminEmail));

        // without the header the super-admin sees the platform tenant's own users
        mvc.perform(get("/api/admin/users").with(host(PLATFORM_HOST)).header("Authorization", bearer(root)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(ROOT_EMAIL));

        // profile always comes from the token's home tenant, even while switched
        mvc.perform(get("/api/auth/me").with(host(PLATFORM_HOST)).header("Authorization", bearer(root))
                        .header("X-Tenant-ID", "iso-managed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superAdmin").value(true));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/status").with(host(PLATFORM_HOST)).secure(true))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Content-Security-Policy"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("Strict-Transport-Security"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"));
    }

    @Autowired
    com.polygis.tenancy.TenantRegistry registry;

    @Test
    void registryPicksUpChangesMadeByAnotherNodeThroughNotify() throws Exception {
        provisionTenant("iso-notify");
        // simulate another node suspending the tenant: change the row behind this node's back, then NOTIFY
        jdbc.update("update public.tenants set status = 'SUSPENDED' where slug = 'iso-notify'");
        jdbc.execute("NOTIFY tenant_registry");

        long deadline = System.currentTimeMillis() + 5_000;
        while (registry.requireBySlug("iso-notify").isActive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(registry.requireBySlug("iso-notify").isActive()).isFalse();
        mvc.perform(get("/api/auth/me").with(host(hostOf("iso-notify")))).andExpect(status().isForbidden());
    }

    @Autowired
    com.polygis.tenancy.TenantSchemaMigrator migrator;

    @Test
    void startupMigrationRunsInParallelAndReportsEveryFailingSchema() throws Exception {
        provisionTenant("iso-par-a");
        provisionTenant("iso-par-b");
        // re-running is idempotent
        migrator.migrateAll();

        // a tenant whose history disagrees with the scripts must fail loudly, naming the schema
        jdbc.update("update t_iso_par_b.flyway_schema_history set checksum = 1 where version = '1'");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(migrator::migrateAll)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("t_iso_par_b")
                    .hasMessageNotContaining("t_iso_par_a");
        } finally {
            jdbc.update("delete from public.tenants where slug = 'iso-par-b'");
            jdbc.execute("drop schema t_iso_par_b cascade");
            registry.reload();
        }
    }

    @Test
    void unknownHostIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").with(host("nope.example.com"))).andExpect(status().isNotFound());
    }

    @Test
    void registerVerifyLoginAndRefreshRotation() throws Exception {
        provisionTenant("iso-auth");
        String host = hostOf("iso-auth");
        String email = "jane@iso-auth.test";

        mvc.perform(jsonPost("/api/auth/register", Map.of("email", email, "password", "Passw0rd-12345"))
                .with(host(host))).andExpect(status().isAccepted());
        // not verified yet
        mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", "Passw0rd-12345"))
                .with(host(host))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("email_not_verified"));
        mvc.perform(jsonPost("/api/auth/verify-email",
                        Map.of("email", email, "code", lastOtp(email, OtpPurpose.EMAIL_VERIFICATION))).with(host(host)))
                .andExpect(status().isOk());

        mvc.perform(jsonPost("/api/auth/login", Map.of("email", email, "password", "wrong-password-1"))
                .with(host(host))).andExpect(status().isUnauthorized());

        var login = login(host, email, "Passw0rd-12345");
        Cookie first = login.getResponse().getCookie("refresh_token");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();

        var refreshed = mvc.perform(post("/api/auth/refresh").with(host(host)).cookie(first))
                .andExpect(status().isOk()).andReturn();
        Cookie second = refreshed.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // replaying the rotated token is treated as theft: the whole family dies
        mvc.perform(post("/api/auth/refresh").with(host(host)).cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(host(host)).cookie(second)).andExpect(status().isUnauthorized());

        // a regular user has no admin permissions
        mvc.perform(get("/api/admin/users").with(host(host)).header("Authorization", bearer(accessToken(login))))
                .andExpect(status().isForbidden());
    }
}
