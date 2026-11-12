package com.polygis.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import com.polygis.support.IntegrationTestBase;

/** Real Tomcat (not MockMvc), because the forwarded-header trust lives in Tomcat's RemoteIpValve. */
class ForwardedHeadersIT {

    static int status(int port, String forwardedHost) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/branding"))
                .header("X-Forwarded-Host", forwardedHost).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class FromTrustedProxy extends IntegrationTestBase {
        @LocalServerPort
        int port;

        @Test
        void forwardedHostSelectsTheTenant() throws Exception {
            assertThat(status(port, PLATFORM_HOST)).isEqualTo(200);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254")
    class FromUntrustedClient extends IntegrationTestBase {
        @LocalServerPort
        int port;

        @Test
        void forwardedHostIsIgnored() throws Exception {
            // the header is dropped, 127.0.0.1 is no tenant host -> 404
            assertThat(status(port, PLATFORM_HOST)).isEqualTo(404);
        }
    }
}
