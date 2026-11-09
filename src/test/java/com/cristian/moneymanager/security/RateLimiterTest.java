package com.cristian.moneymanager.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("ip", 3, WINDOW)).isTrue();
        }
        assertThat(limiter.tryAcquire("ip", 3, WINDOW)).isFalse();
        assertThat(limiter.isLimited("ip", 3, WINDOW)).isTrue();
        assertThat(limiter.secondsUntilReset("ip", WINDOW)).isBetween(1L, 300L);
    }

    @Test
    void keysAreIndependentAndResettable() {
        RateLimiter limiter = new RateLimiter();
        limiter.record("a");
        limiter.record("a");
        assertThat(limiter.isLimited("a", 2, WINDOW)).isTrue();
        assertThat(limiter.isLimited("b", 2, WINDOW)).isFalse();

        limiter.reset("a");
        assertThat(limiter.isLimited("a", 2, WINDOW)).isFalse();
        assertThat(limiter.secondsUntilReset("a", WINDOW)).isZero();
    }

    @Test
    void oldHitsLeaveTheWindow() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        Duration shortWindow = Duration.ofMillis(50);
        assertThat(limiter.tryAcquire("ip", 1, shortWindow)).isTrue();
        assertThat(limiter.tryAcquire("ip", 1, shortWindow)).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire("ip", 1, shortWindow)).isTrue();
    }
}
