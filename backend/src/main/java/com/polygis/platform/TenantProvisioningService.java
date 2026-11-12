package com.polygis.platform;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final com.polygis.tenancy.PlanService plans;
    private final UserRepository users;

    public TenantProvisioningService(TenantRepository tenants, TenantSchemaMigrator migrator,
                                     TenantRegistry registry, UserAdminService userAdmin, MailService mail,
                                     TransactionTemplate tx, AppProperties props, AuditService audit,
                                     OtpService otp, UserRepository users, JdbcTemplate jdbc,
                                     com.polygis.tenancy.PlanService plans) {
        this.jdbc = jdbc;
        this.plans = plans;
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

    public java.util.Set<String> planNames() {
        return plans.planNames();
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
        registry.publishChange();
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
            if (req.plan() != null) {
                if (!plans.planNames().contains(req.plan())) {
                    throw ApiException.badRequest("unknown_plan", "Unknown plan '" + req.plan() + "'. Configured: " + plans.planNames());
                }
                tenant.setPlan(req.plan());
            }
            audit.record("tenant.updated", "tenant", tenant.getSlug(), "status=" + req.status());
            return tenant;
        });
        registry.publishChange();
        return TenantDto.from(t);
    }

    /**
     * Offboarding, step 2 of 2 (step 1 is suspending the tenant). Irreversible: drops the tenant's schema with
     * all its data and removes it from the registry. Take a backup first: {@code pg_dump -n t_<slug>}.
     *
     * @param confirmSlug must equal the tenant's slug - protects against deleting the wrong row
     */
    public void delete(UUID id, String confirmSlug) {
        Tenant tenant = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
        if (props.tenancy().platformTenant().equals(tenant.getSlug())) {
            throw ApiException.badRequest("platform_tenant", "The platform tenant cannot be deleted");
        }
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            throw ApiException.conflict("tenant_active", "Suspend the tenant before deleting it");
        }
        if (!tenant.getSlug().equals(confirmSlug)) {
            throw ApiException.badRequest("confirmation_mismatch", "Confirmation does not match the tenant slug");
        }
        // schema first: if this fails the registry row remains and the delete can simply be retried
        jdbc.execute("drop schema if exists \"" + TenantSchemas.requireValidSchema(tenant.getSchemaName())
                + "\" cascade");
        tx.executeWithoutResult(s -> {
            tenants.deleteById(id);
            audit.record("tenant.deleted", "tenant", tenant.getSlug(), "schema " + tenant.getSchemaName() + " dropped");
        });
        registry.publishChange();
        log.warn("Deleted tenant {} and dropped schema {}", tenant.getSlug(), tenant.getSchemaName());
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

    /** Schema version, size and usage of one tenant, read straight from its schema. */
    public TenantStatsDto stats(UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
        String schema = "\"" + TenantSchemas.requireValidSchema(t.getSchemaName()) + "\"";
        String platformSchema = "\"" + TenantSchemas.schemaFor(props.tenancy().platformTenant()) + "\"";
        String versionSql = "select version from %s.flyway_schema_history where success and version is not null "
                + "order by installed_rank desc limit 1";
        String version = jdbc.query(versionSql.formatted(schema), rs -> rs.next() ? rs.getString(1) : null);
        String latest = jdbc.query(versionSql.formatted(platformSchema), rs -> rs.next() ? rs.getString(1) : null);
        java.sql.Timestamp migratedAt = jdbc.query(
                "select max(installed_on) from %s.flyway_schema_history where success".formatted(schema),
                rs -> rs.next() ? rs.getTimestamp(1) : null);
        Long size = jdbc.queryForObject("select coalesce(sum(pg_total_relation_size(c.oid)), 0) "
                + "from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                + "where n.nspname = ? and c.relkind = 'r'", Long.class, t.getSchemaName());
        Long userCount = jdbc.queryForObject(
                "select count(*) from %s.users where deleted_at is null".formatted(schema), Long.class);
        Long sessions = jdbc.queryForObject(
                "select count(*) from %s.refresh_tokens where revoked_at is null and expires_at > now()"
                        .formatted(schema), Long.class);
        return new TenantStatsDto(t.getSlug(), version, latest, version != null && version.equals(latest),
                migratedAt == null ? null : migratedAt.toInstant(), size == null ? 0 : size,
                userCount == null ? 0 : userCount, sessions == null ? 0 : sessions);
    }

    /** Re-run pending tenant migrations (e.g. retry a failed provisioning). */
    public void migrate(UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
        migrator.migrateTenant(t.getSchemaName());
    }
}
