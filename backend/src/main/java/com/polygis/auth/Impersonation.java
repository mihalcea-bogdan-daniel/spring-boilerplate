package com.polygis.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.polygis.common.ApiException;

/**
 * Someone signed in as another user (super-admin or supervisor) may look around and act, but never touch the
 * user's credentials, sessions or keys, nor start another impersonation. Call {@link #reject} first thing in
 * such endpoints.
 */
public final class Impersonation {

    private Impersonation() {
    }

    public static boolean isImpersonated(Jwt jwt) {
        return jwt != null && jwt.getClaimAsString(JwtClaims.ACTOR) != null;
    }

    public static void reject(Jwt jwt) {
        if (isImpersonated(jwt)) {
            throw ApiException.forbidden("impersonated", "Not allowed in an impersonated session");
        }
    }

    public static void reject(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) reject(token.getToken());
    }
}
