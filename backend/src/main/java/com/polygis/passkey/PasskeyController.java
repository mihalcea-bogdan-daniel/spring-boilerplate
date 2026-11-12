package com.polygis.passkey;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import com.polygis.audit.AuditService;
import com.polygis.auth.AuthCookies;
import com.polygis.auth.AuthService;
import com.polygis.auth.JwtClaims;
import com.polygis.auth.TokenService;
import com.polygis.auth.dto.AuthDtos.TokenResponse;
import com.polygis.common.ApiException;
import com.polygis.config.AppProperties;
import com.polygis.passkey.PasskeyDtos.*;
import com.polygis.settings.SettingsService;
import com.polygis.tenancy.TenantContext;
import com.polygis.user.User;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
public class PasskeyController {

    private final PasskeyService passkeys;
    private final UserRepository users;
    private final AuthService auth;
    private final AuthCookies cookies;
    private final AuditService audit;
    private final SettingsService settings;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public PasskeyController(PasskeyService passkeys, UserRepository users, AuthService auth, AuthCookies cookies,
                             AuditService audit, SettingsService settings, AppProperties props, TransactionTemplate tx) {
        this.passkeys = passkeys;
        this.users = users;
        this.auth = auth;
        this.cookies = cookies;
        this.audit = audit;
        this.settings = settings;
        this.props = props;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ manage my passkeys

    @PostMapping("/api/me/passkeys/options")
    public CreationOptions registrationOptions(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        User user = homeUser(jwt);
        return tx.execute(s -> passkeys.registrationOptions(user, rp(request)));
    }

    @PostMapping("/api/me/passkeys")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyDto register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest req,
                               HttpServletRequest request) {
        User user = homeUser(jwt);
        return tx.execute(s -> {
            Passkey p = passkeys.register(user, req, rp(request));
            audit.recordAs(user.getId(), user.getEmail(), "auth.passkey_added", p.getName());
            return PasskeyDto.from(p);
        });
    }

    @GetMapping("/api/me/passkeys")
    public List<PasskeyDto> list(@AuthenticationPrincipal Jwt jwt) {
        return passkeys.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/api/me/passkeys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        User user = homeUser(jwt);
        tx.executeWithoutResult(s -> {
            Passkey p = passkeys.delete(user.getId(), id);
            audit.recordAs(user.getId(), user.getEmail(), "auth.passkey_removed", p.getName());
        });
    }

    // ------------------------------------------------------------------ sign in with a passkey

    @PostMapping("/api/auth/passkey/options")
    public RequestOptions authenticationOptions(HttpServletRequest request) {
        return passkeys.authenticationOptions(rp(request));
    }

    /** A verified passkey is possession + biometric/PIN: no TOTP challenge follows. */
    @PostMapping("/api/auth/passkey/verify")
    public TokenResponse verify(@Valid @RequestBody AuthenticationCredential credential, HttpServletRequest request,
                                HttpServletResponse response) {
        var result = tx.execute(s -> {
            UUID userId = passkeys.authenticate(credential, rp(request));
            User user = users.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized("passkey_unknown", "Account not found"));
            if (user.getStatus() == UserStatus.DISABLED) {
                throw ApiException.forbidden("account_disabled", "Account disabled");
            }
            audit.recordAs(user.getId(), user.getEmail(), "auth.login", "passkey");
            return auth.issue(user, new TokenService.ClientInfo(request.getHeader("User-Agent"), request.getRemoteAddr()));
        });
        cookies.setRefreshToken(response, result.refreshToken());
        return result.body();
    }

    // ------------------------------------------------------------------ helpers

    /** Passkeys belong to the signed-in user on their own host; operators acting as someone cannot add any. */
    private User homeUser(Jwt jwt) {
        if (jwt.getClaimAsString(JwtClaims.ACTOR) != null) {
            throw ApiException.forbidden("impersonated", "Not allowed in an impersonated session");
        }
        if (!TenantContext.require().slug().equals(jwt.getClaimAsString(JwtClaims.TENANT))) {
            throw ApiException.forbidden("tenant_mismatch", "Manage passkeys on your own workspace");
        }
        return users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> ApiException.unauthorized("user_gone", "User no longer exists"));
    }

    private PasskeyService.RelyingParty rp(HttpServletRequest request) {
        String name = settings.all().getOrDefault("general.displayName", null);
        return RelyingParties.of(request, name == null || name.isBlank() ? props.mail().productName() : name);
    }
}
