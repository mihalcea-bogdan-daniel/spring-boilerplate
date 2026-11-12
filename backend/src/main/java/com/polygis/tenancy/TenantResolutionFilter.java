package com.polygis.tenancy;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * First filter in the chain (registered before Spring Security, see TenancyConfig): binds the host tenant to the
 * thread. Requests to unknown hosts are rejected except for a few tenant-agnostic paths.
 */
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    /** Paths that may run without a tenant (health probes, docs, the central OAuth2 callback, static assets). */
    private static final List<String> TENANT_OPTIONAL = List.of(
            "/actuator/**", "/api/docs/**", "/api/login/oauth2/code/**", "/error",
            "/css/**", "/js/**", "/images/**", "/favicon.ico");

    private final HostTenantResolver resolver;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public TenantResolutionFilter(HostTenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var tenant = resolver.resolve(request);
            if (tenant.isPresent()) {
                if (!tenant.get().isActive()) {
                    reject(response, HttpStatus.FORBIDDEN, "Tenant is not active");
                    return;
                }
                TenantContext.set(tenant.get());
            } else if (!isTenantOptional(request)) {
                log.debug("No tenant for host {}", request.getServerName());
                reject(response, HttpStatus.NOT_FOUND, "Unknown tenant");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isTenantOptional(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return TENANT_OPTIONAL.stream().anyMatch(p -> matcher.match(p, path));
    }

    static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
