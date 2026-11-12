package com.polygis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789";

    @Test
    void rs256TokensVerifyWithThePublishedKeyOnly() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        JwtConfig rsa = config("RS256", pem);
        String token = sign(rsa);

        assertThat(rsa.jwtDecoder().decode(token).getClaimAsString("tenant")).isEqualTo("acme");
        assertThat(rsa.jwtSigning().keyId()).isNotNull();

        @SuppressWarnings("unchecked")
        var keys = (List<Map<String, Object>>) rsa.jwks().get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0)).containsKeys("n", "e", "kid").doesNotContainKeys("d", "p", "q"); // public part only

        // a different key pair (or an HS256 verifier) must reject it
        assertThatThrownBy(() -> config("RS256", "").jwtDecoder().decode(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> config("HS256", null).jwtDecoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void hs256PublishesNoKeysAndRequiresALongSecret() {
        JwtConfig hmac = config("HS256", null);
        assertThat(hmac.jwtDecoder().decode(sign(hmac)).getSubject()).isEqualTo("user-1");
        assertThat((List<?>) hmac.jwks().get("keys")).isEmpty();

        var weak = new AppProperties(null, new AppProperties.Security(
                new AppProperties.Security.Jwt("test", "short", "HS256", null, Duration.ofMinutes(15), Duration.ofDays(1)),
                null, null, null, null), null, null, null);
        assertThatThrownBy(() -> new JwtConfig(weak).jwtEncoder()).isInstanceOf(IllegalStateException.class);
    }

    private static JwtConfig config(String algorithm, String privateKey) {
        return new JwtConfig(new AppProperties(null, new AppProperties.Security(
                new AppProperties.Security.Jwt("test", SECRET, algorithm, privateKey, Duration.ofMinutes(15),
                        Duration.ofDays(1)), null, null, null, null), null, null, null));
    }

    private static String sign(JwtConfig config) {
        var signing = config.jwtSigning();
        JwsHeader.Builder header = JwsHeader.with(signing.algorithm());
        if (signing.keyId() != null) header.keyId(signing.keyId());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("test").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("typ", "access").claim("tenant", "acme").build();
        return config.jwtEncoder().encode(JwtEncoderParameters.from(header.build(), claims)).getTokenValue();
    }
}
