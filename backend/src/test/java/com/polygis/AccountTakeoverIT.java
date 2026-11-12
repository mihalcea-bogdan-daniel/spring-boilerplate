package com.polygis;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.auth.OtpService;
import com.polygis.auth.oauth2.OAuth2UserProvisioner;
import com.polygis.auth.oauth2.OAuth2UserProvisioner.ProviderProfile;
import com.polygis.mfa.Totp;
import com.polygis.support.IntegrationTestBase;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantRegistry;

/** Pre-registration takeover and second-factor bypass via the alternative sign-in paths. */
class AccountTakeoverIT extends IntegrationTestBase {

    private static final String ATTACKER_PASSWORD = "Attacker-Passw0rd";

    @Autowired
    OAuth2UserProvisioner provisioner;
    @Autowired
    OtpService otp;
    @Autowired
    TenantRegistry registry;
    @Autowired
    TransactionTemplate tx;

    @Test
    void emailCodeSignInDiscardsAPasswordSetByAnUnverifiedRegistrant() throws Exception {
        provisionTenant("take-otp");
        String host = hostOf("take-otp");
        String victim = "victim@take-otp.test";
        preRegister(host, victim);

        otpLogin(host, victim); // the real owner signs in with a code from their inbox

        mvc.perform(jsonPost("/api/auth/login", Map.of("email", victim, "password", ATTACKER_PASSWORD)).with(host(host)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void socialSignInDiscardsAPasswordSetByAnUnverifiedRegistrant() throws Exception {
        provisionTenant("take-oauth");
        String host = hostOf("take-oauth");
        String victim = "victim@take-oauth.test";
        preRegister(host, victim);

        mvc.perform(jsonPost("/api/auth/oauth2/exchange", Map.of("code", socialLogin("take-oauth", victim, "g-1")))
                .with(host(host))).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());

        mvc.perform(jsonPost("/api/auth/login", Map.of("email", victim, "password", ATTACKER_PASSWORD)).with(host(host)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void socialSignInMustPassTheSecondFactor() throws Exception {
        String admin = provisionTenant("take-mfa");
        String host = hostOf("take-mfa");
        String token = accessToken(otpLogin(host, admin));
        var setup = body(mvc.perform(jsonPost("/api/me/mfa/setup", Map.of()).with(host(host))
                .header("Authorization", bearer(token))).andReturn());
        byte[] key = Totp.base32Decode(setup.get("secret").asText());
        mvc.perform(jsonPost("/api/me/mfa/enable", Map.of("code", Totp.code(key, Totp.currentStep())))
                .with(host(host)).header("Authorization", bearer(token))).andExpect(status().isOk());

        var res = mvc.perform(jsonPost("/api/auth/oauth2/exchange", Map.of("code", socialLogin("take-mfa", admin, "g-2")))
                        .with(host(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
        org.assertj.core.api.Assertions.assertThat(res.getResponse().getCookie("refresh_token")).isNull();
    }

    private void preRegister(String host, String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/register", Map.of("email", email, "password", ATTACKER_PASSWORD))
                .with(host(host))).andExpect(status().isAccepted());
    }

    /** What OAuth2LoginSuccessHandler does after Google/GitHub vouched for the address. */
    private String socialLogin(String slug, String email, String providerId) {
        return TenantContext.callAs(registry.requireBySlug(slug), () -> tx.execute(s -> otp.issueExchangeCode(
                provisioner.findOrCreate(new ProviderProfile("google", providerId, email, true, "V", "ictim")).getId())));
    }
}
