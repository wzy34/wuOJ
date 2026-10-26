package dev.wuoj.judge;

import java.util.ArrayList;
import java.util.List;

import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.problem.UserProblemStatusRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The worker's {@link JudgeRun}: every report is written under the attempt's lease (fenced) and then announced to
 * live streams. Throws {@link JudgeRepository.LostLeaseException} if another worker has taken the submission over.
 */
class DatabaseJudgeRun implements JudgeRun {

    private final JudgeRepository.Lease lease;
    private final long userId;
    private final long problemId;
    private final JudgeRepository judge;
    private final UserProblemStatusRepository statuses;
    private final EventPublisher events;
    private final TransactionTemplate transactions;
    private final List<TestOutcome> outcomes = new ArrayList<>();
    private boolean finished;

    DatabaseJudgeRun(JudgeRepository.Lease lease, long userId, long problemId, JudgeRepository judge,
            UserProblemStatusRepository statuses, EventPublisher events, TransactionTemplate transactions) {
        this.lease = lease;
        this.userId = userId;
        this.problemId = problemId;
        this.judge = judge;
        this.statuses = statuses;
        this.events = events;
        this.transactions = transactions;
    }

    @Override
    public void running(int current, int total) {
        judge.progress(lease, SubmissionStatus.RUNNING, current, total)
                .orElseThrow(() -> new JudgeRepository.LostLeaseException(lease));
        events.publishCurrent(lease.submissionId());
    }

    @Override
    public void testFinished(TestOutcome outcome) {
        judge.addTestResult(lease, outcome.testNumber(), outcome.verdict(), outcome.timeMs(), outcome.memoryKb());
        outcomes.add(outcome);
    }

    @Override
    public void compileError(@Nullable String compileOutput) {
        finish(Verdict.CE, compileOutput);
    }

    @Override
    public void finish(Verdict verdict, @Nullable String compileOutput) {
        Integer timeMs = outcomes.isEmpty() ? null
                : outcomes.stream().mapToInt(TestOutcome::timeMs).max().orElseThrow();
        Integer memoryKb = outcomes.isEmpty() ? null
                : outcomes.stream().mapToInt(TestOutcome::memoryKb).max().orElseThrow();
        String output = compileOutput == null || compileOutput.isEmpty() ? null : compileOutput;
        transactions.executeWithoutResult(status -> {
            judge.finish(lease, verdict, timeMs, memoryKb, output)
                    .orElseThrow(() -> new JudgeRepository.LostLeaseException(lease));
            if (verdict == Verdict.AC) statuses.markSolved(userId, problemId);
        });
        finished = true;
        events.publishCurrent(lease.submissionId());
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}
