package com.polygis.tenancy;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.polygis.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the tenant from the public host the request was made to (nginx/apache forward it through
 * Host / X-Forwarded-Host; {@code server.forward-headers-strategy=framework} makes getServerName() honour it).
 * <ol>
 *   <li>exact match on a tenant's custom domain (incl. the platform domain, e.g. admin.example.com)</li>
 *   <li>{@code <slug>.<base-domain>} sub-domain</li>
 *   <li>dev only: {@code X-Tenant-ID} header when the host carries no tenant</li>
 * </ol>
 */
@Component
public class HostTenantResolver {

    private final TenantRegistry registry;
    private final AppProperties.Tenancy props;

    public HostTenantResolver(TenantRegistry registry, AppProperties props) {
        this.registry = registry;
        this.props = props.tenancy();
    }

    public Optional<TenantInfo> resolve(HttpServletRequest request) {
        String host = request.getServerName().toLowerCase(Locale.ROOT);

        Optional<TenantInfo> byDomain = registry.findByDomain(host);
        if (byDomain.isPresent()) return byDomain;

        Optional<String> slug = subdomainOf(host);
        if (slug.isPresent() && !props.reservedSlugs().contains(slug.get())) {
            Optional<TenantInfo> bySlug = registry.findBySlug(slug.get());
            if (bySlug.isPresent()) return bySlug;
        }

        if (props.devHeaderFallback()) {
            return registry.findBySlug(request.getHeader(props.tenantHeader()));
        }
        return Optional.empty();
    }

    Optional<String> subdomainOf(String host) {
        String suffix = "." + props.baseDomain().toLowerCase(Locale.ROOT);
        if (!host.endsWith(suffix)) return Optional.empty();
        String prefix = host.substring(0, host.length() - suffix.length());
        // only one label deep: "acme.example.com" -> "acme"; "x.acme.example.com" is rejected
        return prefix.isEmpty() || prefix.contains(".") ? Optional.empty() : Optional.of(prefix);
    }
}
