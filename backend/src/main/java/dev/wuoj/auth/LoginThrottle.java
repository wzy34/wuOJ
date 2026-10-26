package dev.wuoj.auth;

import java.time.Duration;
import java.util.Locale;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.ratelimit.SlidingWindowLimiter;
import dev.wuoj.common.web.RateLimitedException;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-ins per username. Deliberately not per address: a classroom often shares one NAT address, and
 * one student's typos must not lock out the whole class.
 */
@Component
public class LoginThrottle {

    private final SlidingWindowLimiter limiter;
    private final OjProperties.LoginThrottle config;

    public LoginThrottle(SlidingWindowLimiter limiter, OjProperties properties) {
        this.limiter = limiter;
        this.config = properties.loginThrottle();
    }

    /** Throws if the username has too many recent failures, even if this attempt's password is right. */
    public void check(String username) {
        Duration wait = limiter.check(key(username), config.maxFailures(), config.window());
        if (!wait.isZero()) {
            long seconds = SlidingWindowLimiter.seconds(wait);
            throw new RateLimitedException(seconds,
                    "Too many failed sign-in attempts. Try again in " + seconds + " seconds.");
        }
    }

    public void recordFailure(String username) {
        limiter.record(key(username), config.window());
    }

    private static String key(String username) {
        return "oj:rl:login:" + username.toLowerCase(Locale.ROOT);
    }
}
