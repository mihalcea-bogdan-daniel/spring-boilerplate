package com.polygis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;

class ProblemResponsesIT extends IntegrationTestBase {

    @Test
    void everyErrorHasAStableCodeAndTheRequestId() throws Exception {
        String root = superAdminToken();

        mvc.perform(post("/api/auth/login").with(host(PLATFORM_HOST)).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_body"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(get("/api/admin/users").param("sort", "doesNotExist").with(host(PLATFORM_HOST))
                        .header("Authorization", bearer(root)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_sort"));
        // existing but hidden columns are refused too
        mvc.perform(get("/api/admin/users").param("sort", "passwordHash").with(host(PLATFORM_HOST))
                        .header("Authorization", bearer(root)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_sort"));

        mvc.perform(get("/api/admin/users/not-a-uuid").with(host(PLATFORM_HOST)).header("Authorization", bearer(root)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_parameter"));

        mvc.perform(get("/api/admin/users").param("size", "5000").with(host(PLATFORM_HOST))
                        .header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/login")
                        .with(host(PLATFORM_HOST)))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("method_not_allowed"));
    }

    @Test
    void filterErrorsAreValidJsonEvenWithHostileInput() throws Exception {
        // the requested tenant is echoed in the message: it must be encoded, not concatenated into the JSON
        mvc.perform(get("/api/admin/users").with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken()))
                        .header("X-Tenant-ID", "x\",\"code\":\"pwned"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("tenant_not_found"))
                .andExpect(jsonPath("$.detail").value("Unknown tenant x\",\"code\":\"pwned"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(get("/api/auth/me").with(host("nope.example.com")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("tenant_not_found"));
    }
}
