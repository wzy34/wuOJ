package dev.wuoj.user;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.problem.UserProblemStatusRepository;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Without the worker role, so stored submissions stay as the test leaves them. */
@TestPropertySource(properties = "oj.roles=api")
class ProfileApiTest extends IntegrationTest {

    @Autowired
    UserProblemStatusRepository statuses;

    @Autowired
    SubmissionRepository submissions;

    private long alice;
    private long aPlusB;
    private long sieve;
    private long hidden;

    @BeforeEach
    void data() {
        alice = fixtures.user("alice");
        fixtures.user("admin", UserRole.ADMIN);
        aPlusB = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        sieve = fixtures.problem("1002", "prime-sieve", "Prime Sieve", Difficulty.MEDIUM, true);
        hidden = fixtures.problem("1003", "draft", "Draft", Difficulty.HARD, false);
    }

    private void submission(long problem, String verdict) {
        long id = submissions.insert(alice, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = :v WHERE id = :id")
                .param("v", verdict).param("id", id).update();
    }

    @Test
    void profileOfAnActiveUser() throws Exception {
        submission(aPlusB, "WA");
        submission(aPlusB, "AC");
        submission(sieve, "TLE");
        statuses.markSolved(alice, aPlusB);
        statuses.markAttempted(alice, sieve);

        session().perform(get("/api/users/Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.joinedAt").isString())
                .andExpect(jsonPath("$.solvedCount").value(1))
                .andExpect(jsonPath("$.attemptedCount").value(1))
                .andExpect(jsonPath("$.submissionCount").value(3))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.solved[*].slug").value(contains("a-plus-b")))
                .andExpect(jsonPath("$.solved[0].displayId").value("1001"));
    }

    @Test
    void hiddenProblemsNotShown() throws Exception {
        statuses.markSolved(alice, aPlusB);
        statuses.markSolved(alice, hidden);
        submission(hidden, "AC");
        signedIn("admin").perform(get("/api/users/alice"))
                .andExpect(jsonPath("$.solvedCount").value(1))
                .andExpect(jsonPath("$.submissionCount").value(0))
                .andExpect(jsonPath("$.solved[*].slug").value(contains("a-plus-b")));
    }

    @Test
    void solvedMostRecentlyFirst() throws Exception {
        statuses.markSolved(alice, aPlusB);
        fixtures.jdbc().sql("UPDATE user_problem_status SET first_solved_at = now() - interval '1 day'").update();
        statuses.markSolved(alice, sieve);
        session().perform(get("/api/users/alice"))
                .andExpect(jsonPath("$.solved[*].slug").value(contains("prime-sieve", "a-plus-b")));
    }

    @Test
    void disabledAccount() throws Exception {
        fixtures.user("bob");
        fixtures.jdbc().sql("UPDATE users SET enabled = false WHERE username = 'alice'").update();
        signedIn("bob").perform(get("/api/users/alice")).andExpect(status().isNotFound());
        session().perform(get("/api/users/alice")).andExpect(status().isNotFound());
        signedIn("admin").perform(get("/api/users/alice")).andExpect(status().isOk());
    }

    @Test
    void unknownUser() throws Exception {
        session().perform(get("/api/users/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found."));
    }
}
