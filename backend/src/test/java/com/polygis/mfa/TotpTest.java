package com.polygis.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TotpTest {

    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // RFC 6238 appendix B (SHA-1), last 6 digits of the 8-digit values
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void base32RoundTrip() {
        String encoded = Totp.base32Encode(RFC_KEY);
        assertThat(encoded).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decode(encoded)).isEqualTo(RFC_KEY);
    }

    @Test
    void toleratesOneStepOfDriftAndRejectsReplay() {
        String secret = Totp.base32Encode(RFC_KEY);
        long now = 1000;
        String previous = Totp.code(RFC_KEY, now - 1);
        assertThat(Totp.verify(secret, previous, now, 1, -1)).isEqualTo(now - 1);
        assertThat(Totp.verify(secret, previous, now, 1, now - 1)).isEqualTo(-1); // already used
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, now - 2), now, 1, -1)).isEqualTo(-1); // too old
        assertThat(Totp.verify(secret, "abc", now, 1, -1)).isEqualTo(-1);
    }
}
