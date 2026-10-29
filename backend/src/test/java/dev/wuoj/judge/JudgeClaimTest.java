package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Claiming and fencing, driven directly rather than through RabbitMQ. */
class JudgeClaimTest extends IntegrationTest {

    private static final Duration LEASE = Duration.ofSeconds(30);

    @Autowired
    JudgeRepository judge;

    @Autowired
    JudgeWorker worker;

    @Autowired
    SubmissionRepository submissions;

    private long submission;

    @BeforeEach
    void setUp() {
        long user = fixtures.user("alice");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        // Inserted directly, so no job is published and only this test judges it.
        submission = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);
    }

    private String column(String name) {
        return fixtures.jdbc().sql("SELECT " + name + "::text FROM submissions WHERE id = :id")
                .param("id", submission).query((rs, n) -> rs.getString(1)).list().getFirst();
    }

    private void expireLease() {
        fixtures.jdbc().sql("UPDATE submissions SET lease_until = now() - interval '1 second' WHERE id = :id")
                .param("id", submission).update();
    }

    @Test
    void judgesWithTheLimitsInForceWhenSubmitted() {
        fixtures.jdbc().sql("UPDATE problems SET time_limit_ms = 2000, memory_limit_mb = 512").update();

        JudgeRepository.Claim claim = judge.claim(submission, "worker-a", LEASE).orElseThrow();

        assertThat(claim.timeLimitMs()).isEqualTo(1000);
        assertThat(claim.memoryLimitMb()).isEqualTo(256);
    }

    @Test
    void duplicateIsDroppedWhileTheLeaseIsLive() {
        assertThat(judge.claim(submission, "worker-a", LEASE)).isPresent();
        assertThat(judge.claim(submission, "worker-b", LEASE)).isEmpty();
        assertThat(column("worker_id")).isEqualTo("worker-a");
        assertThat(column("attempts")).isEqualTo("1");
    }

    @Test
    void expiredLeaseCanBeTakenOverAndOldResultsAreDiscarded() {
        var first = judge.claim(submission, "worker-a", LEASE).orElseThrow();
        var lease = new JudgeRepository.Lease(submission, "worker-a", first.attempt());
        judge.progress(lease, SubmissionStatus.RUNNING, 1, 5);
        judge.addTestResult(lease, 1, Verdict.AC, 3, 3000);
        expireLease();

        var second = judge.claim(submission, "worker-b", LEASE).orElseThrow();
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(column("status")).isEqualTo("COMPILING");
        assertThat(submissions.testResults(submission)).isEmpty();
    }

    @Test
    void staleAttemptCannotWrite() {
        var first = judge.claim(submission, "worker-a", LEASE).orElseThrow();
        var stale = new JudgeRepository.Lease(submission, "worker-a", first.attempt());
        expireLease();
        var second = judge.claim(submission, "worker-b", LEASE).orElseThrow();
        var current = new JudgeRepository.Lease(submission, "worker-b", second.attempt());

        assertThat(judge.progress(stale, SubmissionStatus.RUNNING, 4, 5)).isEmpty();
        assertThatThrownBy(() -> judge.addTestResult(stale, 1, Verdict.WA, 1, 1))
                .isInstanceOf(JudgeRepository.LostLeaseException.class);
        assertThat(judge.finish(stale, Verdict.WA, 1, 1, null)).isEmpty();
        assertThat(judge.renewLease(stale, LEASE)).isFalse();
        assertThat(column("status")).isEqualTo("COMPILING");
        assertThat(column("verdict")).isNull();

        assertThat(judge.progress(current, SubmissionStatus.RUNNING, 1, 5)).isPresent();
        assertThat(judge.finish(current, Verdict.AC, 5, 3000, null)).isPresent();
        assertThat(column("verdict")).isEqualTo("AC");
    }

    @Test
    void finishedSubmissionIsNeverClaimed() {
        var claim = judge.claim(submission, "worker-a", LEASE).orElseThrow();
        judge.finish(new JudgeRepository.Lease(submission, "worker-a", claim.attempt()), Verdict.AC, 1, 1, null);
        expireLease();
        assertThat(judge.claim(submission, "worker-b", LEASE)).isEmpty();
    }

    @Test
    void eachWriteAdvancesTheEventSequence() {
        long created = Long.parseLong(column("event_seq"));
        var claim = judge.claim(submission, "worker-a", LEASE).orElseThrow();
        assertThat(claim.eventSeq()).isEqualTo(created + 1);
        var lease = new JudgeRepository.Lease(submission, "worker-a", claim.attempt());
        assertThat(judge.progress(lease, SubmissionStatus.RUNNING, 1, 5)).contains(created + 2);
        assertThat(judge.finish(lease, Verdict.AC, 1, 1, null)).contains(created + 3);
    }

    @Test
    void fourthAttemptFinishesAsSystemError() throws Exception {
        fixtures.jdbc().sql("UPDATE submissions SET attempts = 3 WHERE id = :id").param("id", submission).update();
        worker.handle(submission);
        assertThat(column("status")).isEqualTo("FINISHED");
        assertThat(column("verdict")).isEqualTo("SE");
        assertThat(column("attempts")).isEqualTo("4");
    }

    @Test
    void workerJudgesAClaimedSubmissionToTheEnd() throws Exception {
        worker.handle(submission);
        assertThat(column("status")).isEqualTo("FINISHED");
        assertThat(column("verdict")).isEqualTo("AC");
        assertThat(submissions.testResults(submission)).hasSize(5);
        assertThat(column("lease_until")).isNull();
    }
}
