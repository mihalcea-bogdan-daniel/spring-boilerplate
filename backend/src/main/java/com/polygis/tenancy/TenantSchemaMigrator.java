package com.polygis.tenancy;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Owns all DDL. On startup: migrate {@code public} (tenant registry), then every tenant schema.
 * At runtime: {@link #migrateTenant(String)} is used by tenant provisioning.
 * Migrations: {@code db/migration/public} and {@code db/migration/tenant}.
 */
@Slf4j
public class TenantSchemaMigrator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public TenantSchemaMigrator(DataSource dataSource, AppProperties props) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.props = props;
    }

    public void migrateAll() {
        migratePublic();
        List<String> schemas = jdbc.queryForList(
                "select schema_name from public.tenants where status <> 'PROVISIONING'", String.class);
        schemas.forEach(this::migrateTenant);
        log.info("Migrated public + {} tenant schema(s)", schemas.size());
    }

    public void migratePublic() {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(TenantContext.DEFAULT_SCHEMA)
                .locations("classpath:db/migration/public")
                .placeholders(Map.of(
                        "platformSlug", props.tenancy().platformTenant(),
                        "platformSchema", TenantSchemas.schemaFor(props.tenancy().platformTenant()),
                        "platformDomain", props.tenancy().platformDomain()))
                .load()
                .migrate();
    }

    public void migrateTenant(String schema) {
        TenantSchemas.requireValidSchema(schema);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)          // created if missing, and used as default schema
                .createSchemas(true)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
    }
}
