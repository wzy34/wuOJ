package dev.wuoj.auth;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ProblemResponses;
import dev.wuoj.user.UserAccount;
import dev.wuoj.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks a session against its account on every request, before authorization. A session whose account is gone or
 * disabled, or whose epoch is older than the account's, is ended and the request continues as a visitor's. Otherwise
 * the authentication is rebuilt from the account as stored now, so a role is never stale, and the request's
 * {@link Viewer} is recorded. An account that must change its password can do nothing else.
 */
public class AccountFilter extends OncePerRequestFilter {

    static final String MUST_CHANGE_PASSWORD = "Change your password to continue.";

    private static final String VIEWER = Viewer.class.getName();

    /** What an account that must change its password may still do (sign-out is handled before this filter). */
    private static final RequestMatcher ALLOWED_WHILE_HELD = new OrRequestMatcher(List.of(
            PathPatternRequestMatcher.withDefaults().matcher(GET, "/api/auth/me"),
            PathPatternRequestMatcher.withDefaults().matcher(GET, "/api/auth/options"),
            PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/auth/logout"),
            PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/account/password")));

    private static final RequestMatcher API = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    private final UserRepository users;
    private final ProblemResponses problems;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AccountFilter(UserRepository users, ProblemResponses problems) {
        this.users = users;
        this.problems = problems;
    }

    /** The viewer recorded for this request; a visitor when there is no valid session. */
    public static Viewer viewer(HttpServletRequest request) {
        return request.getAttribute(VIEWER) instanceof Viewer viewer ? viewer : Viewer.visitor();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            Optional<UserAccount> account = users.findById(principal.id())
                    .filter(a -> a.enabled() && a.sessionEpoch() == principal.epoch());
            if (account.isEmpty()) {
                revoke(request);
            } else {
                UserAccount current = account.get();
                AuthUser user = AuthUser.of(current);
                SecurityContext context = contextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                contextHolder.setContext(context);
                request.setAttribute(VIEWER, new Viewer(current.id(), current.role()));
                if (current.mustChangePassword() && API.matches(request) && !ALLOWED_WHILE_HELD.matches(request)) {
                    problems.write(request, response, HttpStatus.FORBIDDEN, MUST_CHANGE_PASSWORD);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void revoke(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        contextHolder.clearContext();
    }
}
