package dev.wuoj.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

/** Every request checks its session against the account: revocation and role changes apply at once. */
class AccountSessionTest extends IntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    private long alice;

    @BeforeEach
    void data() {
        alice = fixtures.user("alice");
        fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        fixtures.problem("1002", "draft", "Draft", Difficulty.EASY, false);
    }

    private void sql(String statement) {
        fixtures.jdbc().sql(statement).param("id", alice).update();
    }

    @Test
    void newerEpochEndsTheSession() throws Exception {
        ApiSession client = signedIn("alice");
        client.perform(get("/api/submissions")).andExpect(status().isOk());

        sql("UPDATE users SET session_epoch = session_epoch + 1 WHERE id = :id");

        // A public endpoint answers as for a visitor; the session is gone for good.
        client.perform(get("/api/problems")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].status").value("NONE"));
        client.perform(get("/api/submissions")).andExpect(status().isUnauthorized());
        client.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void disablingEndsTheSession() throws Exception {
        ApiSession client = signedIn("alice");
        sql("UPDATE users SET enabled = false WHERE id = :id");
        client.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":1,\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}"))
                .andExpect(status().isUnauthorized());
        client.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void roleComesFromTheAccount() throws Exception {
        ApiSession client = signedIn("alice");
        client.perform(get("/api/problems/draft")).andExpect(status().isNotFound());

        sql("UPDATE users SET role = 'ADMIN', session_epoch = session_epoch + 1 WHERE id = :id");
        client.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        ApiSession again = signedIn("alice");
        again.perform(get("/api/auth/me")).andExpect(jsonPath("$.role").value(UserRole.ADMIN.name()));
        again.perform(get("/api/problems/draft")).andExpect(status().isOk());
    }

    @Test
    void unchangedAccountKeepsItsSession() throws Exception {
        ApiSession client = signedIn("alice");
        sql("UPDATE users SET last_login_at = now() WHERE id = :id");
        client.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
    }

    /** A session written by an older version (here: an unreadable principal) is treated as signed out. */
    @Test
    void unreadableSessionIsSignedOut() throws Exception {
        ApiSession client = signedIn("alice");
        String id = new String(Base64.getDecoder().decode(client.cookie("SESSION")), StandardCharsets.UTF_8);
        byte[] key = ("spring:session:sessions:" + id).getBytes(StandardCharsets.UTF_8);
        redis.execute(connection -> connection.hashCommands().hSet(key,
                "sessionAttr:SPRING_SECURITY_CONTEXT".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte) 0xac, (byte) 0xed, 0, 5, 's', 'r', 0, 9, 'o', 'l', 'd'}), true);

        client.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        client.perform(get("/api/problems")).andExpect(status().isOk());
    }
}
