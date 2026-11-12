package com.polygis.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.polygis.tenancy.TenantRegistry;
import com.polygis.tenancy.TenantSchemas;

/**
 * Makes access tokens revocable without giving up stateless JWTs: each user row carries
 * {@code sessions_invalid_before}; tokens issued earlier are refused. Lookups are cached for a few seconds,
 * so another node notices a revocation within {@link #CACHE_MILLIS}; this node evicts immediately.
 */
@Component
public class AccessTokenRevocation {

    static final long CACHE_MILLIS = 5_000;

    /** Optional.empty() = no cutoff; null value never cached. A deleted or missing user maps to Instant.MAX. */
    private record Entry(Optional<Instant> cutoff, long expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final TenantRegistry registry;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public AccessTokenRevocation(JdbcTemplate jdbc, TenantRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    /** True if a token for this user, issued at {@code issuedAt} by tenant {@code tenantSlug}, is revoked. */
    public boolean isRevoked(String tenantSlug, UUID userId, Instant issuedAt) {
        String key = tenantSlug + "|" + userId;
        long now = System.currentTimeMillis();
        Entry entry = cache.get(key);
        if (entry == null || entry.expiresAt < now) {
            entry = new Entry(load(tenantSlug, userId), now + CACHE_MILLIS);
            cache.put(key, entry);
        }
        // second precision, like the token's iat: a token minted right after the cutoff stays valid
        return entry.cutoff.map(c -> issuedAt == null || issuedAt.getEpochSecond() < c.getEpochSecond()).orElse(false);
    }

    /** Call after changing {@code sessions_invalid_before} so this node applies it immediately. */
    public void evict(String tenantSlug, UUID userId) {
        cache.remove(tenantSlug + "|" + userId);
    }

    private Optional<Instant> load(String tenantSlug, UUID userId) {
        var tenant = registry.findBySlug(tenantSlug);
        if (tenant.isEmpty()) return Optional.of(Instant.MAX);
        String schema = TenantSchemas.requireValidSchema(tenant.get().schema());
        var rows = jdbc.query("select sessions_invalid_before, deleted_at from \"" + schema + "\".users where id = ?",
                (rs, i) -> {
                    if (rs.getTimestamp("deleted_at") != null) return Optional.of(Instant.MAX);
                    Timestamp cutoff = rs.getTimestamp("sessions_invalid_before");
                    return Optional.ofNullable(cutoff).map(Timestamp::toInstant);
                }, userId);
        return rows.isEmpty() ? Optional.of(Instant.MAX) : rows.get(0);
    }
}
