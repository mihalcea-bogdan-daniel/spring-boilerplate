package com.polygis.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FixedWindowRateLimiterTest {

    @Test
    void allowsCapacityThenBlocksUntilWindowResets() {
        AtomicLong now = new AtomicLong(1_000);
        var limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(60), now::get);

        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("acme|1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("acme|1.2.3.4")).isEqualTo(60);
        assertThat(limiter.tryAcquire("acme|5.6.7.8")).isZero();   // other client unaffected
        assertThat(limiter.tryAcquire("globex|1.2.3.4")).isZero(); // other tenant unaffected

        now.addAndGet(60_000);
        assertThat(limiter.tryAcquire("acme|1.2.3.4")).isZero();
        now.addAndGet(60_000);
        limiter.evictExpired();
        assertThat(limiter.size()).isZero();
    }

    @Test
    void filterAnswers429WithRetryAfterOnlyForAuthPosts() throws Exception {
        var filter = new AuthRateLimitFilter(new FixedWindowRateLimiter(1, Duration.ofMinutes(1)));

        var first = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), first, new MockFilterChain());
        assertThat(first.getStatus()).isEqualTo(200);

        var second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotNull();

        var other = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/me"), other, new MockFilterChain());
        assertThat(other.getStatus()).isEqualTo(200);
    }
}
