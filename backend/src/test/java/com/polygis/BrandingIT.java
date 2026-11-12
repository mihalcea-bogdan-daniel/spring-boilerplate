package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;

class BrandingIT extends IntegrationTestBase {

    @Test
    void brandingIsPublicPerTenantAndValidated() throws Exception {
        String admin = provisionTenant("brand-a");
        provisionTenant("brand-b");
        String token = accessToken(otpLogin(hostOf("brand-a"), admin));

        mvc.perform(put("/api/admin/settings").with(host(hostOf("brand-a"))).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "general.displayName", "Brand A", "brand.primaryColor", "indigo",
                                "brand.logoUrl", "https://cdn.example.com/a.svg"))))
                .andExpect(status().isOk());

        // anonymous, and each host sees only its own tenant's branding
        mvc.perform(get("/api/auth/branding").with(host(hostOf("brand-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Brand A"))
                .andExpect(jsonPath("$.primaryColor").value("indigo"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.com/a.svg"));
        mvc.perform(get("/api/auth/branding").with(host(hostOf("brand-b"))))
                .andExpect(jsonPath("$.primaryColor").value("brand"))
                .andExpect(jsonPath("$.displayName").doesNotExist());

        mvc.perform(put("/api/admin/settings").with(host(hostOf("brand-a"))).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("brand.primaryColor", "red; background:url(x)"))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").with(host(hostOf("brand-a"))).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("brand.logoUrl", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
    }
}
