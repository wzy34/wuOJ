package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Properties;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Without the worker role, so published jobs stay in the queue where the test can count them. */
@TestPropertySource(properties = "oj.roles=api")
class JudgeQueueTest extends IntegrationTest {

    @Autowired
    RabbitAdmin rabbitAdmin;

    private long messages() {
        Properties properties = rabbitAdmin.getQueueProperties(JudgeMessaging.JOBS_QUEUE);
        return properties == null ? -1 : ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue();
    }

    @Test
    void creatingASubmissionPublishesExactlyOneConfirmedJob() throws Exception {
        rabbitAdmin.purgeQueue(JudgeMessaging.JOBS_QUEUE, false);
        fixtures.user("alice");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);

        signedIn("alice").perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem
                                + ",\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}"))
                .andExpect(status().isCreated());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(messages()).isEqualTo(1);
            assertThat(fixtures.jdbc().sql("SELECT count(*) FROM submissions WHERE enqueued_at IS NOT NULL")
                    .query(Long.class).single()).isEqualTo(1);
        });
    }

    @Test
    void topologyIsDeclared() {
        Properties jobs = rabbitAdmin.getQueueProperties(JudgeMessaging.JOBS_QUEUE);
        Properties dead = rabbitAdmin.getQueueProperties(JudgeMessaging.DEAD_QUEUE);
        assertThat(jobs).isNotNull();
        assertThat(dead).isNotNull();
    }
}
