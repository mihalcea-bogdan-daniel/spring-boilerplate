package com.polygis;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.support.IntegrationTestBase;

class PlanIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void userQuotaOfThePlanIsEnforcedAndLiftedByAnUpgrade() throws Exception {
        String admin = provisionTenant("plan-one");
        String host = hostOf("plan-one");
        String id = jdbc.queryForObject("select id::text from public.tenants where slug = 'plan-one'", String.class);
        String root = superAdminToken();
        setPlan(id, "tiny", root); // 2 users
        String token = accessToken(otpLogin(host, admin));

        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "two@plan-one.test")).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "three@plan-one.test")).with(host(host))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("quota_exceeded"));
        // self-registration counts against the same quota
        mvc.perform(jsonPost("/api/auth/register", Map.of("email", "self@plan-one.test", "password", "Passw0rd-12345"))
                .with(host(host))).andExpect(status().isPaymentRequired());

        setPlan(id, "enterprise", root);
        mvc.perform(jsonPost("/api/admin/users", Map.of("email", "three@plan-one.test")).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isCreated());
    }

    private void setPlan(String tenantId, String plan, String root) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/platform/tenants/" + tenantId)
                        .with(host(PLATFORM_HOST)).header("Authorization", bearer(root))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Plan One", "status", "ACTIVE", "plan", plan))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value(plan));
    }
}
