package com.polygis.tenancy;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.polygis.auth.JwtClaims;
import com.polygis.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs right after bearer-token authentication and enforces tenant isolation:
 * <ul>
 *   <li>a regular user's token is only valid on the host of the tenant that issued it;</li>
 *   <li>a SUPER_ADMIN (token issued by the platform tenant) may target any tenant by sending
 *       {@code X-Tenant-ID: <slug>}; the TenantContext is switched before any repository is touched;</li>
 *   <li>anyone else sending a mismatching X-Tenant-ID is refused.</li>
 * </ul>
 */
public class TenantAccessFilter extends OncePerRequestFilter {

    private final TenantRegistry registry;
    private final AppProperties.Tenancy props;

    public TenantAccessFilter(TenantRegistry registry, AppProperties props) {
        this.registry = registry;
        this.props = props.tenancy();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requested = Optional.ofNullable(request.getHeader(props.tenantHeader()))
                .map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        Optional<TenantInfo> hostTenant = TenantContext.current();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tokenTenant = jwtAuth.getToken().getClaimAsString(JwtClaims.TENANT);
            MDC.put(TenantResolutionFilter.MDC_USER, jwtAuth.getToken().getSubject());

            if (isSuperAdmin(jwtAuth, tokenTenant)) {
                if (requested != null) {
                    Optional<TenantInfo> target = registry.findBySlug(requested);
                    if (target.isEmpty()) {
                        TenantResolutionFilter.reject(response, HttpStatus.NOT_FOUND, "Unknown tenant " + requested);
                        return;
                    }
                    // super-admins may also manage SUSPENDED tenants
                    TenantContext.set(target.get());
                    MDC.put(TenantResolutionFilter.MDC_TENANT, target.get().slug() + "<-" + tokenTenant);
                }
            } else {
                String current = hostTenant.map(TenantInfo::slug).orElse(null);
                if (current == null || !current.equals(tokenTenant)) {
                    TenantResolutionFilter.reject(response, HttpStatus.FORBIDDEN, "Token not valid for this tenant");
                    return;
                }
                if (requested != null && !requested.equals(current)) {
                    TenantResolutionFilter.reject(response, HttpStatus.FORBIDDEN, "Tenant switching not allowed");
                    return;
                }
            }
        }
        // Anonymous requests: the host tenant wins; X-Tenant-ID is ignored (or used by the dev fallback upstream).
        chain.doFilter(request, response);
    }

    private boolean isSuperAdmin(JwtAuthenticationToken auth, String tokenTenant) {
        // SUPER_ADMIN is only honoured when the token was minted by the platform tenant
        return props.platformTenant().equals(tokenTenant)
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}
