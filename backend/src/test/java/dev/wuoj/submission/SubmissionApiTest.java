package dev.wuoj.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Without the worker role, so submissions stay as created unless a test changes them. */
@TestPropertySource(properties = "oj.roles=api")
class SubmissionApiTest extends IntegrationTest {

    @Autowired
    SubmissionRepository submissions;

    private long alice;
    private long bob;
    private long aPlusB;
    private long hidden;
    private ApiSession aliceSession;

    @BeforeEach
    void setUp() throws Exception {
        alice = fixtures.user("alice");
        bob = fixtures.user("bob");
        fixtures.user("admin", UserRole.ADMIN);
        aPlusB = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        hidden = fixtures.problem("1002", "draft", "Draft", Difficulty.EASY, false);
        aliceSession = signedIn("alice");
    }

    private static MockHttpServletRequestBuilder submit(Object problemId, String language, String source) {
        String json = "{\"problemId\":" + problemId + ",\"language\":\"" + language + "\",\"sourceCode\":"
                + quote(source) + "}";
        return post("/api/submissions").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockHttpServletRequestBuilder submit(String source) {
        return submit(aPlusB, "CPP17", source);
    }

    private static String quote(String text) {
        return JsonMapper.builder().build().writeValueAsString(text);
    }

    private long countSubmissions() {
        return fixtures.jdbc().sql("SELECT count(*) FROM submissions").query(Long.class).single();
    }

    private void finish(long id, String verdict) {
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = :v, time_ms = 3, memory_kb = 3000 "
                + "WHERE id = :id").param("v", verdict).param("id", id).update();
    }

    private void finishAll() {
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'AC' WHERE status <> 'FINISHED'")
                .update();
    }

    private long idOf(MvcResult result) throws Exception {
        return Long.parseLong(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")
                .toString());
    }

    // --- Creating ------------------------------------------------------------------------------------------------

    @Test
    void acceptedForJudging() throws Exception {
        MvcResult result = aliceSession.perform(submit("int main() { return 0; }\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sourceCode").value("int main() { return 0; }\n"))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.problem.slug").value("a-plus-b"))
                .andExpect(jsonPath("$.language").value("CPP17"))
                .andExpect(jsonPath("$.testResults", hasSize(0)))
                .andExpect(jsonPath("$.verdict").doesNotExist())
                .andReturn();
        long id = idOf(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/submissions/" + id);
        aliceSession.perform(get("/api/problems/a-plus-b")).andExpect(jsonPath("$.status").value("ATTEMPTED"));
    }

    @Test
    void queuedAfterCommitAndConfirmed() throws Exception {
        long id = idOf(aliceSession.perform(submit("int main(){}")).andReturn());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(fixtures.jdbc()
                .sql("SELECT enqueued_at IS NOT NULL FROM submissions WHERE id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue());
    }

    @Test
    void blankSource() throws Exception {
        aliceSession.perform(submit("  \n\t "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("sourceCode")));
        assertThat(countSubmissions()).isZero();
    }

    @Test
    void oversizedSource() throws Exception {
        aliceSession.performInvalid(submit("x".repeat(65_537)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("sourceCode")));
        aliceSession.perform(submit("int main(){}" + "/".repeat(65_536 - 12))).andExpect(status().isCreated());
    }

    @Test
    void sourceLengthCountsUtf16Units() throws Exception {
        // 32,768 emoji are 65,536 UTF-16 code units (the web app's limit) but 131,072 bytes of UTF-8.
        aliceSession.perform(submit("😀".repeat(32_768))).andExpect(status().isCreated());
    }

    @Test
    void unknownLanguageAndMissingProblem() throws Exception {
        aliceSession.performInvalid(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"JAVA\",\"sourceCode\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "problemId", "language")));
    }

    @Test
    void hiddenProblem() throws Exception {
        aliceSession.perform(submit(hidden, "CPP17", "int main(){}"))
                .andExpect(status().isNotFound());
        aliceSession.perform(submit(99999, "CPP17", "int main(){}")).andExpect(status().isNotFound());
        assertThat(countSubmissions()).isZero();
        signedIn("admin").perform(submit(hidden, "CPP17", "int main(){}")).andExpect(status().isCreated());
    }

    @Test
    void burstOfSubmissions() throws Exception {
        for (int i = 0; i < 3; i++) {
            aliceSession.perform(submit("int main(){}")).andExpect(status().isCreated());
            finishAll();
        }
        MvcResult limited = aliceSession.perform(submit("int main(){}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Too many submissions.")))
                .andReturn();
        assertThat(Long.parseLong(limited.getResponse().getHeader("Retry-After"))).isBetween(1L, 10L);
        assertThat(countSubmissions()).isEqualTo(3);
    }

    @Test
    void tooManyUnfinished() throws Exception {
        aliceSession.perform(submit("int main(){}")).andExpect(status().isCreated());
        aliceSession.perform(submit("int main(){}")).andExpect(status().isCreated());
        aliceSession.perform(submit("int main(){}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"));
        // The unfinished limit does not use up the window: once one finishes, the third submission is accepted.
        finish(submissions.list(new dev.wuoj.common.domain.Viewer(alice, UserRole.STUDENT), true, null, null,
                new dev.wuoj.common.web.PageRequest(1, 1)).items().getFirst().id(), "AC");
        aliceSession.perform(submit("int main(){}")).andExpect(status().isCreated());
    }

    @Test
    void limitsArePerUser() throws Exception {
        aliceSession.perform(submit("int main(){}"));
        aliceSession.perform(submit("int main(){}"));
        aliceSession.perform(submit("int main(){}")).andExpect(status().isTooManyRequests());
        signedIn("bob").perform(submit("int main(){}")).andExpect(status().isCreated());
    }

    @Test
    void rejectedRequestsDoNotCount() throws Exception {
        for (int i = 0; i < 5; i++) aliceSession.perform(submit(" ")).andExpect(status().isBadRequest());
        for (int i = 0; i < 3; i++) {
            aliceSession.perform(submit("int main(){}")).andExpect(status().isCreated());
            finishAll();
        }
    }

    // --- Listing -------------------------------------------------------------------------------------------------

    private long stored(long user, long problem, String verdict) {
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        if (verdict != null) finish(id, verdict);
        return id;
    }

    @Test
    void defaultScopeIsMineNewestFirst() throws Exception {
        long a1 = stored(alice, aPlusB, "WA");
        stored(bob, aPlusB, "AC");
        long a2 = stored(alice, aPlusB, null);
        aliceSession.perform(get("/api/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains((int) a2, (int) a1)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[1].verdict").value("WA"))
                .andExpect(jsonPath("$.items[1].timeMs").value(3))
                .andExpect(jsonPath("$.items[0].sourceCode").doesNotExist());
    }

    @Test
    void sharedFilterLink() throws Exception {
        for (int i = 0; i < 25; i++) stored(i % 2 == 0 ? alice : bob, aPlusB, "AC");
        stored(alice, aPlusB, "WA");
        aliceSession.perform(get("/api/submissions").param("scope", "all").param("verdict", "AC").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.items[*].user.username", hasItem("bob")))
                .andExpect(jsonPath("$.items[*].verdict").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("AC"))));
    }

    @Test
    void problemFilter() throws Exception {
        long other = fixtures.problem("1003", "other", "Other", Difficulty.EASY, true);
        stored(alice, aPlusB, "AC");
        long onOther = stored(alice, other, "AC");
        aliceSession.perform(get("/api/submissions").param("problem", "other"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) onOther)));
        aliceSession.perform(get("/api/submissions").param("problem", "no-such-problem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void invalidFilters() throws Exception {
        aliceSession.performInvalid(get("/api/submissions").param("scope", "everyone"))
                .andExpect(status().isBadRequest());
        aliceSession.performInvalid(get("/api/submissions").param("verdict", "OK"))
                .andExpect(status().isBadRequest());
        aliceSession.performInvalid(get("/api/submissions").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenProblemSubmissionsAreOnlyListedForAdmins() throws Exception {
        long onHidden = stored(alice, hidden, "AC");
        long visible = stored(alice, aPlusB, "AC");
        aliceSession.perform(get("/api/submissions").param("scope", "all"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) visible)));
        aliceSession.perform(get("/api/submissions/" + onHidden)).andExpect(status().isNotFound());
        signedIn("admin").perform(get("/api/submissions").param("scope", "all"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) visible, (int) onHidden)));
    }

