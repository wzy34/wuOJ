package dev.wuoj.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

/** Puts a user into the session: after sign-in and registration, and after a password change. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SessionStarter {

    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContexts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    SessionStarter(SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository securityContexts) {
        this.sessionStrategy = sessionStrategy;
        this.securityContexts = securityContexts;
    }

    /** What form login does on success: a new session id, a fresh CSRF token, and the user in the session. */
    void signIn(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessionStrategy.onAuthentication(authentication, request, response);
        save(authentication, request, response);
        // Loading the token replaced by the session strategy writes it to the response as a fresh cookie.
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
    }

    void signIn(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        signIn(authenticated(user), request, response);
    }

    /** Replaces the user stored in the current session, keeping the session (e.g. with a new epoch). */
    void replace(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        save(authenticated(user), request, response);
    }

    private void save(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
    }

    private static Authentication authenticated(AuthUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
