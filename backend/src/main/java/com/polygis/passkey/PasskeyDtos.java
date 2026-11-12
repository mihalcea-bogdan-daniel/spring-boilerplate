package com.polygis.passkey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON shapes of the WebAuthn Level 3 "toJSON" form (base64url strings), which is what
 * {@code @simplewebauthn/browser} sends and expects.
 */
public final class PasskeyDtos {

    private PasskeyDtos() {
    }

    // ------------------------------------------------------------- options sent to the browser

    public record RpEntity(String id, String name) {
    }

    public record UserEntity(String id, String name, String displayName) {
    }

    public record PubKeyCredParam(String type, int alg) {
    }

    public record CredentialDescriptor(String id, String type, List<String> transports) {
    }

    public record AuthenticatorSelection(String residentKey, boolean requireResidentKey, String userVerification) {
    }

    public record CreationOptions(RpEntity rp, UserEntity user, String challenge, List<PubKeyCredParam> pubKeyCredParams,
                                  long timeout, List<CredentialDescriptor> excludeCredentials,
                                  AuthenticatorSelection authenticatorSelection, String attestation) {
    }

    public record RequestOptions(String challenge, long timeout, String rpId, List<CredentialDescriptor> allowCredentials,
                                 String userVerification) {
    }

    // ------------------------------------------------------------- responses from the browser

    public record AttestationResponse(@NotBlank String clientDataJSON, @NotBlank String attestationObject,
                                      List<String> transports) {
    }

    public record RegistrationCredential(@NotBlank @Size(max = 1400) String id, String rawId, String type,
                                         @NotNull @Valid AttestationResponse response) {
    }

    public record AssertionResponse(@NotBlank String clientDataJSON, @NotBlank String authenticatorData,
                                    @NotBlank String signature, String userHandle) {
    }

    public record AuthenticationCredential(@NotBlank @Size(max = 1400) String id, String rawId, String type,
                                           @NotNull @Valid AssertionResponse response) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 100) String name, @NotNull @Valid RegistrationCredential credential) {
    }

    public record PasskeyDto(UUID id, String name, Instant createdAt, Instant lastUsedAt, boolean synced) {
        static PasskeyDto from(Passkey p) {
            return new PasskeyDto(p.getId(), p.getName(), p.getCreatedAt(), p.getLastUsedAt(), p.isBackupState());
        }
    }
}
