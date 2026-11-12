package com.polygis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupSafetyCheckTest {

    @Test
    void productionRefusesDevSecretsAndFallbacks() {
        var check = new StartupSafetyCheck(env("prod"),
                props(StartupSafetyCheck.DEV_JWT_SECRET, StartupSafetyCheck.DOCUMENTED_PASSWORD, true, false));
        assertThat(check.problems()).hasSize(4);
        assertThatThrownBy(check::verify).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET").hasMessageContaining("SUPER_ADMIN_PASSWORD");
    }

    @Test
    void noProfileIsTreatedLikeProduction() {
        var check = new StartupSafetyCheck(env(), props(StartupSafetyCheck.DEV_JWT_SECRET, null, false, true));
        assertThat(check.problems()).containsExactly("JWT_SECRET is the published development secret");
    }

    @Test
    void safeProductionAndDevAreAccepted() {
        new StartupSafetyCheck(env("prod"), props("a-real-secret-a-real-secret-a-real-secret", "S3cret!", false, true))
                .verify();
        new StartupSafetyCheck(env("dev"),
                props(StartupSafetyCheck.DEV_JWT_SECRET, StartupSafetyCheck.DOCUMENTED_PASSWORD, true, false)).verify();
        assertThat(new StartupSafetyCheck(env("dev", "prod"), props("x", null, false, true)).problems())
                .containsExactly("'dev' and 'prod' profiles are both active");
    }

    private static MockEnvironment env(String... profiles) {
        var env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    private static AppProperties props(String secret, String superAdminPassword, boolean fallback, boolean secure) {
        return new AppProperties(
                new AppProperties.Tenancy("example.com", "platform", "admin.example.com", "X-Tenant-ID", fallback, Set.of()),
                new AppProperties.Security(
                        new AppProperties.Security.Jwt("i", secret, "HS256", null, Duration.ofMinutes(15), Duration.ofDays(1)),
                        new AppProperties.Security.RefreshCookie("refresh_token", "/api/auth", secure, "Strict"),
                        null, null, null),
                new AppProperties.Bootstrap("root@example.com", superAdminPassword), null,
                new AppProperties.Cors(List.of()));
    }
}
