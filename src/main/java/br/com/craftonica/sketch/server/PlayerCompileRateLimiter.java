package br.com.craftonica.sketch.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Token bucket: two immediate compiles, then one token every ten seconds. */
public final class PlayerCompileRateLimiter {
    static final double BURST = 2.0;
    static final double TOKENS_PER_NANO = 6.0 / 60.0 / 1_000_000_000.0;

    public interface Clock { long nanoTime(); }

    private final Clock clock;
    private final Map<UUID, Bucket> buckets = new HashMap<UUID, Bucket>();

    public PlayerCompileRateLimiter() {
        this(new Clock() { @Override public long nanoTime() { return System.nanoTime(); } });
    }

    public PlayerCompileRateLimiter(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("Clock is required");
        this.clock = clock;
    }

    public boolean tryAcquire(UUID playerId) {
        if (playerId == null) return false;
        long now = clock.nanoTime();
        Bucket bucket = buckets.get(playerId);
        if (bucket == null) {
            bucket = new Bucket(BURST, now);
            buckets.put(playerId, bucket);
        } else if (now > bucket.updated) {
            bucket.tokens = Math.min(BURST, bucket.tokens + (now - bucket.updated) * TOKENS_PER_NANO);
            bucket.updated = now;
        }
        if (bucket.tokens < 1.0) return false;
        bucket.tokens -= 1.0;
        return true;
    }

    public void clear() { buckets.clear(); }

    private static final class Bucket {
        double tokens;
        long updated;
        Bucket(double tokens, long updated) { this.tokens = tokens; this.updated = updated; }
    }
}
