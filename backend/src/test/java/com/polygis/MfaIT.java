package com.polygis;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.polygis.auth.OtpPurpose;
import com.polygis.mfa.Totp;
import com.polygis.support.IntegrationTestBase;

class MfaIT extends IntegrationTestBase {

    @Test
    void totpIsRequiredAfterEnablingAndRecoveryCodesWorkOnce() throws Exception {
        String email = provisionTenant("mfa-one");
        String host = hostOf("mfa-one");
        String token = accessToken(otpLogin(host, email));

        var setup = body(mvc.perform(jsonPost("/api/me/mfa/setup", Map.of()).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn());
        byte[] key = Totp.base32Decode(setup.get("secret").asText());

        mvc.perform(jsonPost("/api/me/mfa/enable", Map.of("code", "000000")).with(host(host))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        var enabled = body(mvc.perform(jsonPost("/api/me/mfa/enable", Map.of("code", Totp.code(key, Totp.currentStep())))
                        .with(host(host)).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(8)).andReturn());
        String recovery = enabled.get("recoveryCodes").get(0).asText();

        // first factor now only yields a challenge, no tokens and no refresh cookie
        String challenge = challenge(host, email);

        // the code used to enable MFA cannot be replayed
        mvc.perform(jsonPost("/api/auth/mfa/verify", Map.of("mfaToken", challenge, "code", Totp.code(key, Totp.currentStep())))
                .with(host(host))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("mfa_invalid"));

        // a recovery code completes the sign-in ... once
        mvc.perform(jsonPost("/api/auth/mfa/verify", Map.of("mfaToken", challenge, "code", recovery)).with(host(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mfaEnabled").value(true));
        mvc.perform(jsonPost("/api/auth/mfa/verify", Map.of("mfaToken", challenge, "code", recovery)).with(host(host)))
                .andExpect(status().isUnauthorized()); // challenge consumed
        mvc.perform(jsonPost("/api/auth/mfa/verify", Map.of("mfaToken", challenge(host, email), "code", recovery))
                .with(host(host))).andExpect(status().isBadRequest()); // recovery code burned

        // a fresh TOTP step works
        mvc.perform(jsonPost("/api/auth/mfa/verify", Map.of("mfaToken", challenge(host, email),
                        "code", Totp.code(key, Totp.currentStep() + 1))).with(host(host)))
                .andExpect(status().isOk());
    }

    private String challenge(String host, String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/otp/request", Map.of("email", email)).with(host(host)))
                .andExpect(status().isAccepted());
        var res = mvc.perform(jsonPost("/api/auth/otp/verify",
                        Map.of("email", email, "code", lastOtp(email, OtpPurpose.LOGIN))).with(host(host)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
        org.assertj.core.api.Assertions.assertThat(res.getResponse().getCookie("refresh_token")).isNull();
        return body(res).get("mfaToken").asText();
    }
}
