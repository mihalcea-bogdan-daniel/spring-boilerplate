package com.polygis.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Small in-memory fixed-window counter. Good enough to blunt credential stuffing and OTP guessing on one node;
 * for a cluster keep nginx {@code limit_req} in front or swap this class for a Redis-backed implementation.
 */
public class FixedWindowRateLimiter {

    private record Window(long start, int count) {
    }

    private final int capacity;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int capacity, Duration window) {
        this(capacity, window, System::currentTimeMillis);
    }

    FixedWindowRateLimiter(int capacity, Duration window, LongSupplier clock) {
        this.capacity = capacity;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** @return 0 if the call is allowed, otherwise the number of seconds until the window resets. */
    public long tryAcquire(String key) {
        long now = clock.getAsLong();
        Window w = windows.compute(key, (k, cur) ->
                cur == null || now - cur.start >= windowMillis ? new Window(now, 1) : new Window(cur.start, cur.count + 1));
        if (w.count <= capacity) return 0;
        return Math.max(1, (w.start + windowMillis - now + 999) / 1000);
    }

    /** Drops expired windows; call periodically to bound memory. */
    public void evictExpired() {
        long now = clock.getAsLong();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis);
    }

    int size() {
        return windows.size();
    }
}
