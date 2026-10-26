package dev.wuoj.judge;

import java.nio.charset.StandardCharsets;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Jobs RabbitMQ gave up on (rejected as malformed, or delivered too often). Rare, since retries are normally driven
 * by the database; a submission whose job ends up here is finished as System Error so it never stays pending.
 */
@Component
@ConditionalOnRole(Role.WORKER)
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private final JudgeRepository judge;
    private final EventPublisher events;

    public DeadLetterListener(JudgeRepository judge, EventPublisher events) {
        this.judge = judge;
        this.events = events;
    }

    @RabbitListener(queues = JudgeMessaging.DEAD_QUEUE)
    public void onDeadJob(Message message) {
        long submissionId;
        try {
            submissionId = JobPublisher.submissionId(message);
        } catch (IllegalArgumentException e) {
            log.error("Discarding a dead-lettered judge job that names no submission: {}",
                    new String(message.getBody(), StandardCharsets.UTF_8));
            return;
        }
        log.error("Judge job for submission {} was dead-lettered; finishing it as System Error", submissionId);
        if (judge.finishAsSystemError(submissionId).isPresent()) events.publishCurrent(submissionId);
    }
}
