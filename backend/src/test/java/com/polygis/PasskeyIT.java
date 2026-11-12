package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.polygis.support.IntegrationTestBase;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;

/** Full ceremonies against a software authenticator, encoded exactly like a browser's toJSON() output. */
class PasskeyIT extends IntegrationTestBase {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    @Test
    void registerThenSignInWithAPasskeyAndNoPassword() throws Exception {
        String admin = provisionTenant("pk-one");
        String host = hostOf("pk-one");
        String origin = "https://" + host;
        String token = accessToken(otpLogin(host, admin));
        ClientPlatform device = new ClientPlatform(new Origin(origin),
                new WebAuthnAuthenticatorAdaptor(new NoneAttestationAuthenticator()));

        // --- registration
        JsonNode options = body(mvc.perform(browser(jsonPost("/api/me/passkeys/options", Map.of()), host, origin)
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn());
        assertThat(options.at("/rp/id").asText()).isEqualTo(host);
        assertThat(options.at("/authenticatorSelection/userVerification").asText()).isEqualTo("required");

        var created = device.create(creationOptions(options));
        String credentialId = B64.encodeToString(created.getRawId());
        var registration = Map.of("name", "Laptop", "credential", Map.of(
                "id", credentialId, "rawId", credentialId, "type", "public-key",
                "response", Map.of(
                        "clientDataJSON", B64.encodeToString(created.getResponse().getClientDataJSON()),
                        "attestationObject", B64.encodeToString(created.getResponse().getAttestationObject()),
                        "transports", List.of("internal"))));
        mvc.perform(browser(jsonPost("/api/me/passkeys", registration), host, origin).header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Laptop"));
        // the same challenge cannot be used twice
        mvc.perform(browser(jsonPost("/api/me/passkeys", registration), host, origin).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("passkey_challenge"));

        // --- sign in
        var assertion = signIn(device, host, origin, credentialId);
        var session = mvc.perform(browser(jsonPost("/api/auth/passkey/verify", assertion), host, origin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(admin)).andReturn();
        assertThat(session.getResponse().getCookie("refresh_token")).isNotNull();

        // replaying the same signed assertion fails (challenge consumed)
        mvc.perform(browser(jsonPost("/api/auth/passkey/verify", assertion), host, origin))
                .andExpect(status().isBadRequest());

        // --- wrong host: the device will not even use a pk-one passkey for pk-two (rpId scoping) ...
        provisionTenant("pk-two");
        String otherHost = hostOf("pk-two");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> signIn(device, otherHost, "https://" + otherHost, credentialId))
                .isInstanceOf(com.webauthn4j.test.authenticator.webauthn.exception.NotAllowedException.class);
        // ... and a valid pk-one assertion relayed to pk-two is rejected by the server
        mvc.perform(browser(jsonPost("/api/auth/passkey/verify", signIn(device, host, origin, credentialId)),
                        otherHost, "https://" + otherHost))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("passkey_challenge"));
        // a foreign Origin is stopped by CORS; one CORS allows but that is not this host by the passkey check
        mvc.perform(browser(jsonPost("/api/auth/passkey/options", Map.of()), host, "https://evil.example.net"))
                .andExpect(status().isForbidden());
        mvc.perform(browser(jsonPost("/api/auth/passkey/options", Map.of()), host, "https://" + otherHost))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("origin_mismatch"));

        // --- removal
        String passkeyId = body(mvc.perform(get("/api/me/passkeys").with(host(host)).header("Authorization", bearer(token)))
                .andReturn()).get(0).get("id").asText();
        mvc.perform(delete("/api/me/passkeys/" + passkeyId).with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(browser(jsonPost("/api/auth/passkey/verify", signIn(device, host, origin, credentialId)), host, origin))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("passkey_unknown"));
    }

    private Map<String, Object> signIn(ClientPlatform device, String host, String origin, String credentialId)
            throws Exception {
        JsonNode options = body(mvc.perform(browser(jsonPost("/api/auth/passkey/options", Map.of()), host, origin))
                .andExpect(status().isOk()).andReturn());
        device.setOrigin(new Origin(origin));
        var got = device.get(new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(B64D.decode(options.get("challenge").asText())), options.get("timeout").asLong(),
                options.get("rpId").asText(),
                List.of(new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, B64D.decode(credentialId), null)),
                UserVerificationRequirement.REQUIRED, null));
        var r = got.getResponse();
        return Map.of("id", credentialId, "rawId", credentialId, "type", "public-key", "response", Map.of(
                "clientDataJSON", B64.encodeToString(r.getClientDataJSON()),
                "authenticatorData", B64.encodeToString(r.getAuthenticatorData()),
                "signature", B64.encodeToString(r.getSignature()),
                "userHandle", r.getUserHandle() == null ? "" : B64.encodeToString(r.getUserHandle())));
    }

    private static PublicKeyCredentialCreationOptions creationOptions(JsonNode o) {
        return new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(o.at("/rp/id").asText(), o.at("/rp/name").asText()),
                new PublicKeyCredentialUserEntity(B64D.decode(o.at("/user/id").asText()), o.at("/user/name").asText(),
                        o.at("/user/displayName").asText()),
                new DefaultChallenge(B64D.decode(o.get("challenge").asText())),
                List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
                o.get("timeout").asLong(), List.of(),
                new AuthenticatorSelectionCriteria(null, true, ResidentKeyRequirement.REQUIRED, UserVerificationRequirement.REQUIRED),
                AttestationConveyancePreference.NONE, null);
    }

    private static MockHttpServletRequestBuilder browser(MockHttpServletRequestBuilder request, String host, String origin) {
        return request.with(host(host)).header("Origin", origin);
    }
}
