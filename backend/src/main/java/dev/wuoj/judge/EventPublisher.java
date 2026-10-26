package dev.wuoj.judge;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.submission.SubmissionDtos.SubmissionStatusEvent;
import dev.wuoj.submission.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Announces committed status changes to the API instances holding live streams. Best effort: a lost event is
 * repaired by the streams' periodic check against the database.
 */
@Component
public class EventPublisher {

    /** An event with its sequence number, so streams can drop stale or duplicate events. */
    public record StatusMessage(long seq, SubmissionStatusEvent event) {}

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final Viewer SYSTEM = new Viewer(0, UserRole.ADMIN);

    private final RabbitTemplate rabbit;
    private final SubmissionRepository submissions;
    private final JsonMapper json;

    public EventPublisher(RabbitTemplate rabbit, SubmissionRepository submissions, JsonMapper json) {
        this.rabbit = rabbit;
        this.submissions = submissions;
        this.json = json;
    }

    /** Publishes the submission's current, committed state. Call after the change has been committed. */
    public void publishCurrent(long submissionId) {
        try {
            submissions.find(SYSTEM, submissionId).ifPresent(row -> publish(
                    new StatusMessage(row.eventSeq(), SubmissionStatusEvent.of(row))));
        } catch (RuntimeException e) {
            log.warn("Could not publish status event for submission {}", submissionId, e);
        }
    }

    private void publish(StatusMessage status) {
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(status))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                .build();
        rabbit.send(JudgeMessaging.EVENTS_EXCHANGE, routingKey(status.event().submissionId()), message);
    }

    public static String routingKey(long submissionId) {
        return "submission." + submissionId;
    }
}
