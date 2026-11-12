package com.polygis.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.tenancy.TenantJobRunner;

import lombok.extern.slf4j.Slf4j;

/** Nightly housekeeping in every tenant: one-time codes and refresh tokens that can no longer be used. */
@Slf4j
@Component
public class TokenCleanupJob {

    /** Dead refresh tokens are kept a while so reuse of a stolen, rotated token is still detected. */
    static final Duration REFRESH_TOKEN_GRACE = Duration.ofDays(30);
    static final Duration ONE_TIME_TOKEN_GRACE = Duration.ofDays(1);

    private final TenantJobRunner jobs;
    private final OneTimeTokenRepository oneTimeTokens;
    private final RefreshTokenRepository refreshTokens;
    private final TransactionTemplate tx;

    public TokenCleanupJob(TenantJobRunner jobs, OneTimeTokenRepository oneTimeTokens,
                           RefreshTokenRepository refreshTokens, TransactionTemplate tx) {
        this.jobs = jobs;
        this.oneTimeTokens = oneTimeTokens;
        this.refreshTokens = refreshTokens;
        this.tx = tx;
    }

    @Scheduled(cron = "${app.jobs.token-cleanup-cron:0 30 3 * * *}")
    public void run() {
        Instant now = Instant.now();
        int tenants = jobs.forEachTenant("token-cleanup", tenant -> tx.executeWithoutResult(s -> {
            int codes = oneTimeTokens.deleteByExpiresAtBefore(now.minus(ONE_TIME_TOKEN_GRACE));
            int tokens = refreshTokens.deleteDead(now.minus(REFRESH_TOKEN_GRACE));
            if (codes + tokens > 0) {
                log.info("Tenant {}: removed {} one-time codes and {} refresh tokens", tenant.slug(), codes, tokens);
            }
        }));
        log.debug("Token cleanup ran for {} tenant(s)", tenants);
    }
}
