package com.polygis.tenancy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private final int parallelism;

    public TenantSchemaMigrator(DataSource dataSource, AppProperties props) {
        this(dataSource, props, 4);
    }

    public TenantSchemaMigrator(DataSource dataSource, AppProperties props, int parallelism) {
        this.parallelism = Math.max(1, parallelism);
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.props = props;
    }

    public void migrateAll() {
        migratePublic();
        List<String> schemas = jdbc.queryForList(
                "select schema_name from public.tenants where status <> 'PROVISIONING'", String.class);
        migrateTenants(schemas);
        log.info("Migrated public + {} tenant schema(s) with parallelism {}", schemas.size(), parallelism);
    }

    /**
     * Migrates tenant schemas concurrently (bounded pool). Every schema is attempted; if any fail, startup is
     * aborted with the full list so a half-migrated fleet never serves traffic unnoticed.
     */
    void migrateTenants(List<String> schemas) {
        if (schemas.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, schemas.size()), r -> {
            Thread t = new Thread(r, "tenant-migration");
            t.setDaemon(true);
            return t;
        });
        Map<String, Future<?>> futures = new LinkedHashMap<>();
        schemas.forEach(schema -> futures.put(schema, pool.submit(() -> migrateTenant(schema))));
        pool.shutdown();

        List<String> failed = new ArrayList<>();
        futures.forEach((schema, future) -> {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add(schema);
            } catch (ExecutionException e) {
                log.error("Migration of tenant schema {} failed", schema, e.getCause());
                failed.add(schema);
            }
        });
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Tenant schema migration failed for: " + failed);
        }
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
