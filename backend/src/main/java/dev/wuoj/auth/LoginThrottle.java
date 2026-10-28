package dev.wuoj.auth;

import java.time.Duration;
import java.util.Locale;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.ratelimit.SlidingWindowLimiter;
import dev.wuoj.common.web.RateLimitedException;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-ins per username and client address together, and per client address alone. Not per username
 * alone: on a public site that would let anyone lock any account out.
 */
@Component
public class LoginThrottle {

    private final SlidingWindowLimiter limiter;
    private final OjProperties.LoginThrottle config;

    public LoginThrottle(SlidingWindowLimiter limiter, OjProperties properties) {
        this.limiter = limiter;
        this.config = properties.loginThrottle();
    }

    /** Throws if either limit is reached, even if this attempt's password is right. */
    public void check(String username, String address) {
        Duration wait = max(limiter.check(userKey(username, address), config.maxFailures(), config.window()),
                limiter.check(addressKey(address), config.maxFailuresPerAddress(), config.window()));
        if (!wait.isZero()) {
            long seconds = SlidingWindowLimiter.seconds(wait);
            throw new RateLimitedException(seconds,
                    "Too many failed sign-in attempts. Try again in " + seconds + " seconds.");
        }
    }

    public void recordFailure(String username, String address) {
        limiter.record(userKey(username, address), config.window());
        limiter.record(addressKey(address), config.window());
    }

    private static String userKey(String username, String address) {
        return "oj:rl:login:" + username.toLowerCase(Locale.ROOT) + ":" + address;
    }

    private static String addressKey(String address) {
        return "oj:rl:login-ip:" + address;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
