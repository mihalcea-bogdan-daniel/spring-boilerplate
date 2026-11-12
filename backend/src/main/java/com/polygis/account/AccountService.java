package com.polygis.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.account.AccountController.ChangePasswordRequest;
import com.polygis.account.AccountController.UpdateProfileRequest;
import com.polygis.audit.AuditService;
import com.polygis.auth.AuthService;
import com.polygis.auth.RefreshToken;
import com.polygis.auth.RefreshTokenRepository;
import com.polygis.auth.TokenService;
import com.polygis.auth.dto.AuthDtos.MeResponse;
import com.polygis.common.ApiException;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.User;
import com.polygis.user.UserRepository;

@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AuthService auth;
    private final TenantRegistry registry;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final RefreshTokenRepository refreshTokens;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          AuthService auth, TenantRegistry registry, TransactionTemplate tx,
                          AuditService audit, RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.auth = auth;
        this.registry = registry;
        this.tx = tx;
        this.audit = audit;
        this.refreshTokens = refreshTokens;
    }

    public MeResponse updateProfile(UUID userId, String homeTenant, UpdateProfileRequest req) {
        TenantInfo home = registry.requireBySlug(homeTenant);
        return inHomeTenant(home, userId, user -> {
            user.setFirstName(req.firstName());
            user.setLastName(req.lastName());
            return auth.toMe(user, home);
        });
    }

    /** Changing the password signs the user out everywhere (all refresh tokens are revoked). */
    public void changePassword(UUID userId, String homeTenant, ChangePasswordRequest req) {
        inHomeTenant(registry.requireBySlug(homeTenant), userId, user -> {
            if (user.getPasswordHash() != null && (req.currentPassword() == null
                    || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash()))) {
                throw ApiException.badRequest("wrong_password", "Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            tokens.revokeAll(user.getId());
            audit.recordAs(user.getId(), user.getEmail(), "auth.password_changed", null);
            return null;
        });
    }

    public List<SessionDto> sessions(UUID userId, String homeTenant) {
        return TenantContext.callAs(registry.requireBySlug(homeTenant), () -> refreshTokens
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream().map(SessionDto::from).toList());
    }

    /** Revokes one session (its whole rotation family); the device is signed out at its next token refresh. */
    public void revokeSession(UUID userId, String homeTenant, UUID sessionId) {
        TenantContext.runAs(registry.requireBySlug(homeTenant), () -> tx.executeWithoutResult(status -> {
            RefreshToken token = refreshTokens.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> ApiException.notFound("Session"));
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
        }));
    }

    public void revokeAllSessions(UUID userId, String homeTenant) {
        TenantContext.runAs(registry.requireBySlug(homeTenant), () -> tokens.revokeAll(userId));
    }

    public record SessionDto(UUID id, String userAgent, String ipAddress, Instant createdAt, Instant expiresAt) {
        static SessionDto from(RefreshToken t) {
            return new SessionDto(t.getId(), t.getUserAgent(), t.getIpAddress(), t.getCreatedAt(), t.getExpiresAt());
        }
    }

    /** A super-admin may be switched into another tenant (X-Tenant-ID); their own account lives at home. */
    private <T> T inHomeTenant(TenantInfo home, UUID userId, Function<User, T> work) {
        return TenantContext.callAs(home, () -> tx.execute(status -> {
            User user = users.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized("user_gone", "User no longer exists"));
            return work.apply(user);
        }));
    }
}
