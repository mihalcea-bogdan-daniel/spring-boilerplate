package com.polygis.auth.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request / response payloads of {@code /api/auth}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** min 10 chars, at least one letter and one digit. Tune to your policy. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{10,128}$";

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = "must be 10+ chars with letters and digits")
            String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName) {
    }

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record VerifyOtpRequest(@NotBlank @Email String email,
                                   @NotBlank @Pattern(regexp = "^\\d{4,10}$") String code) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{4,10}$") String code,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = "must be 10+ chars with letters and digits")
            String newPassword) {
    }

    public record ExchangeRequest(@NotBlank @Size(max = 100) String code) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {
        public TokenResponse(String accessToken, long expiresIn, MeResponse user) {
            this(accessToken, "Bearer", expiresIn, user);
        }
    }

    public record MeResponse(UUID id, String email, String firstName, String lastName, String tenant,
                             Set<String> roles, Set<String> permissions, boolean superAdmin,
                             boolean hasPassword, boolean mfaEnabled) {
    }

    /** Returned instead of tokens when the account has a second factor. */
    public record MfaChallengeResponse(boolean mfaRequired, String mfaToken) {
    }

    public record MfaVerifyRequest(@NotBlank @Size(max = 100) String mfaToken,
                                   @NotBlank @Size(max = 20) String code) {
    }

    public record MessageResponse(String message) {
    }
}
