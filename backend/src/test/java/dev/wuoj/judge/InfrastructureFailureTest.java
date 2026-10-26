package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** A judge whose infrastructure always fails: each attempt is retried, and after three the verdict is SE. */
@TestPropertySource(properties = {"oj.judge.sweep-interval=1s", "oj.judge.unconfirmed-after=1s"})
@Import(InfrastructureFailureTest.BrokenSandbox.class)
class InfrastructureFailureTest extends IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class BrokenSandbox {
        static final AtomicInteger calls = new AtomicInteger();

        @Bean
        @Primary
        JudgeEngine brokenEngine() {
            return (job, run) -> {
                calls.incrementAndGet();
                throw new IllegalStateException("Cannot connect to the Docker daemon");
            };
        }
    }

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    JobPublisher jobs;

    @Test
    void threeFailedAttemptsEndInSystemError() {
        long user = fixtures.user("alice");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        BrokenSandbox.calls.set(0);
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        jobs.publish(id);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(fixtures.jdbc()
                .sql("SELECT verdict FROM submissions WHERE id = :id").param("id", id)
                .query((rs, n) -> rs.getString(1)).list().getFirst()).isEqualTo("SE"));
        assertThat(BrokenSandbox.calls.get()).isEqualTo(3);
        assertThat(fixtures.jdbc().sql("SELECT attempts FROM submissions WHERE id = :id").param("id", id)
                .query(Integer.class).single()).isEqualTo(4);
    }
}
