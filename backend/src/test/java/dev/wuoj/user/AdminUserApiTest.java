package dev.wuoj.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminUserApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    private long adminId;
    private long alice;
    private long bob;
    private ApiSession admin;

    @BeforeEach
    void accounts() throws Exception {
        adminId = fixtures.user("admin", UserRole.ADMIN);
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        admin = signedIn("admin");
    }

    private static MockHttpServletRequestBuilder update(long id, String json) {
        return patch("/api/admin/users/" + id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder reset(long id) {
        return post("/api/admin/users/" + id + "/password-reset");
    }

    private long auditCount() {
        return fixtures.jdbc().sql("SELECT count(*) FROM audit_log").query(Long.class).single();
    }

    private int signIn(String username, String password) throws Exception {
        return session().start().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // --- List ----------------------------------------------------------------------------------------------------

    @Test
    void listNewestFirst() throws Exception {
        signedIn("alice");
        admin.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].username").value(contains("bob", "alice", "admin")))
                .andExpect(jsonPath("$.items[1].role").value("USER"))
                .andExpect(jsonPath("$.items[1].enabled").value(true))
                .andExpect(jsonPath("$.items[1].mustChangePassword").value(false))
                .andExpect(jsonPath("$.items[1].createdAt").isString())
                .andExpect(jsonPath("$.items[1].lastLoginAt").isString())
                .andExpect(jsonPath("$.items[0].lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void search() throws Exception {
        admin.perform(get("/api/admin/users").param("q", "AL"))
                .andExpect(jsonPath("$.items[*].username").value(contains("alice")));
        admin.perform(get("/api/admin/users").param("q", "%"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void filters() throws Exception {
        fixtures.jdbc().sql("UPDATE users SET enabled = false WHERE id = :id").param("id", bob).update();
        admin.perform(get("/api/admin/users").param("enabled", "false"))
                .andExpect(jsonPath("$.items[*].username").value(contains("bob")));
        admin.perform(get("/api/admin/users").param("role", "ADMIN"))
                .andExpect(jsonPath("$.items[*].username").value(contains("admin")));
        admin.performInvalid(get("/api/admin/users").param("role", "STUDENT")).andExpect(status().isBadRequest());
        admin.performInvalid(get("/api/admin/users").param("enabled", "maybe")).andExpect(status().isBadRequest());
    }

    // --- Role and enabled ----------------------------------------------------------------------------------------

    @Test
    void disableAnAccount() throws Exception {
        ApiSession bobSession = signedIn("bob");
        admin.perform(update(bob, "{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.enabled").value(false));
        bobSession.perform(get("/api/submissions")).andExpect(status().isUnauthorized());
        assertThat(signIn("bob", "bob")).isEqualTo(401);

        admin.perform(update(bob, "{\"enabled\":true}")).andExpect(jsonPath("$.enabled").value(true));
        assertThat(signIn("bob", "bob")).isEqualTo(200);
    }

    @Test
    void promoteAUser() throws Exception {
        ApiSession aliceSession = signedIn("alice");
        admin.perform(update(alice, "{\"role\":\"ADMIN\"}")).andExpect(jsonPath("$.role").value("ADMIN"));
        aliceSession.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        signedIn("alice").perform(get("/api/admin/users")).andExpect(status().isOk());
    }

    @Test
    void demotedAdmin() throws Exception {
        long other = fixtures.user("other-admin", UserRole.ADMIN);
        ApiSession otherSession = signedIn("other-admin");
        admin.perform(update(other, "{\"role\":\"USER\"}")).andExpect(status().isOk());
        otherSession.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        signedIn("other-admin").perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    void changingOneselfIsRefused() throws Exception {
        admin.perform(update(adminId, "{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You cannot change your own role or disable your own account."));
        admin.perform(update(adminId, "{\"enabled\":false}")).andExpect(status().isConflict());
        assertThat(users.findById(adminId).orElseThrow()).extracting("role", "enabled")
                .containsExactly(UserRole.ADMIN, true);
        assertThat(auditCount()).isZero();
        admin.perform(get("/api/admin/users")).andExpect(status().isOk());
    }

    @Test
    void noOpChangesNothing() throws Exception {
        ApiSession aliceSession = signedIn("alice");
        admin.perform(update(alice, "{\"role\":\"USER\",\"enabled\":true}")).andExpect(status().isOk());
        aliceSession.perform(get("/api/auth/me")).andExpect(status().isOk());
        assertThat(auditCount()).isZero();
    }

    @Test
    void invalidUpdates() throws Exception {
        admin.perform(update(999999, "{\"enabled\":false}")).andExpect(status().isNotFound());
        admin.performInvalid(update(alice, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("role")));
        admin.performInvalid(update(alice, "{\"role\":\"STUDENT\"}")).andExpect(status().isBadRequest());
    }

    // --- Password reset ------------------------------------------------------------------------------------------

    @Test
    void resetAForgottenPassword() throws Exception {
        ApiSession bobSession = signedIn("bob");
        String body = admin.perform(reset(bob)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");
        assertThat(temporary).hasSize(16).matches("[A-HJ-NP-Za-km-z2-9]+");

        bobSession.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        assertThat(signIn("bob", "bob")).isEqualTo(401);
        session().start().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"" + temporary + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        String details = fixtures.jdbc().sql("SELECT coalesce(details::text, '') FROM audit_log")
                .query(String.class).single();
        assertThat(details).doesNotContain(temporary);
        assertThat(users.findById(bob).orElseThrow().passwordHash()).doesNotContain(temporary);
    }

    @Test
    void resettingOneselfIsRefused() throws Exception {
        admin.perform(reset(adminId)).andExpect(status().isConflict());
        assertThat(signIn("admin", "admin")).isEqualTo(200);
        admin.perform(reset(999999)).andExpect(status().isNotFound());
    }

    // --- Audit ---------------------------------------------------------------------------------------------------

    @Test
    void actionsAreRecorded() throws Exception {
        admin.perform(update(alice, "{\"role\":\"ADMIN\"}"));
        admin.perform(reset(bob));
        admin.perform(update(bob, "{\"enabled\":false}"));
        admin.perform(get("/api/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].action").value(contains("DISABLED", "PASSWORD_RESET", "ROLE_CHANGED")))
                .andExpect(jsonPath("$.items[0].actor.username").value("admin"))
                .andExpect(jsonPath("$.items[0].target.username").value("bob"))
                .andExpect(jsonPath("$.items[0].fromRole").doesNotExist())
                .andExpect(jsonPath("$.items[2].fromRole").value("USER"))
                .andExpect(jsonPath("$.items[2].toRole").value("ADMIN"))
                .andExpect(jsonPath("$.items[*].createdAt", not(hasItem((Object) null))));
    }

    @Test
    void refusedChangeIsNotRecorded() throws Exception {
        admin.perform(update(adminId, "{\"role\":\"USER\"}")).andExpect(status().isConflict());
        admin.perform(get("/api/admin/audit")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void usersCannotSeeTheAudit() throws Exception {
        signedIn("alice").perform(get("/api/admin/audit")).andExpect(status().isForbidden());
    }
}
