package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AuthApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @BeforeEach
    void accounts() {
        fixtures.user("student");
        fixtures.user("alice");
        fixtures.user("bob");
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder submit() {
        return post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":1,\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}");
    }

    @Test
    void signedOutProbeGetsATokenAnd401() throws Exception {
        session().perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void successfulSignIn() throws Exception {
        ApiSession client = session().start();
        client.perform(login("student", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.id").isNumber())
                // HttpOnly and SameSite are checked over real HTTP in SessionLifetimeTest: MockMvc's response
                // drops the attributes of cookies that Spring Session writes as raw headers.
                .andExpect(cookie().exists("SESSION"));
        client.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"));
        assertThat(users.findByUsername("student").orElseThrow().lastLoginAt()).isNotNull();
    }

    @Test
    void usernameMatchesIgnoringCase() throws Exception {
        session().start().perform(login("Student", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"));
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() throws Exception {
        MvcResult unknown = session().start().perform(login("nobody", "whatever"))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrong = session().start().perform(login("alice", "wrong"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
    }

    @Test
    void disabledAccountLooksLikeAWrongPassword() throws Exception {
        long id = users.findByUsername("bob").orElseThrow().id();
        users.update(id, users.findById(id).orElseThrow().passwordHash(), UserRole.USER, false);
        MvcResult disabled = session().start().perform(login("bob", "bob"))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrong = session().start().perform(login("alice", "wrong")).andReturn();
        assertThat(disabled.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
    }

    @Test
    void signInChangesTheSessionId() throws Exception {
        ApiSession client = session().signIn("alice", "alice");
        String before = client.cookie("SESSION");
        client.perform(login("bob", "bob")).andExpect(status().isOk());
        String after = client.cookie("SESSION");
        assertThat(after).isNotNull().isNotEqualTo(before);

        ApiSession stale = session();
        stale.setCookie("SESSION", before);
        stale.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void signInIssuesAFreshCsrfToken() throws Exception {
        ApiSession client = session().start();
        String before = client.cookie("XSRF-TOKEN");
        client.perform(login("student", "student")).andExpect(cookie().exists("XSRF-TOKEN"));
        String after = client.cookie("XSRF-TOKEN");
        assertThat(after).isNotNull().isNotEqualTo(before);

        // The token from the login response works for the next state-changing request.
        client.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void stateChangingRequestWithoutCsrfHeaderIsForbidden() throws Exception {
        ApiSession client = session().signIn("student", "student").withoutCsrfHeader();
        client.perform(submit())
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(fixtures.jdbc().sql("SELECT count(*) FROM submissions").query(Long.class).single()).isZero();
    }

    @Test
    void loginWithoutCsrfHeaderIsForbiddenAndCreatesNoSession() throws Exception {
        session().start().withoutCsrfHeader().perform(login("student", "student"))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void publicEndpointWhileSignedOut() throws Exception {
        session().perform(get("/api/problems")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWhileSignedOut() throws Exception {
        session().start().perform(submit())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void ownSubmissionsNeedASession() throws Exception {
        session().perform(get("/api/submissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        session().perform(get("/api/submissions").param("scope", "mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreForAdmins() throws Exception {
        session().perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        signedIn("student").perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void passwordsAreStoredAsAdaptiveHashes() {
        String hash = users.findByUsername("student").orElseThrow().passwordHash();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("student");
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        ApiSession client = session().signIn("student", "student");
        String sessionCookie = client.cookie("SESSION");
        client.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

        ApiSession old = session();
        old.setCookie("SESSION", sessionCookie);
        old.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void signOutWithoutSessionIsStillNoContent() throws Exception {
        session().start().perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void guessingIsSlowed() throws Exception {
        for (int i = 0; i < 5; i++) {
            session().start().perform(login("alice", "wrong" + i)).andExpect(status().isUnauthorized());
        }
        MvcResult throttled = session().start().perform(login("alice", "alice"))
                .andExpect(status().isTooManyRequests())
                .andExpect(cookie().doesNotExist("SESSION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "Too many failed sign-in attempts. Try again in ")))
                .andReturn();
        long retryAfter = Long.parseLong(throttled.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 300L);
    }

    @Test
    void otherUsersUnaffected() throws Exception {
        for (int i = 0; i < 5; i++) {
            session().start().perform(login("ALICE", "wrong"));
        }
        session().start().perform(login("alice", "alice")).andExpect(status().isTooManyRequests());
        session().start().perform(login("bob", "bob")).andExpect(status().isOk());
    }

    @Test
    void otherAddressesUnaffected() throws Exception {
        for (int i = 0; i < 5; i++) {
            session().start().perform(from("203.0.113.1", login("alice", "wrong")));
        }
        session().start().perform(from("203.0.113.1", login("alice", "alice"))).andExpect(status().isTooManyRequests());
        session().start().perform(from("203.0.113.2", login("alice", "alice"))).andExpect(status().isOk());
    }

    @Test
    void sprayingFromOneAddress() throws Exception {
        for (int i = 0; i < 50; i++) {
            session().start().perform(from("203.0.113.9", login("user" + i, "wrong")))
                    .andExpect(status().isUnauthorized());
        }
        session().start().perform(from("203.0.113.9", login("bob", "bob")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        session().start().perform(from("203.0.113.10", login("bob", "bob"))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder from(String address, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }
}
