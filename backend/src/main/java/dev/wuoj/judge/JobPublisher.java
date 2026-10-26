package dev.wuoj.judge;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hands submissions to the judge queue. Publishing happens after the creating transaction commits, so a worker never
 * sees a submission that does not exist yet; {@code enqueued_at} is set only once the broker has confirmed the
 * message, and the recovery sweeper republishes anything that stays unconfirmed.
 */
@Component
public class JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobPublisher.class);
    private static final Pattern JOB = Pattern.compile("\\{\\s*\"submissionId\"\\s*:\\s*(\\d+)\\s*}");

    private final RabbitTemplate rabbit;
    private final JudgeRepository judge;

    public JobPublisher(RabbitTemplate rabbit, JudgeRepository judge) {
        this.rabbit = rabbit;
        this.judge = judge;
    }

    /** Publishes after the current transaction commits, or right away if there is none. */
    public void publishAfterCommit(long submissionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(submissionId);
                }
            });
        } else {
            publish(submissionId);
        }
    }

    /** Never throws: a failed publish is retried by the recovery sweeper. */
    public void publish(long submissionId) {
        try {
            Message message = MessageBuilder
                    .withBody(("{\"submissionId\":" + submissionId + "}").getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            CorrelationData correlation = new CorrelationData(Long.toString(submissionId));
            rabbit.send(JudgeMessaging.JOBS_EXCHANGE, JudgeMessaging.JOB_ROUTING_KEY, message, correlation);
            correlation.getFuture().whenComplete((confirm, error) -> {
                if (error == null && confirm.ack() && correlation.getReturned() == null) {
                    judge.markEnqueued(submissionId);
                } else {
                    log.warn("Judge job for submission {} was not confirmed; the sweeper will retry", submissionId);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not publish judge job for submission {}; the sweeper will retry", submissionId, e);
        }
    }

    /** The submission id of a job message. */
    static long submissionId(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Matcher matcher = JOB.matcher(body);
        if (!matcher.matches()) throw new IllegalArgumentException("Malformed judge job: " + body);
        return Long.parseLong(matcher.group(1));
    }
}
