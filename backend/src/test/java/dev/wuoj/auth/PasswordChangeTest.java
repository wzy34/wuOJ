package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PasswordChangeTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @BeforeEach
    void alice() {
        fixtures.user("alice");
    }

    static MockHttpServletRequestBuilder change(String current, String next) {
        return post("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private int signIn(String password) throws Exception {
        return session().start().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void changeEndsOtherSessions() throws Exception {
        ApiSession first = signedIn("alice");
        ApiSession second = signedIn("alice");

        first.perform(change("alice", "correct horse battery")).andExpect(status().isNoContent());

        first.perform(get("/api/submissions")).andExpect(status().isOk());
        first.perform(get("/api/auth/me")).andExpect(jsonPath("$.username").value("alice"));
        second.perform(get("/api/submissions")).andExpect(status().isUnauthorized());
        assertThat(signIn("alice")).isEqualTo(401);
        assertThat(signIn("correct horse battery")).isEqualTo(200);
    }

    @Test
    void wrongCurrentPassword() throws Exception {
        ApiSession client = signedIn("alice");
        String before = users.findByUsername("alice").orElseThrow().passwordHash();
        client.perform(change("wrong", "correct horse battery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("currentPassword")));
        assertThat(users.findByUsername("alice").orElseThrow().passwordHash()).isEqualTo(before);
        client.perform(get("/api/auth/me")).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordCountsAsFailedSignIn() throws Exception {
        ApiSession client = signedIn("alice");
        for (int i = 0; i < 5; i++) {
            client.perform(change("wrong" + i, "correct horse battery")).andExpect(status().isBadRequest());
        }
        client.perform(change("alice", "correct horse battery")).andExpect(status().isTooManyRequests());
        assertThat(signIn("alice")).isEqualTo(429);
    }

    @Test
    void newPasswordMustFollowTheRules() throws Exception {
        ApiSession client = signedIn("alice");
        client.performInvalid(change("alice", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("newPassword")));
        client.perform(change("alice", "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("newPassword")));
    }

    @Test
    void needsASession() throws Exception {
        session().start().perform(change("alice", "correct horse battery")).andExpect(status().isUnauthorized());
    }
}
