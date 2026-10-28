package dev.wuoj.auth;

import java.time.Duration;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.ratelimit.SlidingWindowLimiter;
import dev.wuoj.common.web.ForbiddenException;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.RateLimitedException;
import dev.wuoj.user.UserAccount;
import dev.wuoj.user.UserRepository;
import dev.wuoj.user.Usernames;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Self-registration. Checked in this order: registration is open (403), the client address registered too many
 * accounts recently (429), the username (400), the password (400). Only successful registrations count towards the
 * per-address limit.
 */
@Service
public class RegistrationService {

    static final String CLOSED = "Registration is closed.";

    private final UserRepository users;
    private final PasswordPolicy policy;
    private final PasswordEncoder passwordEncoder;
    private final SlidingWindowLimiter limiter;
    private final OjProperties.Registration config;

    public RegistrationService(UserRepository users, PasswordPolicy policy, PasswordEncoder passwordEncoder,
            SlidingWindowLimiter limiter, OjProperties properties) {
        this.users = users;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.config = properties.registration();
    }

    public boolean isOpen() {
        return config.enabled();
    }

    public UserAccount register(String username, String password, String address) {
        if (!config.enabled()) throw new ForbiddenException(CLOSED);
        String key = "oj:rl:register:" + address;
        Duration wait = limiter.check(key, config.perAddress(), config.window());
        if (!wait.isZero()) {
            long seconds = SlidingWindowLimiter.seconds(wait);
            throw new RateLimitedException(seconds,
                    "Too many accounts were registered from your network. Try again in " + seconds + " seconds.");
        }

        if (!Usernames.isValid(username)) {
            throw new InvalidFieldsException("username",
                    "may only contain letters, digits, '.', '_' and '-' (at most 64 characters)");
        }
        if (Usernames.isReserved(username)) throw new InvalidFieldsException("username", "is reserved");
        if (users.findByUsername(username).isPresent()) throw taken();
        policy.problem(username, password).ifPresent(problem -> {
            throw new InvalidFieldsException("password", problem);
        });

        long id;
        try {
            id = users.insert(username, passwordEncoder.encode(password), UserRole.USER, true);
        } catch (DuplicateKeyException e) {
            // Registered by someone else between the check and the insert.
            throw taken();
        }
        limiter.record(key, config.window());
        return users.findById(id).orElseThrow();
    }

    private static InvalidFieldsException taken() {
        return new InvalidFieldsException("username", "is already taken");
    }
}
