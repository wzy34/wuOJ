package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class RegistrationApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @BeforeEach
    void alice() {
        fixtures.user("alice");
    }

    static MockHttpServletRequestBuilder register(String username, String password) {
        JsonMapper json = JsonMapper.builder().build();
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":" + json.writeValueAsString(username) + ",\"password\":"
                        + json.writeValueAsString(password) + "}");
    }

    private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }

    private long accountCount() {
        return fixtures.jdbc().sql("SELECT count(*) FROM users").query(Long.class).single();
    }

    @Test
    void newAccountIsSignedIn() throws Exception {
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        ApiSession client = session().start();
        String tokenBefore = client.cookie("XSRF-TOKEN");
        client.perform(register("carol", "correct horse battery"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(cookie().exists("SESSION"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
        assertThat(client.cookie("XSRF-TOKEN")).isNotEqualTo(tokenBefore);
        client.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("carol"));

        // The token set by the register response works for the next state-changing request.
        client.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem
                                + ",\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}"))
                .andExpect(status().isCreated());

        var stored = users.findByUsername("carol").orElseThrow();
        assertThat(stored.passwordHash()).startsWith("{bcrypt}").doesNotContain("correct horse battery");
        assertThat(stored.lastLoginAt()).isNotNull();
        session().signIn("carol", "correct horse battery");
    }

    @Test
    void takenUsername() throws Exception {
        session().start().perform(register("Alice", "correct horse battery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("username")))
                .andExpect(jsonPath("$.errors[0].message").value("is already taken"))
                .andExpect(cookie().doesNotExist("SESSION"));
        assertThat(accountCount()).isEqualTo(1);
    }

    @Test
    void reservedUsername() throws Exception {
        session().start().perform(register("Admin", "correct horse battery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("username")));
        assertThat(accountCount()).isEqualTo(1);
    }

    @Test
    void malformedUsername() throws Exception {
        session().start().performInvalid(register("bad name", "correct horse battery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("username")));
        assertThat(accountCount()).isEqualTo(1);
    }

    @Test
    void tooShortPassword() throws Exception {
        session().start().performInvalid(register("carol", "abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("password")));
        assertThat(users.findByUsername("carol")).isEmpty();
    }

    @Test
    void commonPassword() throws Exception {
        session().start().perform(register("carol", "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("password")));
        assertThat(users.findByUsername("carol")).isEmpty();
    }

    @Test
    void passwordEqualToUsername() throws Exception {
        session().start().perform(register("Carolina99", "carolina99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not be the same as the username"));
    }

    @Test
    void registrationIsOpenByDefault() throws Exception {
        session().perform(get("/api/auth/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true));
    }

    @Test
    void massRegistration() throws Exception {
        // Rejected registrations do not count.
        for (int i = 0; i < 3; i++) {
            session().start().perform(from("203.0.113.4", register("alice", "correct horse battery")))
                    .andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 5; i++) {
            session().start().perform(from("203.0.113.4", register("user" + i, "correct horse battery")))
                    .andExpect(status().isCreated());
        }
        session().start().perform(from("203.0.113.4", register("user5", "correct horse battery")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(users.findByUsername("user5")).isEmpty();
        session().start().perform(from("203.0.113.5", register("user5", "correct horse battery")))
                .andExpect(status().isCreated());
    }
}
