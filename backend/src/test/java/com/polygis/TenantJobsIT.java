package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.auth.TokenCleanupJob;
import com.polygis.support.IntegrationTestBase;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantJobRunner;

class TenantJobsIT extends IntegrationTestBase {

    @Autowired
    TenantJobRunner jobs;
    @Autowired
    TokenCleanupJob cleanup;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void jobRunsInEveryTenantSchemaAndSurvivesOneFailingTenant() throws Exception {
        provisionTenant("job-a");
        provisionTenant("job-b");

        List<String> schemas = new ArrayList<>();
        int ok = jobs.forEachTenant("test-job", tenant -> {
            schemas.add(TenantContext.currentSchema());
            if (tenant.slug().equals("job-a")) throw new IllegalStateException("boom");
        });

        assertThat(schemas).contains("t_job_a", "t_job_b", "t_platform");
        assertThat(ok).isEqualTo(schemas.size() - 1);
        assertThat(TenantContext.current()).isEmpty(); // nothing leaks onto the calling thread
    }

    @Test
    void cleanupRemovesOnlyDeadTokens() throws Exception {
        String admin = provisionTenant("job-clean");
        otpLogin(hostOf("job-clean"), admin); // leaves a consumed login code and a live refresh token
        String userId = jdbc.queryForObject("select id::text from t_job_clean.users limit 1", String.class);

        jdbc.update("insert into t_job_clean.one_time_tokens (user_id, purpose, code_hash, expires_at) "
                + "values (?::uuid, 'LOGIN', 'old', now() - interval '3 days')", userId);
        jdbc.update("insert into t_job_clean.refresh_tokens (user_id, token_hash, family_id, expires_at) "
                + "values (?::uuid, 'dead', gen_random_uuid(), now() - interval '60 days')", userId);

        cleanup.run();

        assertThat(jdbc.queryForObject("select count(*) from t_job_clean.one_time_tokens where code_hash = 'old'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from t_job_clean.refresh_tokens where token_hash = 'dead'",
                Integer.class)).isZero();
        // the live session and the recent code are untouched
        assertThat(jdbc.queryForObject("select count(*) from t_job_clean.refresh_tokens", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from t_job_clean.one_time_tokens", Integer.class)).isEqualTo(1);
    }
}
