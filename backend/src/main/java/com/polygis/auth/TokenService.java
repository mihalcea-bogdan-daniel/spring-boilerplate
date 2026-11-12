package com.polygis.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.common.ApiException;
import com.polygis.common.Hashing;
import com.polygis.config.AppProperties;
import com.polygis.tenancy.TenantInfo;
import com.polygis.user.User;

import lombok.extern.slf4j.Slf4j;

/**
 * Access tokens: short-lived HS256 JWTs carrying tenant, roles and permissions (stateless authorization).
 * Refresh tokens: opaque, hashed in the tenant schema, rotated on every use with reuse detection.
 */
@Slf4j
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties.Security.Jwt props;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, AppProperties props) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.props = props.security().jwt();
    }

    public static final java.time.Duration IMPERSONATION_TTL = java.time.Duration.ofMinutes(30);

    public record IssuedTokens(String accessToken, long expiresIn, String refreshToken) {
    }

    public record ClientInfo(String userAgent, String ip) {
    }

    @Transactional
    public IssuedTokens issue(User user, TenantInfo tenant, ClientInfo client) {
        return new IssuedTokens(accessToken(user, tenant), props.accessTokenTtl().toSeconds(),
                newRefreshToken(user.getId(), UUID.randomUUID(), client).raw);
    }

    public String accessToken(User user, TenantInfo tenant) {
        return accessToken(user, tenant, null, props.accessTokenTtl());
    }

    /** Access token for a super-admin acting as {@code user}: carries {@code act} and has no refresh token. */
    public String impersonationToken(User user, TenantInfo tenant, String actorEmail) {
        return accessToken(user, tenant, actorEmail, IMPERSONATION_TTL);
    }

    private String accessToken(User user, TenantInfo tenant, String actorEmail, java.time.Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS)
                .claim(JwtClaims.TENANT, tenant.slug())
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.NAME, user.displayName())
                .claim(JwtClaims.ROLES, List.copyOf(user.roleNames()))
                .claim(JwtClaims.PERMISSIONS, List.copyOf(user.permissionCodes()));
        if (actorEmail != null) builder.claim(JwtClaims.ACTOR, actorEmail);
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Rotates a refresh token. Presenting an already-rotated token revokes the whole family (theft signal). */
    @Transactional(noRollbackFor = ApiException.class)
    public RotatedToken rotate(String rawRefreshToken, ClientInfo client) {
        RefreshToken current = refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("refresh_invalid", "Invalid refresh token"));
        if (current.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for user {} - revoking family {}", current.getUserId(),
                    current.getFamilyId());
            refreshTokens.revokeFamily(current.getFamilyId(), Instant.now());
            throw ApiException.unauthorized("refresh_reused", "Refresh token reuse detected");
        }
        if (!current.isUsable()) {
            throw ApiException.unauthorized("refresh_expired", "Refresh token expired");
        }
        NewRefresh next = newRefreshToken(current.getUserId(), current.getFamilyId(), client);
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(next.id);
        return new RotatedToken(current.getUserId(), next.raw);
    }

    public record RotatedToken(UUID userId, String refreshToken) {
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    private record NewRefresh(UUID id, String raw) {
    }

    private NewRefresh newRefreshToken(UUID userId, UUID familyId, ClientInfo client) {
        String raw = Hashing.randomToken(48);
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setFamilyId(familyId);
        t.setTokenHash(Hashing.sha256(raw));
        t.setExpiresAt(Instant.now().plus(props.refreshTokenTtl()));
        if (client != null) {
            t.setUserAgent(client.userAgent() == null ? null
                    : client.userAgent().substring(0, Math.min(255, client.userAgent().length())));
            t.setIpAddress(client.ip());
        }
        refreshTokens.save(t);
        return new NewRefresh(t.getId(), raw);
    }
}
