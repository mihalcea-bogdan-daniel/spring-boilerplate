package com.polygis.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Request-scoped holder of the active tenant. Set by {@link TenantResolutionFilter} (host based) and possibly
 * overridden by {@link TenantAccessFilter} (super-admin X-Tenant-ID). Always cleared in a finally block.
 */
public final class TenantContext {

    /** Schema used when no tenant is bound (shared tables, bootstrap, health checks). */
    public static final String DEFAULT_SCHEMA = "public";

    private static final ThreadLocal<TenantInfo> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantInfo tenant) {
        CURRENT.set(tenant);
    }

    public static Optional<TenantInfo> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantInfo require() {
        TenantInfo t = CURRENT.get();
        if (t == null) {
            throw new TenantNotResolvedException("No tenant bound to the current request");
        }
        return t;
    }

    public static String currentSchema() {
        TenantInfo t = CURRENT.get();
        return t == null ? DEFAULT_SCHEMA : t.schema();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code work} as another tenant and restores the previous one (e.g. provisioning, super-admin "me"). */
    public static <T> T callAs(TenantInfo tenant, Supplier<T> work) {
        TenantInfo previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static void runAs(TenantInfo tenant, Runnable work) {
        callAs(tenant, () -> { work.run(); return null; });
    }
}
