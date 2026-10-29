package dev.wuoj.judge;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The queue side of submissions: claiming, leases and fenced progress writes. Every write by a worker is conditioned
 * on {@code worker_id} and {@code attempts}, so a worker that lost its lease can never overwrite a newer attempt.
 */
@Repository
public class JudgeRepository {

    /** The work a worker needs; {@code attempt} doubles as the fencing token for its writes. */
    public record Claim(long submissionId, int attempt, long eventSeq, long userId, long problemId,
            String sourceCode, String testdataVersion, int timeLimitMs, int memoryLimitMb) {}

    /** A claimed attempt: the fencing token for every write. */
    public record Lease(long submissionId, String workerId, int attempt) {}

    private final JdbcClient jdbc;

    public JudgeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void markEnqueued(long submissionId) {
        jdbc.sql("UPDATE submissions SET enqueued_at = now() WHERE id = :id AND enqueued_at IS NULL")
                .param("id", submissionId)
                .update();
    }

    /**
     * Claims a submission that is pending, or whose previous worker's lease has expired, and resets it for a fresh
     * attempt. Empty if it is finished or another worker holds a live lease.
     */
    public Optional<Claim> claim(long submissionId, String workerId, Duration lease) {
        Optional<Long> claimed = jdbc.sql("""
                        UPDATE submissions
                           SET status = 'COMPILING', worker_id = :worker, attempts = attempts + 1,
                               lease_until = now() + make_interval(secs => :lease), event_seq = event_seq + 1,
                               current_test = NULL, total_tests = NULL, verdict = NULL, time_ms = NULL,
                               memory_kb = NULL, compile_output = NULL
                         WHERE id = :id
                           AND (status = 'PENDING'
                                OR (status IN ('COMPILING', 'RUNNING') AND lease_until < now()))
                        RETURNING id""")
                .param("id", submissionId)
                .param("worker", workerId)
                .param("lease", lease.toMillis() / 1000.0)
                .query(Long.class)
                .optional();
        if (claimed.isEmpty()) return Optional.empty();
        jdbc.sql("DELETE FROM submission_test_results WHERE submission_id = :id").param("id", submissionId).update();
        return jdbc.sql("""
                        SELECT s.id, s.attempts, s.event_seq, s.user_id, s.problem_id, s.source_code,
                               s.testdata_version, s.time_limit_ms, s.memory_limit_mb
                          FROM submissions s
                         WHERE s.id = :id""")
                .param("id", submissionId)
                .query((rs, n) -> new Claim(rs.getLong("id"), rs.getInt("attempts"), rs.getLong("event_seq"),
                        rs.getLong("user_id"), rs.getLong("problem_id"), rs.getString("source_code"),
                        rs.getString("testdata_version"), rs.getInt("time_limit_ms"),
                        rs.getInt("memory_limit_mb")))
                .optional();
    }

    /**
     * Extends a live lease; false if the attempt is no longer ours. An expired lease is never renewed, so a late
     * heartbeat cannot undo {@link #expireLease}.
     */
    public boolean renewLease(Lease lease, Duration duration) {
        return jdbc.sql("""
                        UPDATE submissions SET lease_until = now() + make_interval(secs => :lease)
                         WHERE id = :id AND worker_id = :worker AND attempts = :attempt AND status <> 'FINISHED'
                           AND lease_until > now()""")
                .param("lease", duration.toMillis() / 1000.0)
                .params(fence(lease))
                .update() == 1;
    }

    /** Gives the submission back for a retry by the recovery sweeper (e.g. after an infrastructure error). */
    public void expireLease(Lease lease) {
        jdbc.sql("""
                        UPDATE submissions SET lease_until = now() - interval '1 second'
                         WHERE id = :id AND worker_id = :worker AND attempts = :attempt AND status <> 'FINISHED'""")
                .params(fence(lease))
                .update();
    }

    /**
     * Records a change of status or progress; returns the new event sequence number, or empty if the attempt is no
     * longer ours.
     */
    public Optional<Long> progress(Lease lease, SubmissionStatus status, @Nullable Integer currentTest,
            @Nullable Integer totalTests) {
        return jdbc.sql("""
                        UPDATE submissions
                           SET status = :status, current_test = :current, total_tests = :total,
                               event_seq = event_seq + 1
                         WHERE id = :id AND worker_id = :worker AND attempts = :attempt AND status <> 'FINISHED'
                        RETURNING event_seq""")
                .param("status", status.name())
                .param("current", currentTest)
                .param("total", totalTests)
                .params(fence(lease))
                .query(Long.class)
                .optional();
    }

    public void addTestResult(Lease lease, int testNumber, Verdict verdict, int timeMs, int memoryKb) {
        int inserted = jdbc.sql("""
                        INSERT INTO submission_test_results (submission_id, test_number, verdict, time_ms, memory_kb)
                        SELECT :id, :test, :verdict, :time, :memory
                         WHERE EXISTS (SELECT 1 FROM submissions
                                        WHERE id = :id AND worker_id = :worker AND attempts = :attempt
                                          AND status <> 'FINISHED')""")
                .param("test", testNumber)
                .param("verdict", verdict.name())
                .param("time", timeMs)
                .param("memory", memoryKb)
                .params(fence(lease))
                .update();
        if (inserted == 0) throw new LostLeaseException(lease);
    }

