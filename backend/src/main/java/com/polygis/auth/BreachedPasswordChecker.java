package com.polygis.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.polygis.common.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Rejects passwords that appear in known data breaches, using the Have I Been Pwned range API.
 * k-anonymity: only the first 5 hex characters of the SHA-1 hash leave the server, never the password.
 * Fails open (accepts the password) if the service cannot be reached.
 */
@Slf4j
@Component
public class BreachedPasswordChecker {

    private final boolean enabled;
    private final RestClient rest;

    public BreachedPasswordChecker(@Value("${app.security.password.check-breached:true}") boolean enabled) {
        this.enabled = enabled;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.rest = RestClient.builder().requestFactory(factory)
                .baseUrl("https://api.pwnedpasswords.com").build();
    }

    /** @throws ApiException 400 {@code password_breached} if the password is known to be compromised */
    public void assertNotBreached(String password) {
        if (!enabled || password == null) return;
        String hash = sha1Hex(password);
        try {
            String body = rest.get().uri("/range/{prefix}", hash.substring(0, 5))
                    .header("Add-Padding", "true").retrieve().body(String.class);
            if (occurrences(body, hash.substring(5)) > 0) {
                throw ApiException.badRequest("password_breached",
                        "This password appears in a known data breach. Please choose a different one.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Breached-password check unavailable, accepting password: {}", e.getMessage());
        }
    }

    /** Parses the "SUFFIX:COUNT" lines of a range response. */
    static int occurrences(String rangeBody, String suffix) {
        if (rangeBody == null) return 0;
        for (String line : rangeBody.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                try {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    static String sha1Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
