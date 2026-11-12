package com.polygis.platform;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.common.ApiException;
import com.polygis.common.PageResponse;
import com.polygis.config.AppProperties;
import com.polygis.mail.MailService;
import com.polygis.platform.TenantDtos.*;
import com.polygis.tenancy.*;
import com.polygis.user.Role;
import com.polygis.user.UserAdminService;
import com.polygis.user.dto.UserDtos.CreateUserRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Provisioning = register (PROVISIONING) -> CREATE SCHEMA + Flyway -> seed admin user -> ACTIVE.
 * Steps are separate transactions because DDL and the tenant-scoped session use different schemas; a failure
 * leaves the row in PROVISIONING so it can be retried or cleaned up.
 */
@Slf4j
@Service
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final TenantSchemaMigrator migrator;
    private final TenantRegistry registry;
    private final UserAdminService userAdmin;
    private final MailService mail;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public TenantProvisioningService(TenantRepository tenants, TenantSchemaMigrator migrator,
                                     TenantRegistry registry, UserAdminService userAdmin, MailService mail,
                                     TransactionTemplate tx, AppProperties props) {
        this.tenants = tenants;
        this.migrator = migrator;
        this.registry = registry;
        this.userAdmin = userAdmin;
        this.mail = mail;
        this.tx = tx;
        this.props = props;
    }

    public PageResponse<TenantDto> search(String q, Pageable pageable) {
        return PageResponse.of(tenants.search(q == null || q.isBlank() ? null : q.trim(), pageable), TenantDto::from);
    }

    public TenantDto provision(CreateTenantRequest req) {
        if (props.tenancy().reservedSlugs().contains(req.slug())) {
            throw ApiException.badRequest("slug_reserved", "This identifier is reserved");
        }
        Tenant tenant = tx.execute(s -> {
            if (tenants.existsBySlug(req.slug())) throw ApiException.conflict("slug_taken", "Slug already in use");
            if (req.domain() != null && !req.domain().isBlank() && tenants.existsByDomain(req.domain())) {
                throw ApiException.conflict("domain_taken", "Domain already in use");
            }
            Tenant t = new Tenant();
            t.setSlug(req.slug());
            t.setName(req.name());
            t.setDomain(req.domain() == null || req.domain().isBlank() ? null : req.domain().toLowerCase());
            t.setSchemaName(TenantSchemas.schemaFor(req.slug()));
            t.setStatus(TenantStatus.PROVISIONING);
            return tenants.save(t);
        });

        migrator.migrateTenant(tenant.getSchemaName());

        TenantInfo info = tenant.toInfo();
        TenantContext.runAs(info, () -> userAdmin.create(new CreateUserRequest(
                req.adminEmail(), req.adminFirstName(), req.adminLastName(), null, Set.of(Role.TENANT_ADMIN))));

        Tenant active = tx.execute(s -> {
            Tenant t = tenants.findById(tenant.getId()).orElseThrow();
            t.setStatus(TenantStatus.ACTIVE);
            return t;
        });
        registry.reload();
        mail.sendTenantInvite(req.adminEmail(), req.name(), active.toInfo().host(props.tenancy().baseDomain()));
        log.info("Provisioned tenant {} ({})", active.getSlug(), active.getSchemaName());
        return TenantDto.from(active);
    }

    public TenantDto update(UUID id, UpdateTenantRequest req) {
        Tenant t = tx.execute(s -> {
            Tenant tenant = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
            if (props.tenancy().platformTenant().equals(tenant.getSlug()) && req.status() != TenantStatus.ACTIVE) {
                throw ApiException.badRequest("platform_tenant", "The platform tenant cannot be suspended");
            }
            tenant.setName(req.name());
            tenant.setDomain(req.domain() == null || req.domain().isBlank() ? null : req.domain().toLowerCase());
            tenant.setStatus(req.status());
            return tenant;
        });
        registry.reload();
        return TenantDto.from(t);
    }

    /** Re-run pending tenant migrations (e.g. retry a failed provisioning). */
    public void migrate(UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
        migrator.migrateTenant(t.getSchemaName());
    }
}
