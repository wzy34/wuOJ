package dev.wuoj.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** After an admin reset, an account can do nothing but change its password. */
class ForcedPasswordChangeTest extends IntegrationTest {

    private long problem;

    @BeforeEach
    void heldAccount() {
        long bob = fixtures.user("bob");
        fixtures.jdbc().sql("UPDATE users SET must_change_password = true WHERE id = :id").param("id", bob).update();
        problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
    }

    private MockHttpServletRequestBuilder submit() {
        return post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemId\":" + problem + ",\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}");
    }

    @Test
    void resetAccountIsHeld() throws Exception {
        ApiSession bob = session().start();
        bob.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        bob.perform(submit())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Change your password to continue."));
        bob.perform(get("/api/problems")).andExpect(status().isForbidden());
        bob.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        bob.perform(get("/api/auth/options")).andExpect(status().isOk());
    }

    @Test
    void releasedAfterChange() throws Exception {
        ApiSession bob = signedIn("bob");
        bob.perform(PasswordChangeTest.change("bob", "correct horse battery")).andExpect(status().isNoContent());
        bob.perform(get("/api/auth/me")).andExpect(jsonPath("$.mustChangePassword").value(false));
        bob.perform(submit()).andExpect(status().isCreated());
    }

    @Test
    void csrfIsCheckedFirst() throws Exception {
        signedIn("bob").withoutCsrfHeader().perform(submit())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Missing or invalid CSRF token. Reload the page and try again."));
    }

    @Test
    void signOutStillWorks() throws Exception {
        ApiSession bob = signedIn("bob");
        bob.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        bob.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
