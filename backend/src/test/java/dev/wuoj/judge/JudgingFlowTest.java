package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Submissions going through the real queue and worker, judged by the simulated judge (test profile). */
class JudgingFlowTest extends IntegrationTest {

    private ApiSession alice;
    private long problem;

    @BeforeEach
    void setUp() throws Exception {
        fixtures.user("alice");
        problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        alice = signedIn("alice");
    }

    private long submit(String source) throws Exception {
        String body = alice.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem + ",\"language\":\"CPP17\",\"sourceCode\":\""
                                + source + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void awaitFinished(long id) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(fixtures.jdbc()
                .sql("SELECT status FROM submissions WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("FINISHED"));
    }

    @Test
    void wrongAnswerOnTestThree() throws Exception {
        long id = submit("int main() {} // @mock-verdict: WA");
        awaitFinished(id);
        alice.perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.verdict").value("WA"))
                .andExpect(jsonPath("$.totalTests").value(5))
                .andExpect(jsonPath("$.currentTest").doesNotExist())
                .andExpect(jsonPath("$.testResults[*].verdict").value(contains("AC", "AC", "WA")))
                .andExpect(jsonPath("$.timeMs").isNumber())
                .andExpect(jsonPath("$.memoryKb").isNumber());
        alice.perform(get("/api/problems/a-plus-b")).andExpect(jsonPath("$.status").value("ATTEMPTED"));
    }

    @Test
    void acceptedMarksTheProblemSolved() throws Exception {
        long id = submit("int main() { return 0; }");
        awaitFinished(id);
        alice.perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.verdict").value("AC"))
                .andExpect(jsonPath("$.testResults.length()").value(5));
        alice.perform(get("/api/problems")).andExpect(jsonPath("$.items[0].status").value("SOLVED"));
    }

    @Test
    void compilationErrorHasOutputAndNoMeasurements() throws Exception {
        long id = submit("int helper() {}");
        awaitFinished(id);
        alice.perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.verdict").value("CE"))
                .andExpect(jsonPath("$.compileOutput").value(org.hamcrest.Matchers.containsString("main")))
                .andExpect(jsonPath("$.testResults.length()").value(0))
                .andExpect(jsonPath("$.timeMs").doesNotExist());
    }
}
