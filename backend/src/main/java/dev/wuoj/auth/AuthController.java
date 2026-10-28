package dev.wuoj.auth;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.web.ClientAddress;
import dev.wuoj.common.web.ProblemResponses;
import dev.wuoj.user.UserAccount;
import dev.wuoj.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnRole(Role.API)
public class AuthController {

    /** The same answer for unknown users, wrong passwords and disabled accounts. */
    static final String INVALID_CREDENTIALS = "Invalid username or password.";

    public record LoginRequest(@NotEmpty @Size(max = 64) String username, @NotEmpty @Size(max = 128) String password) {}

    /** The username and password rules are checked by {@link RegistrationService}, as field errors. */
    public record RegisterRequest(@NotEmpty @Size(max = 64) String username, @NotEmpty String password) {}

    public record AuthOptions(boolean registrationEnabled) {}

    public record CurrentUser(long id, String username, UserRole role, boolean mustChangePassword) {

        static CurrentUser of(AuthUser user) {
            return new CurrentUser(user.id(), user.getUsername(), user.role(), user.mustChangePassword());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SessionStarter sessions;
    private final LoginThrottle throttle;
    private final RegistrationService registration;
    private final UserRepository users;

    public AuthController(AuthenticationManager authenticationManager, SessionStarter sessions, LoginThrottle throttle,
            RegistrationService registration, UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.throttle = throttle;
        this.registration = registration;
        this.users = users;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String address = ClientAddress.of(request);
        throttle.check(body.username(), address);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(body.username(), address);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemResponses.problem(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS));
        }

        sessions.signIn(authentication, request, response);
        AuthUser user = (AuthUser) authentication.getPrincipal();
        users.recordSignIn(user.id());
        return ResponseEntity.ok(CurrentUser.of(user));
    }

    /** Creates a user account and signs in as it, like a sign-in. */
    @PostMapping("/register")
    ResponseEntity<CurrentUser> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account = registration.register(body.username(), body.password(), ClientAddress.of(request));
        AuthUser user = AuthUser.of(account);
        sessions.signIn(user, request, response);
        users.recordSignIn(user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(CurrentUser.of(user));
    }

    @GetMapping("/options")
    AuthOptions options() {
        return new AuthOptions(registration.isOpen());
    }

    /** Also issues the {@code XSRF-TOKEN} cookie, signed in or not, so the app can send state-changing requests. */
    @GetMapping("/me")
    ResponseEntity<?> me(CsrfToken csrfToken, @Nullable Authentication authentication) {
        csrfToken.getToken();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return ResponseEntity.ok(CurrentUser.of(user));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemResponses.problem(HttpStatus.UNAUTHORIZED, "Not signed in."));
    }
}
