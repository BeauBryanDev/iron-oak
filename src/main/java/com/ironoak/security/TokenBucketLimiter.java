package com.ironoak.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Per-key token bucket: a key may burst up to {@code capacity} requests, then
 * is refilled
 * evenly so that {@code capacity} requests are allowed per minute. State is in
 * memory and
 * bounded: when {@code maxKeys} is reached the least recently used key is
 * forgotten, so a
 * flood of distinct keys cannot exhaust the heap.
 */
public class TokenBucketLimiter {

    /** Outcome of one attempt; retryAfterSeconds is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final long MINUTE_NANOS = 60_000_000_000L;

    private final int capacity;
    private final LongSupplier nanoClock;
    private final Map<String, double[]> buckets; // key -> {tokens, lastRefillNanos}

    public TokenBucketLimiter(int requestsPerMinute, int maxKeys) {
        this(requestsPerMinute, maxKeys, System::nanoTime);
    }

    public TokenBucketLimiter(int requestsPerMinute,
            int maxKeys,
            LongSupplier nanoClock) {

        this.capacity = Math.max(1, requestsPerMinute);
        this.nanoClock = nanoClock;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                return size() > Math.max(1, maxKeys);
            }
        };
    }

    public synchronized Decision tryAcquire(String key) {

        long now = nanoClock.getAsLong();
        double[] bucket = buckets.computeIfAbsent(key, k -> new double[] { capacity, now });
        double refill = (now - bucket[1]) * (capacity / (double) MINUTE_NANOS);

        bucket[0] = Math.min(capacity, bucket[0] + Math.max(0, refill));
        bucket[1] = now;

        if (bucket[0] >= 1) {
            bucket[0] -= 1;

            return new Decision(true, 0);
        }
        double missingTokens = 1 - bucket[0];

        long waitNanos = (long) Math.ceil(missingTokens * (MINUTE_NANOS / (double) capacity));

        return new Decision(false, Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L));
    }
}
