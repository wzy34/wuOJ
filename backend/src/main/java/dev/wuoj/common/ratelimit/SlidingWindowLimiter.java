package dev.wuoj.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Sliding-window counters in Redis, shared by every application instance. Each event is a member of a sorted set
 * scored by Redis server time, so the window is exact and the scripts are atomic.
 */
@Component
public class SlidingWindowLimiter {

    private static final String NOW_AND_TRIM = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local window = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            """;

    private static final String WAIT = """
            local function wait()
              local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              return tonumber(oldest[2]) + window - now
            end
            """;

    /** ARGV: window ms, limit, member. Adds the event if under the limit; returns 0 or the ms to wait. */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of(NOW_AND_TRIM + WAIT + """
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[2]) then
              redis.call('ZADD', KEYS[1], now, ARGV[3])
              redis.call('PEXPIRE', KEYS[1], window)
              return 0
            end
            return wait()
            """, Long.class);

    /** ARGV: window ms, limit. Returns 0 if under the limit, or the ms until it will be. */
    private static final RedisScript<Long> CHECK = RedisScript.of(NOW_AND_TRIM + WAIT + """
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[2]) then return 0 end
            return wait()
            """, Long.class);

    /** ARGV: window ms, member. Records an event. */
    private static final RedisScript<Long> RECORD = RedisScript.of(NOW_AND_TRIM + """
            redis.call('ZADD', KEYS[1], now, ARGV[2])
            redis.call('PEXPIRE', KEYS[1], window)
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public SlidingWindowLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts an event if fewer than {@code limit} happened within the window; returns the wait, or zero. */
    public Duration tryAcquire(String key, int limit, Duration window) {
        return wait(redis.execute(ACQUIRE, List.of(key), Long.toString(window.toMillis()), Integer.toString(limit),
                UUID.randomUUID().toString()));
    }

    /** How long until fewer than {@code limit} events are in the window; zero if that is already the case. */
    public Duration check(String key, int limit, Duration window) {
        return wait(redis.execute(CHECK, List.of(key), Long.toString(window.toMillis()), Integer.toString(limit)));
    }

    public void record(String key, Duration window) {
        redis.execute(RECORD, List.of(key), Long.toString(window.toMillis()), UUID.randomUUID().toString());
    }

    private static Duration wait(Long millis) {
        return millis == null || millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    /** Whole seconds to tell a client, never zero. */
    public static long seconds(Duration wait) {
        return Math.max(1, (wait.toMillis() + 999) / 1000);
    }
}
