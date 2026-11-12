package com.polygis.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Tenancy tenancy,
        Security security,
        Bootstrap bootstrap,
        Mail mail,
        Cors cors) {

    public record Tenancy(
            String baseDomain,
            @DefaultValue("platform") String platformTenant,
            String platformDomain,
            @DefaultValue("X-Tenant-ID") String tenantHeader,
            @DefaultValue("false") boolean devHeaderFallback,
            @DefaultValue Set<String> reservedSlugs) {
    }

    public record Security(Jwt jwt, RefreshCookie refreshCookie, Password password, Otp otp, OAuth2 oauth2) {

        public record Jwt(String issuer, String secret,
                          @DefaultValue("HS256") String algorithm, String privateKey,
                          @DefaultValue("15m") Duration accessTokenTtl,
                          @DefaultValue("14d") Duration refreshTokenTtl) {
        }

        public record RefreshCookie(@DefaultValue("refresh_token") String name,
                                    @DefaultValue("/api/auth") String path,
                                    @DefaultValue("true") boolean secure,
                                    @DefaultValue("Strict") String sameSite) {
        }

        public record Password(@DefaultValue("12") int bcryptStrength,
                               @DefaultValue("5") int maxFailedAttempts,
                               @DefaultValue("15m") Duration lockoutDuration) {
        }

        public record Otp(@DefaultValue("6") int length,
                          @DefaultValue("10m") Duration ttl,
                          @DefaultValue("5") int maxAttempts,
                          @DefaultValue("60s") Duration resendCooldown) {
        }

        public record OAuth2(@DefaultValue("true") boolean autoRegister,
                             String frontendCallback,
                             String cookieDomain) {
        }
    }

    public record Bootstrap(String superAdminEmail, String superAdminPassword) {
    }

    public record Mail(String from, @DefaultValue("Boilerplate") String productName) {
    }

    public record Cors(@DefaultValue List<String> allowedOriginPatterns) {
    }
}
