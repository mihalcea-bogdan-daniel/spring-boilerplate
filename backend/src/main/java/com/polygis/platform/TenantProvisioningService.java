package com.polygis.platform;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.audit.AuditService;
import com.polygis.auth.OtpService;
import com.polygis.common.ApiException;
import com.polygis.user.UserRepository;
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
    private final AuditService audit;
    private final OtpService otp;
    private final UserRepository users;

    public TenantProvisioningService(TenantRepository tenants, TenantSchemaMigrator migrator,
                                     TenantRegistry registry, UserAdminService userAdmin, MailService mail,
                                     TransactionTemplate tx, AppProperties props, AuditService audit,
                                     OtpService otp, UserRepository users) {
        this.tenants = tenants;
        this.migrator = migrator;
        this.registry = registry;
        this.userAdmin = userAdmin;
        this.mail = mail;
        this.tx = tx;
        this.props = props;
        this.audit = audit;
        this.otp = otp;
        this.users = users;
    }

    public PageResponse<TenantDto> search(String q, Pageable pageable) {
        return PageResponse.of(tenants.search(q == null ? "" : q.trim(), pageable), TenantDto::from);
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
                req.adminEmail(), req.adminFirstName(), req.adminLastName(), null, Set.of(Role.TENANT_ADMIN)),
                false)); // the tenant invitation below replaces the per-user invite

        Tenant active = tx.execute(s -> {
            Tenant t = tenants.findById(tenant.getId()).orElseThrow();
            t.setStatus(TenantStatus.ACTIVE);
            audit.record("tenant.provisioned", "tenant", t.getSlug(), "admin=" + req.adminEmail());
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
            audit.record("tenant.updated", "tenant", tenant.getSlug(), "status=" + req.status());
            return tenant;
        });
        registry.reload();
        return TenantDto.from(t);
    }

    /** @return URL on the tenant host that starts the impersonated session */
    public String impersonate(String slug, UUID userId, String actorEmail) {
        if (props.tenancy().platformTenant().equals(slug)) {
            throw ApiException.forbidden("platform_tenant", "Platform accounts cannot be impersonated");
        }
        TenantInfo target = registry.requireBySlug(slug);
        String code = TenantContext.callAs(target, () -> tx.execute(s -> {
            if (!users.existsById(userId)) throw ApiException.notFound("User");
            audit.record("user.impersonation_started", "user", userId, null);
            return otp.issueImpersonationCode(userId, actorEmail);
        }));
        return UriComponentsBuilder
                .fromUriString(props.security().oauth2().frontendCallback()
                        .replace("{host}", target.host(props.tenancy().baseDomain())))
                .queryParam("impersonate", code).build().encode().toUriString();
    }

    /** Re-run pending tenant migrations (e.g. retry a failed provisioning). */
    public void migrate(UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
        migrator.migrateTenant(t.getSchemaName());
    }
}
