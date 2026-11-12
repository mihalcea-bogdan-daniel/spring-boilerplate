package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.auth.OtpPurpose;
import com.polygis.support.IntegrationTestBase;

import jakarta.servlet.http.Cookie;

class ConcurrencyIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentRefreshesWithOneTokenNeverBothSucceed() throws Exception {
        String admin = provisionTenant("race-refresh");
        String host = hostOf("race-refresh");
        Cookie cookie = otpLogin(host, admin).getResponse().getCookie("refresh_token");

        List<Integer> statuses = parallel(8, () -> mvc.perform(post("/api/auth/refresh").with(host(host)).cookie(cookie))
                .andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 200).count()).isLessThanOrEqualTo(1);
        assertThat(statuses).allMatch(s -> s == 200 || s == 401);
    }

    @Test
    void parallelSignUpsCannotOvershootThePlan() throws Exception {
        String admin = provisionTenant("race-quota");
        String host = hostOf("race-quota");
        jdbc.update("update public.tenants set plan = 'tiny' where slug = 'race-quota'"); // 2 users, admin is #1
        String token = accessToken(otpLogin(host, admin));

        var counter = new java.util.concurrent.atomic.AtomicInteger();
        List<Integer> statuses = parallel(6, () -> mvc.perform(jsonPost("/api/admin/users",
                        Map.of("email", "u" + counter.incrementAndGet() + "@race-quota.test"))
                        .with(host(host)).header("Authorization", bearer(token)))
                .andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from t_race_quota.users", Integer.class)).isEqualTo(2);
    }

    @Test
    void parallelOtpGuessesRespectTheAttemptLimit() throws Exception {
        String admin = provisionTenant("race-otp");
        String host = hostOf("race-otp");
        mvc.perform(jsonPost("/api/auth/otp/request", Map.of("email", admin)).with(host(host))).andExpect(status().isAccepted());
        String code = lastOtp(admin, OtpPurpose.LOGIN);
        String wrong = code.equals("000000") ? "111111" : "000000";

        parallel(12, () -> mvc.perform(jsonPost("/api/auth/otp/verify", Map.of("email", admin, "code", wrong))
                .with(host(host))).andReturn().getResponse().getStatus());

        // max-attempts (5) is exhausted no matter how the guesses interleaved: the right code is now dead too
        mvc.perform(jsonPost("/api/auth/otp/verify", Map.of("email", admin, "code", code)).with(host(host)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select attempts from t_race_otp.one_time_tokens where purpose = 'LOGIN' "
                + "order by created_at desc limit 1", Integer.class)).isEqualTo(6);
    }

    private static <T> List<T> parallel(int n, Callable<T> task) throws Exception {
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) futures.add(pool.submit(() -> {
            start.await();
            return task.call();
        }));
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) results.add(f.get());
        pool.shutdown();
        return results;
    }
}
