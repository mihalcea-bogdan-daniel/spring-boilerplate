package com.polygis.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.polygis.common.Problems;

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
                    // invalid_token makes the SPA refresh: role changes recover silently, revoked sessions go to login
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                    Problems.write(response, HttpStatus.UNAUTHORIZED, "token_revoked", "Session is no longer valid");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
