package com.polygis.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.polygis.common.Problems;

import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Throttles POST /api/auth/** per tenant + client IP. Runs right after tenant resolution. */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenant = TenantContext.current().map(TenantInfo::slug).orElse("-");
        long retryAfter = limiter.tryAcquire(tenant + "|" + request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Too many attempts, try again in " + retryAfter + " seconds");
            return;
        }
        chain.doFilter(request, response);
    }
}
