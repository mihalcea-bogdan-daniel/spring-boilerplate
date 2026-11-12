package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.polygis.support.IntegrationTestBase;

/**
 * Writes the live OpenAPI document to build/openapi.json on every test run. The frontend turns it into
 * TypeScript types with {@code npm run gen:api}, so client and server types cannot drift apart.
 */
class OpenApiExportIT extends IntegrationTestBase {

    @Test
    void exportsOpenApiDocument() throws Exception {
        String spec = mvc.perform(get("/api/docs").with(host(PLATFORM_HOST)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var paths = json.readTree(spec).get("paths");
        assertThat(paths.has("/api/auth/login")).isTrue();
        assertThat(paths.has("/api/admin/users")).isTrue();
        assertThat(paths.has("/api/platform/tenants")).isTrue();

        Path out = Path.of("build", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(spec)));
    }
}
