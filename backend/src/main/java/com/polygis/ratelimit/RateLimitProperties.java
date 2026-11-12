package com.polygis.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled  master switch
 * @param capacity requests allowed per window, per tenant + client IP, on POST /api/auth/**
 * @param window   length of the window
 */
@ConfigurationProperties(prefix = "app.rate-limit.auth")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("30") int capacity,
                                  @DefaultValue("1m") Duration window) {
}
