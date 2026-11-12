package com.polygis.tenancy;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory index of tenants (by slug and by custom domain) so tenant resolution never hits the DB on the hot
 * path. Uses plain JDBC against {@code public.tenants} to stay independent of the Hibernate tenant session.
 * Other nodes are told about changes through PostgreSQL NOTIFY; the periodic refresh is only a safety net.
 */
@Slf4j
@Component
public class TenantRegistry {

    /** PostgreSQL NOTIFY channel announcing that public.tenants changed. */
    public static final String CHANNEL = "tenant_registry";

    private final JdbcTemplate jdbc;
    private volatile Map<String, TenantInfo> bySlug = Map.of();
    private volatile Map<String, TenantInfo> byDomain = Map.of();

    public TenantRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void reload() {
        Map<String, TenantInfo> slugs = new ConcurrentHashMap<>();
        Map<String, TenantInfo> domains = new ConcurrentHashMap<>();
        jdbc.query("select id, slug, schema_name, domain, status from public.tenants", rs -> {
            TenantInfo t = new TenantInfo(
                    rs.getObject("id", java.util.UUID.class),
                    rs.getString("slug"),
                    rs.getString("schema_name"),
                    rs.getString("domain"),
                    TenantStatus.valueOf(rs.getString("status")));
            slugs.put(t.slug(), t);
            if (t.domain() != null) domains.put(t.domain().toLowerCase(Locale.ROOT), t);
        });
        this.bySlug = slugs;
        this.byDomain = domains;
        log.debug("Tenant registry loaded {} tenants", slugs.size());
    }

    /** Call after changing public.tenants: reloads this node now and tells the others (see TenantRegistryListener). */
    public void publishChange() {
        reload();
        jdbc.execute("NOTIFY " + CHANNEL);
    }

    public Optional<TenantInfo> findBySlug(String slug) {
        return slug == null ? Optional.empty() : Optional.ofNullable(bySlug.get(slug.toLowerCase(Locale.ROOT)));
    }

    public Optional<TenantInfo> findByDomain(String host) {
        return host == null ? Optional.empty() : Optional.ofNullable(byDomain.get(host.toLowerCase(Locale.ROOT)));
    }

    public TenantInfo requireBySlug(String slug) {
        return findBySlug(slug).orElseThrow(() -> new TenantNotResolvedException("Unknown tenant: " + slug));
    }

    public java.util.Collection<TenantInfo> all() {
        return bySlug.values();
    }
}
