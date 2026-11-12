package com.polygis.passkey;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.polygis.auth.OneTimeToken;
import com.polygis.auth.OneTimeTokenRepository;
import com.polygis.auth.OtpPurpose;
import com.polygis.common.ApiException;
import com.polygis.common.Hashing;
import com.polygis.passkey.PasskeyDtos.*;
import com.polygis.user.User;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.CollectedClientDataConverter;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;

/**
 * Passkeys (WebAuthn) without server sessions: each challenge is a one-time token (hash of the challenge,
 * 5 minutes) found again through the challenge echoed in clientDataJSON. The relying party is the tenant host,
 * so a passkey only ever works on the workspace it was created on.
 * <p>Sign-in requires user verification (biometric or device PIN), so a passkey counts as both factors.
 */
@Service
public class PasskeyService {

    /** Who and where a ceremony runs: rpId = tenant host, origin = what the browser reports for that host. */
    public record RelyingParty(String id, String name, String origin) {
    }

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final long TIMEOUT_MS = CHALLENGE_TTL.toMillis();
    private static final List<PubKeyCredParam> ALGORITHMS = List.of(
            new PubKeyCredParam("public-key", -7),    // ES256
            new PubKeyCredParam("public-key", -8),    // EdDSA
            new PubKeyCredParam("public-key", -257)); // RS256
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasskeyRepository passkeys;
    private final OneTimeTokenRepository challenges;
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final WebAuthnManager webAuthn = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
    private final AttestedCredentialDataConverter credentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
    private final CollectedClientDataConverter clientDataConverter = new CollectedClientDataConverter(objectConverter);

    public PasskeyService(PasskeyRepository passkeys, OneTimeTokenRepository challenges) {
        this.passkeys = passkeys;
        this.challenges = challenges;
    }

    // ------------------------------------------------------------------ registration (signed-in user)

    @Transactional
    public CreationOptions registrationOptions(User user, RelyingParty rp) {
        String challenge = newChallenge(user.getId(), OtpPurpose.PASSKEY_REGISTRATION);
        List<CredentialDescriptor> exclude = passkeys.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(p -> new CredentialDescriptor(p.getCredentialId(), "public-key", transports(p))).toList();
        return new CreationOptions(new RpEntity(rp.id(), rp.name()),
                new UserEntity(B64.encodeToString(userHandle(user.getId())), user.getEmail(), user.displayName()),
                challenge, ALGORITHMS, TIMEOUT_MS, exclude,
                new AuthenticatorSelection("required", true, "required"), "none");
    }

