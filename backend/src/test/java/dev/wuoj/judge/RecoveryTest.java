package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Recovery from lost messages and crashed workers; a fast sweeper keeps the waits short. */
@TestPropertySource(properties = {"oj.judge.sweep-interval=1s", "oj.judge.unconfirmed-after=1s"})
class RecoveryTest extends IntegrationTest {

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    JudgeRepository judge;

    @Autowired
    RabbitMQContainer rabbit;

    private long user;
    private long problem;

    @BeforeEach
    void setUp() {
        user = fixtures.user("alice");
        problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
    }

    private String column(long id, String name) {
        return fixtures.jdbc().sql("SELECT " + name + "::text FROM submissions WHERE id = :id")
                .param("id", id).query((rs, n) -> rs.getString(1)).list().getFirst();
    }

    private void awaitFinished(long id, Duration timeout) {
        Awaitility.await().atMost(timeout).untilAsserted(() -> assertThat(column(id, "status")).isEqualTo("FINISHED"));
    }

    @Test
    void lostPublishIsRepublished() {
        // Created without publishing, as if the broker never received the job.
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        awaitFinished(id, Duration.ofSeconds(20));
        assertThat(column(id, "verdict")).isEqualTo("AC");
        assertThat(column(id, "enqueued_at")).isNotNull();
    }

    @Test
    void crashedWorkerIsReplacedAndJudgingStartsOver() {
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        fixtures.jdbc().sql("UPDATE submissions SET enqueued_at = now() WHERE id = :id").param("id", id).update();
        // A worker claims it, gets two tests in, and dies: its lease runs out.
        var claim = judge.claim(id, "dead-worker", Duration.ofSeconds(1)).orElseThrow();
        var lease = new JudgeRepository.Lease(id, "dead-worker", claim.attempt());
        judge.progress(lease, SubmissionStatus.RUNNING, 2, 5);
        judge.addTestResult(lease, 1, Verdict.AC, 999, 999);
        judge.addTestResult(lease, 2, Verdict.AC, 999, 999);

        awaitFinished(id, Duration.ofSeconds(20));
        assertThat(column(id, "attempts")).isEqualTo("2");
        assertThat(column(id, "verdict")).isEqualTo("AC");
        assertThat(submissions.testResults(id)).hasSize(5).allSatisfy(r -> assertThat(r.timeMs()).isNotEqualTo(999));
    }

    @Test
    void submissionsMadeWhileTheBrokerIsDownAreJudgedAfterItComesBack() throws Exception {
        ApiSession alice = signedIn("alice");
        rabbit.execInContainer("rabbitmqctl", "stop_app");
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                String body = alice.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"problemId\":" + problem + ",\"language\":\"CPP17\","
                                        + "\"sourceCode\":\"int main(){}\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                ids.add(((Number) JsonPath.read(body, "$.id")).longValue());
            }
            for (long id : ids) assertThat(column(id, "status")).isEqualTo("PENDING");
        } finally {
            rabbit.execInContainer("rabbitmqctl", "start_app");
            rabbit.execInContainer("rabbitmqctl", "await_startup");
        }
        for (long id : ids) {
            awaitFinished(id, Duration.ofSeconds(60));
            assertThat(column(id, "verdict")).isEqualTo("AC");
        }
    }
}
