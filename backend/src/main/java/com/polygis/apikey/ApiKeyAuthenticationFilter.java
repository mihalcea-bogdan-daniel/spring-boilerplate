package com.polygis.apikey;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.polygis.tenancy.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates {@code X-API-Key} against the host tenant. Keys are for the business/admin API only: personal
 * endpoints (/api/me, /api/auth) and the platform console always require a signed-in user.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final List<String> USER_ONLY = List.of("/api/me", "/api/auth/", "/api/platform/");

    private final ApiKeyService keys;

    public ApiKeyAuthenticationFilter(ApiKeyService keys) {
        this.keys = keys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null
                || USER_ONLY.stream().anyMatch(p -> request.getRequestURI().startsWith(p));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var key = TenantContext.current().flatMap(t -> keys.authenticate(request.getHeader(HEADER)));
        if (key.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,\
                    "detail":"Invalid API key","code":"invalid_api_key"}""");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(key.get()));
        chain.doFilter(request, response);
    }
}