    @Transactional
    public Passkey register(User user, RegisterRequest req, RelyingParty rp) {
        var response = req.credential().response();
        byte[] clientData = decode(response.clientDataJSON());
        DefaultChallenge challenge = consumeChallenge(clientData, OtpPurpose.PASSKEY_REGISTRATION, user.getId());
        try {
            var data = webAuthn.verify(
                    new RegistrationRequest(decode(response.attestationObject()), clientData,
                            response.transports() == null ? null : new HashSet<>(response.transports())),
                    new RegistrationParameters(serverProperty(rp, challenge), parameters(), true, true));
            var authData = data.getAttestationObject().getAuthenticatorData();
            var attested = authData.getAttestedCredentialData();
            String credentialId = B64.encodeToString(attested.getCredentialId());
            if (passkeys.findByCredentialId(credentialId).isPresent()) {
                throw ApiException.conflict("passkey_exists", "This passkey is already registered");
            }
            Passkey p = new Passkey();
            p.setUserId(user.getId());
            p.setCredentialId(credentialId);
            p.setCredentialData(credentialDataConverter.convert(attested));
            p.setSignCount(authData.getSignCount());
            p.setUvInitialized(authData.isFlagUV());
            p.setBackupEligible(authData.isFlagBE());
            p.setBackupState(authData.isFlagBS());
            p.setTransports(response.transports() == null ? null : String.join(",", response.transports()));
            p.setName(req.name().trim());
            return passkeys.save(p);
        } catch (VerificationException | DataConversionException e) {
            throw ApiException.badRequest("passkey_invalid", "The passkey could not be verified: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PasskeyDto> list(UUID userId) {
        return passkeys.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PasskeyDto::from).toList();
    }

    @Transactional
    public Passkey delete(UUID userId, UUID passkeyId) {
        Passkey p = passkeys.findByIdAndUserId(passkeyId, userId).orElseThrow(() -> ApiException.notFound("Passkey"));
        passkeys.delete(p);
        return p;
    }

    // ------------------------------------------------------------------ sign-in (anonymous)

    /** Discoverable credentials: no user is named up front, the authenticator offers what it holds for this host. */
    @Transactional
    public RequestOptions authenticationOptions(RelyingParty rp) {
        return new RequestOptions(newChallenge(null, OtpPurpose.PASSKEY_LOGIN), TIMEOUT_MS, rp.id(), List.of(), "required");
    }

    /** @return the id of the user who proved possession of the passkey */
    @Transactional(noRollbackFor = ApiException.class)
    public UUID authenticate(AuthenticationCredential credential, RelyingParty rp) {
        var response = credential.response();
        byte[] clientData = decode(response.clientDataJSON());
        DefaultChallenge challenge = consumeChallenge(clientData, OtpPurpose.PASSKEY_LOGIN, null);
        Passkey passkey = passkeys.findByCredentialId(credential.id())
                .orElseThrow(() -> ApiException.unauthorized("passkey_unknown", "This passkey is not registered here"));
        byte[] userHandle = StringUtils.hasText(response.userHandle()) ? decode(response.userHandle()) : null;
        if (userHandle != null && !Arrays.equals(userHandle, userHandle(passkey.getUserId()))) {
            throw ApiException.unauthorized("passkey_invalid", "Passkey does not belong to this account");
        }
        byte[] credentialId = decode(credential.id());
        var record = new CredentialRecordImpl(new NoneAttestationStatement(), passkey.isUvInitialized(),
                passkey.isBackupEligible(), passkey.isBackupState(), passkey.getSignCount(),
                credentialDataConverter.convert(passkey.getCredentialData()), null, null, null, null);
        try {
            var data = webAuthn.verify(
                    new AuthenticationRequest(credentialId, userHandle, decode(response.authenticatorData()), clientData,
                            null, decode(response.signature())),
                    new AuthenticationParameters(serverProperty(rp, challenge), record, List.of(credentialId), true, true));
            passkey.setSignCount(data.getAuthenticatorData().getSignCount());
            passkey.setBackupState(data.getAuthenticatorData().isFlagBS());
            passkey.setUvInitialized(true);
            passkey.setLastUsedAt(Instant.now());
            return passkey.getUserId();
        } catch (VerificationException | DataConversionException e) {
            throw ApiException.unauthorized("passkey_invalid", "The passkey could not be verified");
        }
    }

    @Transactional
    public void deleteAllFor(UUID userId) {
        passkeys.deleteByUserId(userId);
    }

    // ------------------------------------------------------------------ helpers

    private String newChallenge(UUID userId, OtpPurpose purpose) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String challenge = B64.encodeToString(raw);
        OneTimeToken t = new OneTimeToken();
        t.setUserId(userId);
        t.setPurpose(purpose);
        t.setCodeHash(Hashing.sha256(challenge));
        t.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));
        challenges.save(t);
        return challenge;
    }

    /** Finds the challenge echoed by the browser, checks purpose/owner/expiry and burns it. */
    private DefaultChallenge consumeChallenge(byte[] clientDataJSON, OtpPurpose purpose, UUID expectedUser) {
        byte[] challenge;
        try {
            challenge = clientDataConverter.convert(clientDataJSON).getChallenge().getValue();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("passkey_invalid", "Malformed client data");
        }
        OneTimeToken token = challenges.lockByCodeHash(Hashing.sha256(B64.encodeToString(challenge)), purpose)
                .filter(OneTimeToken::isActive)
                .filter(t -> expectedUser == null || expectedUser.equals(t.getUserId()))
                .orElseThrow(() -> ApiException.badRequest("passkey_challenge", "The passkey request expired, try again"));
        token.setConsumedAt(Instant.now());
        return new DefaultChallenge(challenge);
    }

    private static ServerProperty serverProperty(RelyingParty rp, DefaultChallenge challenge) {
        return new ServerProperty(new Origin(rp.origin()), rp.id(), challenge);
    }

    private static List<PublicKeyCredentialParameters> parameters() {
        return List.of(
                new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
                new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.EdDSA),
                new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256));
    }

    /** WebAuthn user handle: the 16 bytes of the user id (no personal data). */
    static byte[] userHandle(UUID userId) {
        return ByteBuffer.allocate(16).putLong(userId.getMostSignificantBits()).putLong(userId.getLeastSignificantBits()).array();
    }

    private static List<String> transports(Passkey p) {
        return p.getTransports() == null ? List.of() : List.of(p.getTransports().split(","));
    }


    private static byte[] decode(String base64url) {
        try {
            return B64D.decode(base64url);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("passkey_invalid", "Malformed passkey response");
        }
    }
}
