package com.polygis.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Rejects access tokens issued before the user's sessions were invalidated (see {@link AccessTokenRevocation}). */
public class AccessTokenRevocationFilter extends OncePerRequestFilter {

    private final AccessTokenRevocation revocation;

    public AccessTokenRevocationFilter(AccessTokenRevocation revocation) {
        this.revocation = revocation;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            var jwt = auth.getToken();
            if (revocation.isRevoked(jwt.getClaimAsString(JwtClaims.TENANT), UUID.fromString(jwt.getSubject()),
                    jwt.getIssuedAt())) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                // invalid_token makes the SPA refresh: role changes recover silently, revoked sessions go to login
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("""
                        {"type":"about:blank","title":"Unauthorized","status":401,\
                        "detail":"Session is no longer valid","code":"token_revoked"}""");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
