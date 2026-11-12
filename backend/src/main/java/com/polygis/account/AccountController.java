package com.polygis.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.polygis.auth.JwtClaims;
import com.polygis.auth.dto.AuthDtos;
import com.polygis.auth.dto.AuthDtos.MeResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-service endpoints for the signed-in user. Always act on the token's home tenant. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    public record UpdateProfileRequest(@Size(max = 100) String firstName, @Size(max = 100) String lastName) {
    }

    /** currentPassword is required only when the account already has a password (not for OTP/social-only users). */
    public record ChangePasswordRequest(
            String currentPassword,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX,
                    message = "must be 10+ chars with letters and digits") String newPassword) {
    }

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PutMapping
    public MeResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        return accounts.updateProfile(userId(jwt), tenant(jwt), req);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest req) {
        accounts.changePassword(userId(jwt), tenant(jwt), req);
    }

    @GetMapping("/sessions")
    public List<AccountService.SessionDto> sessions(@AuthenticationPrincipal Jwt jwt) {
        return accounts.sessions(userId(jwt), tenant(jwt));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        accounts.revokeSession(userId(jwt), tenant(jwt), id);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@AuthenticationPrincipal Jwt jwt) {
        accounts.revokeAllSessions(userId(jwt), tenant(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString(JwtClaims.TENANT);
    }
}
