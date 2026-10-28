package dev.wuoj.auth;

import static jakarta.servlet.DispatcherType.ASYNC;
import static jakarta.servlet.DispatcherType.ERROR;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import java.util.List;

import dev.wuoj.common.web.ProblemResponses;
import dev.wuoj.user.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Cookie sessions (stored in Redis by Spring Session) with SPA-style CSRF: the {@code XSRF-TOKEN} cookie is readable
 * by the web app, which echoes it in {@code X-XSRF-TOKEN}. Sign-in is a JSON endpoint ({@link AuthController}), not
 * form login, and every failure is a problem-details body rather than a redirect. Reading problems, everyone's
 * submissions and profiles needs no session; every session is checked against its account by {@link AccountFilter}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, CookieCsrfTokenRepository csrfTokens,
            SecurityContextRepository securityContexts, ProblemResponses problems, UserRepository users)
            throws Exception {
        http.securityMatcher("/api/**", "/actuator/**")
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(securityContexts))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(ASYNC, ERROR).permitAll()
                        .requestMatchers(POST, "/api/auth/login", "/api/auth/logout", "/api/auth/register")
                        .permitAll()
                        .requestMatchers(GET, "/api/auth/me", "/api/auth/options", "/actuator/health",
                                "/actuator/health/**").permitAll()
                        .requestMatchers(GET, "/api/submissions/*/events").authenticated()
                        // scope=mine needs a session too; the submission controller enforces it.
                        .requestMatchers(GET, "/api/problems", "/api/problems/*", "/api/submissions",
                                "/api/submissions/*", "/api/users/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new AccountFilter(users, problems), AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) -> problems.write(request, response,
                                HttpStatus.UNAUTHORIZED, "Sign in to continue."))
                        .accessDeniedHandler((request, response, e) -> problems.write(request, response,
                                HttpStatus.FORBIDDEN, e instanceof CsrfException
                                        ? "Missing or invalid CSRF token. Reload the page and try again."
                                        : "You are not allowed to do this.")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** What form login would do on success: a new session id, and a new CSRF token. */
    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(CookieCsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    AuthenticationManager authenticationManager(AccountUserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
