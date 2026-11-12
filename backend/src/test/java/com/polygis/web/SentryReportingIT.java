package com.polygis.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polygis.common.ApiException;
import com.polygis.support.IntegrationTestBase;

import io.sentry.ITransportFactory;
import io.sentry.Sentry;
import io.sentry.SentryEnvelope;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;

/** Unexpected errors reach Sentry with tenant/request tags; expected 4xx never do. Nothing leaves the JVM. */
@Import(SentryReportingIT.Config.class)
@TestPropertySource(properties = "sentry.dsn=https://public@sentry.invalid/1")
class SentryReportingIT extends IntegrationTestBase {

    static final List<String> SENT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Config {
        @Bean
        ITransportFactory capturingTransport() {
            return (options, details) -> new ITransport() {
                @Override
                public void send(SentryEnvelope envelope, io.sentry.Hint hint) throws java.io.IOException {
                    var out = new java.io.ByteArrayOutputStream();
                    try {
                        options.getSerializer().serialize(envelope, out);
                    } catch (Exception e) {
                        throw new java.io.IOException(e);
                    }
                    SENT.add(out.toString(StandardCharsets.UTF_8));
                }

                @Override
                public void flush(long timeoutMillis) {
                }

                @Override
                public RateLimiter getRateLimiter() {
                    return null;
                }

                @Override
                public void close(boolean isRestarting) {
                }

                @Override
                public void close() {
                }
            };
        }

        @RestController
        static class Boom {
            @GetMapping("/api/test/boom")
            String boom() {
                throw new IllegalStateException("kaboom-" + "secret-detail");
            }

            @GetMapping("/api/test/expected")
            String expected() {
                throw ApiException.badRequest("nope", "expected client error");
            }
        }
    }

    @BeforeEach
    void clear() {
        SENT.clear();
    }

    @Test
    void unexpectedErrorsAreReportedWithTagsAndHiddenFromTheClient() throws Exception {
        mvc.perform(get("/api/test/boom").with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("kaboom"))));
        Sentry.flush(2000);

        assertThat(SENT).anySatisfy(e -> assertThat(e)
                .contains("kaboom-secret-detail")
                .contains("\"tenant\":\"platform\"")
                .contains("requestId"));
    }

    @Test
    void expectedClientErrorsAreNotReported() throws Exception {
        mvc.perform(get("/api/test/expected").with(host(PLATFORM_HOST)).header("Authorization", bearer(superAdminToken())))
                .andExpect(status().isBadRequest());
        Sentry.flush(2000);
        assertThat(SENT).noneSatisfy(e -> assertThat(e).contains("expected client error"));
    }
}