    /** Finishes the attempt; returns the final event sequence number, or empty if the attempt is no longer ours. */
    public Optional<Long> finish(Lease lease, Verdict verdict, @Nullable Integer timeMs, @Nullable Integer memoryKb,
            @Nullable String compileOutput) {
        return jdbc.sql("""
                        UPDATE submissions
                           SET status = 'FINISHED', verdict = :verdict, time_ms = :time, memory_kb = :memory,
                               compile_output = :compileOutput, current_test = NULL, finished_at = now(),
                               lease_until = NULL, event_seq = event_seq + 1
                         WHERE id = :id AND worker_id = :worker AND attempts = :attempt AND status <> 'FINISHED'
                        RETURNING event_seq""")
                .param("verdict", verdict.name())
                .param("time", timeMs)
                .param("memory", memoryKb)
                .param("compileOutput", compileOutput)
                .params(fence(lease))
                .query(Long.class)
                .optional();
    }

    /**
     * Finishes a submission as System Error without a lease, for submissions that ran out of attempts or whose
     * message was dead-lettered. Returns the final event sequence number if it was not finished yet.
     */
    public Optional<Long> finishAsSystemError(long submissionId) {
        return jdbc.sql("""
                        UPDATE submissions
                           SET status = 'FINISHED', verdict = 'SE', current_test = NULL, finished_at = now(),
                               lease_until = NULL, event_seq = event_seq + 1
                         WHERE id = :id AND status <> 'FINISHED'
                        RETURNING event_seq""")
                .param("id", submissionId)
                .query(Long.class)
                .optional();
    }

    /**
     * Puts submissions whose worker's lease expired (it crashed, or gave up after an infrastructure error) back to
     * {@code PENDING}, discarding partial results, so they are judged again from the start once republished.
     */
    public List<Long> resetAbandoned() {
        List<Long> ids = jdbc.sql("""
                        UPDATE submissions
                           SET status = 'PENDING', enqueued_at = NULL, worker_id = NULL, lease_until = NULL,
                               current_test = NULL, total_tests = NULL, event_seq = event_seq + 1
                         WHERE status IN ('COMPILING', 'RUNNING') AND lease_until < now()
                        RETURNING id""")
                .query(Long.class)
                .list();
        for (long id : ids) {
            jdbc.sql("DELETE FROM submission_test_results WHERE submission_id = :id").param("id", id).update();
        }
        return ids;
    }

    /** Pending submissions whose job the broker never confirmed (the publish or its confirm was lost). */
    public List<Long> unconfirmed(Duration olderThan) {
        return jdbc.sql("""
                        SELECT id FROM submissions
                         WHERE status = 'PENDING' AND enqueued_at IS NULL
                           AND created_at < now() - make_interval(secs => :age)
                         ORDER BY id""")
                .param("age", olderThan.toMillis() / 1000.0)
                .query(Long.class)
                .list();
    }

    /**
     * Pending submissions whose confirmed job has waited so long that the message itself may have been lost. Their
     * {@code enqueued_at} is renewed, so each is republished at most once per period; duplicates are harmless.
     */
    public List<Long> renewStalePending(Duration olderThan) {
        return jdbc.sql("""
                        UPDATE submissions SET enqueued_at = now()
                         WHERE status = 'PENDING' AND enqueued_at < now() - make_interval(secs => :age)
                        RETURNING id""")
                .param("age", olderThan.toMillis() / 1000.0)
                .query(Long.class)
                .list();
    }

    /** Takes a transaction-scoped advisory lock so that only one instance sweeps at a time. */
    public boolean tryLockSweeper() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                .param("key", 0x6F6A5357L) // "ojSW"
                .query(Boolean.class)
                .single());
    }

    /** Whether this attempt is still being judged under a live lease; its containers and files are still needed. */
    public boolean isLiveAttempt(long submissionId, int attempt) {
        return jdbc.sql("""
                        SELECT count(*) FROM submissions
                         WHERE id = :id AND attempts = :attempt AND status IN ('COMPILING', 'RUNNING')
                           AND lease_until >= now()""")
                .param("id", submissionId)
                .param("attempt", attempt)
                .query(Long.class)
                .single() > 0;
    }

    private static Map<String, Object> fence(Lease lease) {
        return Map.of("id", lease.submissionId(), "worker", lease.workerId(), "attempt", lease.attempt());
    }

    /** The attempt is no longer ours: another worker took over after our lease expired. */
    public static class LostLeaseException extends RuntimeException {
        public LostLeaseException(Lease lease) {
            super("Lost the lease on submission " + lease.submissionId() + " attempt " + lease.attempt());
        }
    }
}
