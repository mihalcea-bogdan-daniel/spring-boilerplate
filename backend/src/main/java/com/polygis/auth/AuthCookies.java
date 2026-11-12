package com.polygis.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.polygis.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refresh token transport: HttpOnly, host-only (no Domain attribute => never shared across tenant sub-domains),
 * SameSite=Strict, scoped to /api/auth.
 */
@Component
public class AuthCookies {

    private final AppProperties.Security.RefreshCookie cookie;
    private final Duration ttl;

    public AuthCookies(AppProperties props) {
        this.cookie = props.security().refreshCookie();
        this.ttl = props.security().jwt().refreshTokenTtl();
    }

    public void setRefreshToken(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, ttl).toString());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public String readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (cookie.name().equals(c.getName()) && !c.getValue().isBlank()) return c.getValue();
        }
        return null;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(cookie.path())
                .maxAge(maxAge)
                .build();
    }
}
