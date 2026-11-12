package com.polygis.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(prefix = "app.rate-limit.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    private final FixedWindowRateLimiter limiter;

    public RateLimitConfig(RateLimitProperties props) {
        this.limiter = new FixedWindowRateLimiter(props.capacity(), props.window());
    }

    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter() {
        var reg = new FilterRegistrationBean<>(new AuthRateLimitFilter(limiter));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20); // after TenantResolutionFilter (+10), before Spring Security
        reg.addUrlPatterns("/api/auth/*");
        return reg;
    }

    @Scheduled(fixedDelayString = "PT5M")
    void evict() {
        limiter.evictExpired();
    }
}
