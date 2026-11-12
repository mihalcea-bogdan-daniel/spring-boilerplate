package com.polygis.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.audit.AuditService;
import com.polygis.auth.dto.AuthDtos.*;
import com.polygis.common.ApiException;
import com.polygis.config.AppProperties;
import com.polygis.mail.MailService;
import com.polygis.mfa.MfaService;
import com.polygis.settings.SettingsService;
import com.polygis.tenancy.PlanService;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.Role;
import com.polygis.user.RoleRepository;
import com.polygis.user.User;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;

/**
 * All flows operate inside the tenant bound by TenantResolutionFilter (i.e. the host). Responses avoid account
 * enumeration where it matters (register / OTP request / password reset always answer 202).
 */
@Service
public class AuthService {

    /** Pre-computed hash used to equalise timing when the e-mail does not exist. */
    private final String dummyHash;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final OtpService otp;
    private final MailService mail;
    private final SettingsService settings;
    private final TenantRegistry registry;
    private final AppProperties props;
    private final AuditService audit;
    private final BreachedPasswordChecker breached;
    private final MfaService mfa;
    private final PlanService plans;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       TokenService tokens, OtpService otp, MailService mail, SettingsService settings,
                       TenantRegistry registry, AppProperties props, AuditService audit,
                       BreachedPasswordChecker breached, MfaService mfa, PlanService plans) {
        this.plans = plans;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.otp = otp;
        this.mail = mail;
        this.settings = settings;
        this.registry = registry;
        this.props = props;
        this.audit = audit;
        this.breached = breached;
        this.mfa = mfa;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    /** Either a full session (body + refreshToken) or a pending second-factor challenge (mfaToken). */
    public record AuthResult(TokenResponse body, String refreshToken, String mfaToken) {
        public AuthResult(TokenResponse body, String refreshToken) {
            this(body, refreshToken, null);
        }
    }

    // ---------------------------------------------------------------- registration + e-mail verification

    @Transactional
    public void register(RegisterRequest req) {
        if (!settings.getBoolean(SettingsService.ALLOW_SELF_REGISTRATION, true)) {
            throw ApiException.forbidden("registration_disabled", "Self-registration is disabled for this tenant");
        }
        Optional<User> existing = users.findByEmailIgnoreCase(req.email());
        if (existing.isPresent()) {
            // Silently re-send verification to unverified accounts; never reveal that an account exists.
            if (!existing.get().isEmailVerified()) sendOtpQuietly(existing.get(), OtpPurpose.EMAIL_VERIFICATION);
            return;
        }
        breached.assertNotBreached(req.password());
        plans.assertCanAddUser(users.count());
        User user = new User();
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setStatus(UserStatus.PENDING);
        roles.findByName(Role.USER).ifPresent(r -> user.getRoles().add(r));
        users.save(user);
        sendOtp(user, OtpPurpose.EMAIL_VERIFICATION);
    }

    @Transactional
    public AuthResult verifyEmail(VerifyOtpRequest req, TokenService.ClientInfo client) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> ApiException.badRequest("otp_invalid", "Invalid or expired code"));
        otp.verifyNumeric(user.getId(), OtpPurpose.EMAIL_VERIFICATION, req.code());
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING) user.setStatus(UserStatus.ACTIVE);
        mail.sendWelcome(user.getEmail(), user.displayName(), currentHost());
        return issue(user, client);
    }

    @Transactional
    public void resendVerification(EmailRequest req) {
        users.findByEmailIgnoreCase(req.email()).filter(u -> !u.isEmailVerified())
                .ifPresent(u -> sendOtp(u, OtpPurpose.EMAIL_VERIFICATION));
    }

    // ---------------------------------------------------------------- password login

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest req, TokenService.ClientInfo client) {
        Optional<User> found = users.findByEmailIgnoreCase(req.email());
        if (found.isEmpty() || found.get().getPasswordHash() == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw badCredentials();
        }
        User user = found.get();
        if (user.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "account_locked", "Account temporarily locked");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            registerFailure(user);
            audit.recordAs(user.getId(), user.getEmail(), "auth.login_failed", user.isLocked() ? "account locked" : null);
            throw badCredentials();
        }
        if (!user.isEmailVerified()) {
            sendOtpQuietly(user, OtpPurpose.EMAIL_VERIFICATION);
            throw ApiException.forbidden("email_not_verified", "E-mail address not verified");
        }
        ensureActive(user);
        return completeFirstFactor(user, client, "password");
    }

    // ---------------------------------------------------------------- passwordless (e-mail OTP) login

    @Transactional
    public void requestLoginOtp(EmailRequest req) {
        users.findByEmailIgnoreCase(req.email())
                .filter(u -> u.getStatus() != UserStatus.DISABLED)
                .ifPresent(u -> sendOtpQuietly(u, OtpPurpose.LOGIN));
    }

    @Transactional
    public AuthResult verifyLoginOtp(VerifyOtpRequest req, TokenService.ClientInfo client) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> ApiException.badRequest("otp_invalid", "Invalid or expired code"));
        otp.verifyNumeric(user.getId(), OtpPurpose.LOGIN, req.code());
        user.verifyEmailAsOwner(); // proving inbox ownership verifies the address (and drops unproven credentials)
        if (user.getStatus() == UserStatus.PENDING) user.setStatus(UserStatus.ACTIVE);
        ensureActive(user);
        return completeFirstFactor(user, client, "email-code");
    }

    // ---------------------------------------------------------------- second factor (TOTP)

    /** First factor passed: sign in, or hand out a challenge if the account has TOTP enabled. */
    private AuthResult completeFirstFactor(User user, TokenService.ClientInfo client, String method) {
        if (user.isTotpEnabled()) {
            return new AuthResult(null, null, otp.issueMfaChallenge(user.getId()));
        }
        audit.recordAs(user.getId(), user.getEmail(), "auth.login", method);
        return issue(user, client);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult verifyMfa(MfaVerifyRequest req, TokenService.ClientInfo client) {
        OneTimeToken challenge = otp.attemptMfaChallenge(req.mfaToken());
        User user = users.findById(challenge.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("mfa_expired", "Sign-in expired, please start again"));
        if (!mfa.verify(user, req.code())) {
            audit.recordAs(user.getId(), user.getEmail(), "auth.mfa_failed", null);
            throw ApiException.badRequest("mfa_invalid", "Invalid authentication code");
        }
        challenge.setConsumedAt(Instant.now());
        ensureActive(user);
        audit.recordAs(user.getId(), user.getEmail(), "auth.login", "mfa");
        return issue(user, client);
    }

    // ---------------------------------------------------------------- password reset

    @Transactional
    public void requestPasswordReset(EmailRequest req) {
        users.findByEmailIgnoreCase(req.email())
                .filter(u -> u.getStatus() != UserStatus.DISABLED)
                .ifPresent(u -> sendOtpQuietly(u, OtpPurpose.PASSWORD_RESET));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> ApiException.badRequest("otp_invalid", "Invalid or expired code"));
        breached.assertNotBreached(req.newPassword());
        otp.verifyNumeric(user.getId(), OtpPurpose.PASSWORD_RESET, req.code());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        tokens.revokeAll(user.getId()); // sign out everywhere
        audit.recordAs(user.getId(), user.getEmail(), "auth.password_reset", null);
    }

    // ---------------------------------------------------------------- tokens

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String refreshToken, TokenService.ClientInfo client) {
        if (refreshToken == null) throw ApiException.unauthorized("refresh_missing", "No refresh token");
        TokenService.RotatedToken rotated = tokens.rotate(refreshToken, client);
        User user = users.findById(rotated.userId())
                .orElseThrow(() -> ApiException.unauthorized("refresh_invalid", "Invalid refresh token"));
        ensureActive(user);
        TenantInfo tenant = TenantContext.require();
        return new AuthResult(new TokenResponse(tokens.accessToken(user, tenant),
                props.security().jwt().accessTokenTtl().toSeconds(), toMe(user, tenant)), rotated.refreshToken());
    }

    @Transactional
    public AuthResult exchangeOAuth2Code(String code, TokenService.ClientInfo client) {
        UUID userId = otp.consumeExchangeCode(code);
        User user = users.findById(userId).orElseThrow(() -> ApiException.badRequest("code_invalid", "Invalid code"));
        ensureActive(user);
        // same second-factor rules as password and e-mail-code sign-in
        return completeFirstFactor(user, client, "oauth2");
    }

    /**
     * Starts an impersonated session from the one-time code issued by the platform console. The session is an
     * access token only (30 minutes, {@code act} claim, no refresh token) and every use is audited.
     */
    @Transactional
    public TokenResponse exchangeImpersonationCode(String code) {
        OneTimeToken token = otp.consumeImpersonationCode(code);
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("code_invalid", "Invalid code"));
        TenantInfo tenant = TenantContext.require();
        return new TokenResponse(tokens.impersonationToken(user, tenant, token.getData()),
                TokenService.IMPERSONATION_TTL.toSeconds(), toMe(user, tenant).withImpersonator(token.getData()));
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) tokens.revoke(refreshToken);
    }

    /**
     * Profile of the token owner. Loaded from the token's own tenant: a super-admin may currently be switched
     * into another tenant via X-Tenant-ID, but their account lives in the platform schema.
     */
    public MeResponse me(UUID userId, String tokenTenant, String impersonator) {
        TenantInfo home = registry.requireBySlug(tokenTenant);
        return TenantContext.callAs(home, () -> users.findById(userId)
                .map(u -> toMe(u, home).withImpersonator(impersonator))
                .orElseThrow(() -> ApiException.unauthorized("user_gone", "User no longer exists")));
    }

    /** Used by the OAuth2 success handler and by verification flows. */
    @Transactional
    public AuthResult issue(User user, TokenService.ClientInfo client) {
        TenantInfo tenant = TenantContext.require();
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        TokenService.IssuedTokens t = tokens.issue(user, tenant, client);
        return new AuthResult(new TokenResponse(t.accessToken(), t.expiresIn(), toMe(user, tenant)), t.refreshToken());
    }

    public MeResponse toMe(User u, TenantInfo tenant) {
        boolean superAdmin = props.tenancy().platformTenant().equals(tenant.slug())
                && u.roleNames().contains(Role.SUPER_ADMIN);
        return new MeResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), tenant.slug(),
                u.roleNames(), u.permissionCodes(), superAdmin, u.getPasswordHash() != null,
                u.isTotpEnabled(), null);
    }

    // ---------------------------------------------------------------- helpers

    private void registerFailure(User user) {
        var pw = props.security().password();
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= pw.maxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plus(pw.lockoutDuration()));
            user.setFailedLoginCount(0);
        }
        users.save(user);
    }

    private void ensureActive(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden("account_disabled", "Account disabled");
        }
    }

    private void sendOtp(User user, OtpPurpose purpose) {
        String code = otp.issueNumeric(user.getId(), purpose);
        mail.sendOtp(user.getEmail(), user.displayName(), code, purpose, currentHost());
    }

    private void sendOtpQuietly(User user, OtpPurpose purpose) {
        try {
            sendOtp(user, purpose);
        } catch (ApiException e) {
            if (!"otp_cooldown".equals(e.getCode())) throw e;
        }
    }

    private String currentHost() {
        return TenantContext.require().host(props.tenancy().baseDomain());
    }

    private static ApiException badCredentials() {
        return ApiException.unauthorized("bad_credentials", "Invalid e-mail or password");
    }
}
