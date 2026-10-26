package dev.wuoj.judge;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import com.rabbitmq.client.Channel;
import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.problem.UserProblemStatusRepository;
import dev.wuoj.testdata.TestDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumes judge jobs. A message only says "look at submission N"; the conditional claim in the database decides
 * whether this worker judges it, so duplicate and stale messages are dropped. The message is acknowledged only after
 * the attempt has ended, so a crash makes RabbitMQ deliver it again.
 */
@Component
@ConditionalOnRole(Role.WORKER)
public class JudgeWorker {

    /** Attempts after which a submission is given up on as System Error. */
    static final int MAX_ATTEMPTS = 3;
    static final Duration LEASE = Duration.ofSeconds(30);
    static final Duration HEARTBEAT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final String workerId = workerId();
    private final JudgeRepository judge;
    private final TestDataRepository testdata;
    private final UserProblemStatusRepository statuses;
    private final EventPublisher events;
    private final TransactionTemplate transactions;
    private final JudgeSlots slots;
    private final JudgeEngine engine;
    private final TaskScheduler scheduler;

    public JudgeWorker(JudgeRepository judge, TestDataRepository testdata, UserProblemStatusRepository statuses,
            EventPublisher events, TransactionTemplate transactions, JudgeSlots slots, JudgeEngine engine,
            TaskScheduler scheduler) {
        this.judge = judge;
        this.testdata = testdata;
        this.statuses = statuses;
        this.events = events;
        this.transactions = transactions;
        this.slots = slots;
        this.engine = engine;
        this.scheduler = scheduler;
    }

    @RabbitListener(queues = JudgeMessaging.JOBS_QUEUE, containerFactory = "judgeListenerFactory")
    public void onJob(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException, InterruptedException {
        long submissionId;
        try {
            submissionId = JobPublisher.submissionId(message);
        } catch (IllegalArgumentException e) {
            log.error("Dead-lettering a malformed judge job: {}", e.getMessage());
            channel.basicReject(tag, false);
            return;
        }
        try {
            handle(submissionId);
        } finally {
            channel.basicAck(tag, false);
        }
    }

    /** Judges the submission if this worker can claim it. Exposed for tests. */
    void handle(long submissionId) throws InterruptedException {
        Optional<JudgeRepository.Claim> claimed = judge.claim(submissionId, workerId, LEASE);
        if (claimed.isEmpty()) {
            log.debug("Submission {} is finished or held by another worker; dropping the job", submissionId);
            return;
        }
        JudgeRepository.Claim claim = claimed.get();
        if (claim.attempt() > MAX_ATTEMPTS) {
            log.warn("Submission {} failed {} attempts; finishing as System Error", submissionId, MAX_ATTEMPTS);
            judge.finishAsSystemError(submissionId);
            events.publishCurrent(submissionId);
            return;
        }
        events.publishCurrent(submissionId);

        JudgeRepository.Lease lease = new JudgeRepository.Lease(submissionId, workerId, claim.attempt());
        JudgeRun run = new DatabaseJudgeRun(lease, claim.userId(), claim.problemId(), judge, statuses, events,
                transactions);
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            if (!judge.renewLease(lease, LEASE)) log.warn("Lost the lease on submission {}", submissionId);
        }, Instant.now().plus(HEARTBEAT), HEARTBEAT);
        JudgeSlots.Slot slot = slots.take();
        try {
            int testCount = testdata.testCount(claim.problemId(), claim.testdataVersion())
                    .orElseThrow(() -> new IllegalStateException("Unknown test-data version for " + submissionId));
            engine.judge(new JudgeJob(submissionId, claim.attempt(), claim.sourceCode(), claim.problemId(),
                    claim.testdataVersion(), testCount, claim.timeLimitMs(), claim.memoryLimitMb(), slot), run);
            if (!run.isFinished()) throw new IllegalStateException("The judge engine reported no verdict");
        } catch (JudgeRepository.LostLeaseException e) {
            log.warn("{}; leaving submission {} to the new attempt", e.getMessage(), submissionId);
        } catch (InterruptedException e) {
            judge.expireLease(lease);
            throw e;
        } catch (Exception e) {
            log.error("Judging submission {} (attempt {}) failed; it will be retried", submissionId,
                    claim.attempt(), e);
            judge.expireLease(lease);
        } finally {
            heartbeat.cancel(false);
            slots.release(slot);
        }
    }

    private static String workerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            host = "worker";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
