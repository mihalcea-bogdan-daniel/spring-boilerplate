package com.polygis.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import com.polygis.common.Hashing;
import com.polygis.tenancy.PlanService;

/**
 * Tenant API keys for machine-to-machine access: {@code X-API-Key: pk_<prefix>_<secret>}.
 * A key lives in one tenant's schema, so it only ever works on that tenant's host, and it can never hold more
 * permissions than the admin who created it.
 */
@Service
public class ApiKeyService {

    public static final String FEATURE = "api-keys";
    private static final String PREFIX = "pk_";

    private final ApiKeyRepository keys;
    private final AuditService audit;
    private final PlanService plans;

    public ApiKeyService(ApiKeyRepository keys, AuditService audit, PlanService plans) {
        this.keys = keys;
        this.audit = audit;
        this.plans = plans;
    }

    public record Created(ApiKey key, String rawKey) {
    }

    @Transactional
    public Created create(String name, Set<String> permissions, Authentication creator) {
        plans.requireFeature(FEATURE);
        Set<String> own = new TreeSet<>();
        for (GrantedAuthority a : creator.getAuthorities()) own.add(a.getAuthority());
        if (permissions == null || permissions.isEmpty() || !own.containsAll(permissions)) {
            throw ApiException.forbidden("permission_escalation",
                    "A key needs at least one permission and cannot have permissions you do not hold");
        }
        String prefix = Hashing.randomToken(6).replace("-", "a").replace("_", "b");
        String raw = PREFIX + prefix + "_" + Hashing.randomToken(32);

        ApiKey key = new ApiKey();
        key.setName(name.trim());
        key.setKeyPrefix(PREFIX + prefix);
        key.setKeyHash(Hashing.sha256(raw));
        key.setPermissions(String.join(",", new TreeSet<>(permissions)));
        key.setCreatedBy(creator.getName());
        keys.save(key);
        audit.record("apikey.created", "api_key", key.getId(), key.getName() + " " + permissions);
        return new Created(key, raw);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list() {
        return keys.findByRevokedAtIsNullOrderByCreatedAtDesc();
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey key = keys.findById(id).orElseThrow(() -> ApiException.notFound("API key"));
        key.setRevokedAt(Instant.now());
        audit.record("apikey.revoked", "api_key", key.getId(), key.getName());
    }

    /** Resolves a presented key inside the current tenant; unknown, malformed and revoked keys yield empty. */
    @Transactional
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(PREFIX) || rawKey.length() > 120) return Optional.empty();
        Optional<ApiKey> key = keys.findByKeyHashAndRevokedAtIsNull(Hashing.sha256(rawKey));
        key.ifPresent(k -> {
            // coarse "last used": at most one write per minute per key
            if (k.getLastUsedAt() == null || k.getLastUsedAt().isBefore(Instant.now().minusSeconds(60))) {
                k.setLastUsedAt(Instant.now());
            }
        });
        return key;
    }
}