    // --- Detail --------------------------------------------------------------------------------------------------

    private long compileError(long user) {
        long id = submissions.insert(user, aPlusB, Language.CPP17, "int main() { return 0 }", Fixtures.VERSION);
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'CE', "
                + "compile_output = 'main.cpp:1:24: error: expected '';''' WHERE id = :id").param("id", id).update();
        return id;
    }

    @Test
    void ownerSeesCodeAndCompilerOutput() throws Exception {
        long id = compileError(alice);
        aliceSession.perform(get("/api/submissions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CE"))
                .andExpect(jsonPath("$.sourceCode").value("int main() { return 0 }"))
                .andExpect(jsonPath("$.compileOutput").value("main.cpp:1:24: error: expected ';'"))
                .andExpect(jsonPath("$.timeMs").doesNotExist())
                .andExpect(jsonPath("$.memoryKb").doesNotExist());
    }

    @Test
    void othersDoNot() throws Exception {
        long id = compileError(alice);
        signedIn("bob").perform(get("/api/submissions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CE"))
                .andExpect(jsonPath("$.sourceCode").doesNotExist())
                .andExpect(jsonPath("$.compileOutput").doesNotExist());
    }

    @Test
    void testResultsInOrderAndProgress() throws Exception {
        long id = stored(alice, aPlusB, null);
        fixtures.jdbc().sql("UPDATE submissions SET status = 'RUNNING', current_test = 3, total_tests = 5 "
                + "WHERE id = :id").param("id", id).update();
        for (int t = 2; t >= 1; t--) {
            fixtures.jdbc().sql("INSERT INTO submission_test_results VALUES (:id, :t, 'AC', :t, 1000)")
                    .param("id", id).param("t", t).update();
        }
        aliceSession.perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentTest").value(3))
                .andExpect(jsonPath("$.totalTests").value(5))
                .andExpect(jsonPath("$.testResults[*].testNumber").value(contains(1, 2)));
    }

    @Test
    void unknownId() throws Exception {
        aliceSession.perform(get("/api/submissions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Submission not found."));
    }
}
