package com.polygis.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.common.ApiException;
import com.polygis.common.Hashing;
import com.polygis.config.AppProperties;

/**
 * Short numeric codes sent by e-mail (verification, passwordless login, password reset) and opaque single-use
 * codes (OAuth2 -> SPA exchange). Codes are stored as SHA-256(userId:code); attempts are capped and resends
 * are throttled.
 */
@Service
public class OtpService {

    private final OneTimeTokenRepository repository;
    private final AppProperties.Security.Otp props;

    public OtpService(OneTimeTokenRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props.security().otp();
    }

    @Transactional
    public String issueNumeric(UUID userId, OtpPurpose purpose) {
        repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .filter(t -> t.getCreatedAt().plus(props.resendCooldown()).isAfter(Instant.now()))
                .ifPresent(t -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "otp_cooldown",
                            "Please wait before requesting another code");
                });
        repository.invalidateActive(userId, purpose, Instant.now());
        String code = Hashing.numericCode(props.length());
        save(userId, purpose, hash(userId, code), props.ttl());
        return code;
    }

    /** Validates a numeric code; failures count towards the attempt limit even if the caller's tx rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyNumeric(UUID userId, OtpPurpose purpose, String code) {
        OneTimeToken token = repository.lockLatestActive(userId, purpose)
                .filter(OneTimeToken::isActive)
                .orElseThrow(() -> ApiException.badRequest("otp_invalid", "Invalid or expired code"));
        token.setAttempts(token.getAttempts() + 1);
        if (token.getAttempts() > props.maxAttempts()) {
            token.setConsumedAt(Instant.now());
            throw ApiException.badRequest("otp_locked", "Too many attempts, request a new code");
        }
        if (code == null || !Hashing.constantTimeEquals(token.getCodeHash(), hash(userId, code.trim()))) {
            throw ApiException.badRequest("otp_invalid", "Invalid or expired code");
        }
        token.setConsumedAt(Instant.now());
    }

    /** Opaque high-entropy code used to hand tokens to the SPA after an OAuth2 redirect. */
    @Transactional
    public String issueExchangeCode(UUID userId) {
        String code = Hashing.randomToken(32);
        save(userId, OtpPurpose.OAUTH2_EXCHANGE, Hashing.sha256(code), Duration.ofSeconds(60));
        return code;
    }

    @Transactional
    public UUID consumeExchangeCode(String code) {
        OneTimeToken token = repository.lockByCodeHash(Hashing.sha256(code), OtpPurpose.OAUTH2_EXCHANGE)
                .filter(OneTimeToken::isActive)
                .orElseThrow(() -> ApiException.badRequest("code_invalid", "Invalid or expired code"));
        token.setConsumedAt(Instant.now());
        return token.getUserId();
    }

    /** One-time code a super-admin's browser carries to the tenant host to start an impersonated session. */
    @Transactional
    public String issueImpersonationCode(UUID userId, String actorEmail) {
        String code = Hashing.randomToken(32);
        OneTimeToken t = new OneTimeToken();
        t.setUserId(userId);
        t.setPurpose(OtpPurpose.IMPERSONATION);
        t.setCodeHash(Hashing.sha256(code));
        t.setData(actorEmail);
        t.setExpiresAt(Instant.now().plus(Duration.ofSeconds(60)));
        repository.save(t);
        return code;
    }

    @Transactional
    public OneTimeToken consumeImpersonationCode(String code) {
        OneTimeToken token = repository.lockByCodeHash(Hashing.sha256(code), OtpPurpose.IMPERSONATION)
                .filter(OneTimeToken::isActive)
                .orElseThrow(() -> ApiException.badRequest("code_invalid", "Invalid or expired code"));
        token.setConsumedAt(Instant.now());
        return token;
    }

    /** Opaque handle proving "password/e-mail code was correct, second factor still pending" (5 minutes). */
    @Transactional
    public String issueMfaChallenge(UUID userId) {
        String code = Hashing.randomToken(32);
        save(userId, OtpPurpose.MFA_CHALLENGE, Hashing.sha256(code), Duration.ofMinutes(5));
        return code;
    }

    /** Loads a pending challenge and counts the attempt; the caller consumes it on success. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public OneTimeToken attemptMfaChallenge(String code) {
        OneTimeToken token = repository.lockByCodeHash(Hashing.sha256(code), OtpPurpose.MFA_CHALLENGE)
                .filter(OneTimeToken::isActive)
                .orElseThrow(() -> ApiException.unauthorized("mfa_expired", "Sign-in expired, please start again"));
        token.setAttempts(token.getAttempts() + 1);
        if (token.getAttempts() > props.maxAttempts()) {
            token.setConsumedAt(Instant.now());
            throw ApiException.unauthorized("mfa_expired", "Too many attempts, please sign in again");
        }
        return token;
    }

    private void save(UUID userId, OtpPurpose purpose, String hash, Duration ttl) {
        OneTimeToken t = new OneTimeToken();
        t.setUserId(userId);
        t.setPurpose(purpose);
        t.setCodeHash(hash);
        t.setExpiresAt(Instant.now().plus(ttl));
        repository.save(t);
    }

    private static String hash(UUID userId, String code) {
        return Hashing.sha256(userId + ":" + code);
    }
}
