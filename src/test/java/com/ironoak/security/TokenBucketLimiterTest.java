package com.ironoak.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test: no Spring, no database. */
class TokenBucketLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsABurstThenRefusesAndTellsWhenToRetry() {
        AtomicLong clock = new AtomicLong();
        TokenBucketLimiter limiter = new TokenBucketLimiter(6, 100, clock::get);

        for (int i = 0; i < 6; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        }
        TokenBucketLimiter.Decision refused = limiter.tryAcquire("1.1.1.1");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 10L); // 6/min = one token per 10s
    }

    @Test
    void refillsOverTimeButNeverBeyondTheCapacity() {
        AtomicLong clock = new AtomicLong();
        TokenBucketLimiter limiter = new TokenBucketLimiter(6, 100, clock::get);
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.addAndGet(10 * SECOND);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.addAndGet(3600 * SECOND); // an hour idle still only gives one full bucket
        int allowed = 0;
        for (int i = 0; i < 20; i++) {
            if (limiter.tryAcquire("a").allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(6);
    }

    @Test
    void keysAreIndependent() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(1, 100, new AtomicLong()::get);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void memoryStaysBoundedByForgettingTheLeastRecentlyUsedKey() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(1, 3, new AtomicLong()::get);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c");
        limiter.tryAcquire("d"); // evicts "a", which therefore starts over with a full bucket
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("d").allowed()).isFalse(); // recent keys are remembered
    }
}
