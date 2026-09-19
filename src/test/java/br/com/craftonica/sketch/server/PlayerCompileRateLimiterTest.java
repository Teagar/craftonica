package br.com.craftonica.sketch.server;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlayerCompileRateLimiterTest {
    @Test
    public void allowsBurstTwoAndRefillsSixPerMinute() {
        MutableClock clock = new MutableClock();
        PlayerCompileRateLimiter limiter = new PlayerCompileRateLimiter(clock);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player));
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
        clock.now += 9_999_999_999L;
        assertFalse(limiter.tryAcquire(player));
        clock.now += 2L;
        assertTrue(limiter.tryAcquire(player));
    }

    @Test
    public void playersHaveIndependentBuckets() {
        PlayerCompileRateLimiter limiter = new PlayerCompileRateLimiter(new MutableClock());
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(first));
        assertFalse(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(second));
    }

    @Test
    public void removingPlayerDropsTheirBucket() {
        PlayerCompileRateLimiter limiter = new PlayerCompileRateLimiter(new MutableClock());
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player));
        assertTrue(limiter.tryAcquire(player));
        assertFalse(limiter.tryAcquire(player));
        limiter.remove(player);
        assertTrue(limiter.tryAcquire(player));
    }

    private static final class MutableClock implements PlayerCompileRateLimiter.Clock {
        long now;
        @Override public long nanoTime() { return now; }
    }
}
