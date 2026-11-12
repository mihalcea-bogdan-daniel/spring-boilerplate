package com.polygis.tenancy;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Tenant registry row. Lives in the shared {@code public} schema regardless of the session's search_path. */
@Entity
@Table(name = "tenants", schema = "public")
@Getter
@Setter
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 42)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    private String schemaName;

    /** Optional custom domain (e.g. portal.customer.com). */
    @Column(unique = true)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.PROVISIONING;

    /** Key into app.plans (quotas, feature flags). */
    @Column(nullable = false, length = 40)
    private String plan = "free";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public TenantInfo toInfo() {
        return new TenantInfo(id, slug, schemaName, domain, status);
    }
}
