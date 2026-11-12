package com.polygis.tenancy;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
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

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TENANT = "tenant";
    public static final String MDC_USER = "user";

    private final HostTenantResolver resolver;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public TenantResolutionFilter(HostTenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Correlation data for every log line of this request (see logging.pattern.level in application.yml)
        String requestId = requestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            var tenant = resolver.resolve(request);
            if (tenant.isPresent()) {
                if (!tenant.get().isActive()) {
                    reject(response, HttpStatus.FORBIDDEN, "Tenant is not active");
                    return;
                }
                TenantContext.set(tenant.get());
                MDC.put(MDC_TENANT, tenant.get().slug());
            } else if (!isTenantOptional(request)) {
                log.debug("No tenant for host {}", request.getServerName());
                reject(response, HttpStatus.NOT_FOUND, "Unknown tenant");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT);
            MDC.remove(MDC_USER);
        }
    }

    /** Reuses the proxy's id when it looks sane, so one id follows the request across nginx, Nuxt and Spring. */
    private static String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null && incoming.matches("[A-Za-z0-9._-]{8,64}") ? incoming
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
