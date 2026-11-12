package com.polygis.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start outside the {@code dev}/{@code test} profiles with settings that are only acceptable on a
 * laptop: the published dev JWT secret, the documented super-admin password, or the anonymous X-Tenant-ID
 * fallback. Fails closed, so a deploy that forgets a secret never comes up half-secure.
 */
@Component
public class StartupSafetyCheck {

    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";
    static final String DOCUMENTED_PASSWORD = "ChangeMe!123";

    private final Environment env;
    private final AppProperties props;

    public StartupSafetyCheck(Environment env, AppProperties props) {
        this.env = env;
        this.props = props;
    }

    @PostConstruct
    void verify() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe configuration for profiles "
                    + Arrays.toString(env.getActiveProfiles()) + ":\n - " + String.join("\n - ", problems));
        }
    }

    List<String> problems() {
        List<String> active = Arrays.asList(env.getActiveProfiles());
        List<String> problems = new ArrayList<>();
        if (active.contains("dev") && active.contains("prod")) {
            problems.add("'dev' and 'prod' profiles are both active");
        }
        if (active.contains("dev") || active.contains("test")) return problems;

        var jwt = props.security().jwt();
        if (DEV_JWT_SECRET.equals(jwt.secret())) {
            problems.add("JWT_SECRET is the published development secret");
        }
        if (props.bootstrap() != null && DOCUMENTED_PASSWORD.equals(props.bootstrap().superAdminPassword())) {
            problems.add("SUPER_ADMIN_PASSWORD is the documented default");
        }
        if (props.tenancy().devHeaderFallback()) {
            problems.add("app.tenancy.dev-header-fallback lets anonymous callers choose a tenant");
        }
        if (!props.security().refreshCookie().secure()) {
            problems.add("the refresh-token cookie is not marked Secure");
        }
        return problems;
    }
}
