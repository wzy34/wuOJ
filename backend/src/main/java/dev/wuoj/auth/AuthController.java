package dev.wuoj.auth;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.web.ProblemResponses;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
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

    public record CurrentUser(long id, String username, UserRole role) {

        static CurrentUser of(AuthUser user) {
            return new CurrentUser(user.id(), user.getUsername(), user.role());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContexts;
    private final LoginThrottle throttle;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository securityContexts, LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.securityContexts = securityContexts;
        this.throttle = throttle;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        throttle.check(body.username());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(body.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemResponses.problem(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS));
        }

        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        renderCsrfToken(request);
        return ResponseEntity.ok(CurrentUser.of((AuthUser) authentication.getPrincipal()));
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

    /** Loading the token replaced at sign-in writes it to the response as a fresh cookie. */
    private static void renderCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
    }
}
