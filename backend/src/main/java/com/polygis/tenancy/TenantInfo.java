package com.polygis.tenancy;

import java.util.UUID;

/** Immutable snapshot of a tenant as seen by the request pipeline. */
public record TenantInfo(UUID id, String slug, String schema, String domain, TenantStatus status) {

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    /** Public host of this tenant: custom domain if set, otherwise slug.baseDomain. */
    public String host(String baseDomain) {
        return domain != null && !domain.isBlank() ? domain : slug + "." + baseDomain;
    }
}
