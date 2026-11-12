package com.polygis.config;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.polygis.auth.JwtClaims;

import lombok.extern.slf4j.Slf4j;

/**
 * Access-token signing.
 * <ul>
 *   <li><b>HS256</b> (default): one shared secret ({@code JWT_SECRET}). Simplest when only this app verifies.</li>
 *   <li><b>RS256</b>: private key signs here, anyone verifies with the public key published at
 *       {@code /api/auth/jwks.json}. Use it when gateways or other services validate tokens.
 *       Key: {@code JWT_PRIVATE_KEY} = PKCS#8 PEM ({@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048}).</li>
 * </ul>
 */
@Slf4j
@Configuration
public class JwtConfig {

    /** What TokenService needs to build the JWS header. */
    public record Signing(JwsAlgorithm algorithm, String keyId) {
    }

    private final AppProperties.Security.Jwt props;
    private final RSAKey rsaKey; // null in HS256 mode

    public JwtConfig(AppProperties appProperties) {
        this.props = appProperties.security().jwt();
        this.rsaKey = "RS256".equalsIgnoreCase(props.algorithm()) ? loadRsaKey(props.privateKey()) : null;
    }

    @Bean
    Signing jwtSigning() {
        return rsaKey != null ? new Signing(SignatureAlgorithm.RS256, rsaKey.getKeyID())
                : new Signing(MacAlgorithm.HS256, null);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return rsaKey != null ? new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)))
                : new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;
        try {
            decoder = rsaKey != null
                    ? NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build()
                    : NimbusJwtDecoder.withSecretKey(hmacKey()).macAlgorithm(MacAlgorithm.HS256).build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
        OAuth2TokenValidator<Jwt> accessOnly = jwt -> JwtClaims.ACCESS.equals(jwt.getClaimAsString(JwtClaims.TOKEN_TYPE))
                && jwt.getClaimAsString(JwtClaims.TENANT) != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an access token", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()), accessOnly));
        return decoder;
    }

    /** Public keys for external verifiers; empty in HS256 mode (a shared secret must never be published). */
    public Map<String, Object> jwks() {
        return rsaKey != null ? new JWKSet(rsaKey.toPublicJWK()).toJSONObject() : new JWKSet().toJSONObject();
    }

    private SecretKey hmacKey() {
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static RSAKey loadRsaKey(String pem) {
        try {
            RSAPrivateCrtKey privateKey;
            if (StringUtils.hasText(pem)) {
                String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            } else {
                log.warn("RS256 selected but no app.security.jwt.private-key set: using an EPHEMERAL key. "
                        + "Tokens die on restart and other nodes cannot verify them - set JWT_PRIVATE_KEY.");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                privateKey = (RSAPrivateCrtKey) pair.getPrivate();
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint().build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | com.nimbusds.jose.JOSEException
                 | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Invalid app.security.jwt.private-key: expected an RSA PKCS#8 PEM", e);
        }
    }
}
