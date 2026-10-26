package dev.wuoj.judge;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes sure every unfinished submission eventually reaches a worker, whatever was lost on the way: a publish whose
 * confirm never came, a worker that died mid-judge, or (rarely) a message lost by the broker. Runs on one worker
 * instance at a time.
 */
@Component
@ConditionalOnRole(Role.WORKER)
public class RecoverySweeper {

    static final Duration PENDING_TOO_LONG = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(RecoverySweeper.class);

    private final JudgeRepository judge;
    private final JobPublisher jobs;
    private final EventPublisher events;
    private final TransactionTemplate transactions;
    private final Duration unconfirmedAfter;

    public RecoverySweeper(JudgeRepository judge, JobPublisher jobs, EventPublisher events,
            TransactionTemplate transactions, @Value("${oj.judge.unconfirmed-after:30s}") Duration unconfirmedAfter) {
        this.judge = judge;
        this.jobs = jobs;
        this.events = events;
        this.transactions = transactions;
        this.unconfirmedAfter = unconfirmedAfter;
    }

    @Scheduled(initialDelayString = "${oj.judge.sweep-interval:15s}",
            fixedDelayString = "${oj.judge.sweep-interval:15s}")
    public void sweep() {
        record Found(List<Long> abandoned, List<Long> republish) {}
        Found found = transactions.execute(status -> {
            if (!judge.tryLockSweeper()) return new Found(List.of(), List.of());
            List<Long> abandoned = judge.resetAbandoned();
            List<Long> republish = new ArrayList<>(abandoned);
            republish.addAll(judge.unconfirmed(unconfirmedAfter));
            republish.addAll(judge.renewStalePending(PENDING_TOO_LONG));
            return new Found(abandoned, republish.stream().distinct().sorted().toList());
        });
        if (found == null) return;
        if (!found.abandoned().isEmpty()) log.warn("Rejudging abandoned submissions {}", found.abandoned());
        found.abandoned().forEach(events::publishCurrent);
        found.republish().forEach(jobs::publish);
    }
}
