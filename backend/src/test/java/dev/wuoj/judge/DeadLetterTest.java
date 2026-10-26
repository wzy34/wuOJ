package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class DeadLetterTest extends IntegrationTest {

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    JobPublisher jobs;

    private String verdictOf(long id) {
        return fixtures.jdbc().sql("SELECT verdict FROM submissions WHERE id = :id").param("id", id)
                .query((rs, n) -> rs.getString(1)).list().getFirst();
    }

    @Test
    void malformedJobIsDeadLetteredWithoutAffectingOthers(CapturedOutput output) {
        long user = fixtures.user("alice");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);

        rabbit.send(JudgeMessaging.JOBS_EXCHANGE, JudgeMessaging.JOB_ROUTING_KEY,
                MessageBuilder.withBody("not a job".getBytes(StandardCharsets.UTF_8)).build());
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
        jobs.publish(id);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(output).contains("Dead-lettering a malformed judge job")
                    .contains("Discarding a dead-lettered judge job that names no submission: not a job");
            assertThat(verdictOf(id)).isEqualTo("AC");
        });
    }

    @Test
    void deadLetteredJobOfASubmissionFinishesItAsSystemError() {
        long user = fixtures.user("alice");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);

        rabbit.send(JudgeMessaging.DEAD_EXCHANGE, JudgeMessaging.DEAD_ROUTING_KEY, MessageBuilder
                .withBody(("{\"submissionId\":" + id + "}").getBytes(StandardCharsets.UTF_8)).build());

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(verdictOf(id))
                .isEqualTo("SE"));
    }
}
