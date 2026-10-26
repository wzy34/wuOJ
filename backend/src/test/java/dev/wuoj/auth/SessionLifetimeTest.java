package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import dev.wuoj.support.RealServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class SessionLifetimeTest extends RealServerTest {

    @Test
    void sessionCookieIsHttpOnlyAndLax() throws Exception {
        fixtures.user("student");
        HttpResponse<String> login = new Browser().signIn(port, "student");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("SESSION=").contains("HttpOnly")
                        .contains("SameSite=Lax"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"));
    }

    @Test
    void sessionSurvivesARestart() throws Exception {
        fixtures.user("student");
        Browser browser = new Browser();
        assertThat(browser.signIn(port, "student").statusCode()).isEqualTo(200);

        try (ConfigurableApplicationContext restarted = startAnotherInstance(Map.of("oj.roles", "api"))) {
            HttpResponse<String> me = browser.get(portOf(restarted), "/api/auth/me");
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.body()).contains("\"username\":\"student\"");
        }
    }

    @Test
    void idleSessionsExpire() throws Exception {
        fixtures.user("student");
        try (ConfigurableApplicationContext shortSessions = startAnotherInstance(
                Map.of("oj.roles", "api", "spring.session.timeout", "2s"))) {
            int shortPort = portOf(shortSessions);
            Browser browser = new Browser();
            assertThat(browser.signIn(shortPort, "student").statusCode()).isEqualTo(200);
            assertThat(browser.get(shortPort, "/api/auth/me").statusCode()).isEqualTo(200);

            Thread.sleep(3_000);
            assertThat(browser.get(shortPort, "/api/auth/me").statusCode()).isEqualTo(401);
        }
    }
}
