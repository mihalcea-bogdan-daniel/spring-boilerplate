package com.polygis.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/** Tells Hibernate which tenant (= schema name) the session being opened belongs to. */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return TenantContext.DEFAULT_SCHEMA.equals(tenantId);
    }
}
