package com.polygis.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs background work once per active tenant, with that tenant bound to the thread (so repositories hit the
 * right schema). A PostgreSQL advisory lock makes each job run on only one node at a time - no extra
 * infrastructure needed. One failing tenant never stops the others.
 *
 * <pre>
 * &#64;Scheduled(cron = "0 0 3 * * *")
 * void nightly() { jobs.forEachTenant("report-digest", tenant -> digestService.send()); }
 * </pre>
 */
@Slf4j
@Component
public class TenantJobRunner {

    private final TenantRegistry registry;
    private final DataSource dataSource;

    public TenantJobRunner(TenantRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.dataSource = dataSource;
    }

    /** @return number of tenants processed successfully, or -1 if another node holds the job lock */
    public int forEachTenant(String jobName, Consumer<TenantInfo> work) {
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection, jobName)) {
                log.debug("Job {} is already running on another node", jobName);
                return -1;
            }
            try {
                int ok = 0;
                for (TenantInfo tenant : registry.all()) {
                    if (!tenant.isActive()) continue;
                    try {
                        TenantContext.runAs(tenant, () -> work.accept(tenant));
                        ok++;
                    } catch (RuntimeException e) {
                        log.error("Job {} failed for tenant {}", jobName, tenant.slug(), e);
                    }
                }
                return ok;
            } finally {
                unlock(lockConnection, jobName);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not acquire lock for job " + jobName, e);
        }
    }

    private static boolean tryLock(Connection c, String jobName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select pg_try_advisory_lock(hashtext(?))")) {
            ps.setString(1, "job:" + jobName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection c, String jobName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select pg_advisory_unlock(hashtext(?))")) {
            ps.setString(1, "job:" + jobName);
            ps.execute();
        }
    }
}
