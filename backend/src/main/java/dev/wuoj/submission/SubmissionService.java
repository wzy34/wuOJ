package dev.wuoj.submission;

import java.time.Duration;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.ratelimit.SlidingWindowLimiter;
import dev.wuoj.common.web.NotFoundException;
import dev.wuoj.common.web.RateLimitedException;
import dev.wuoj.judge.JobPublisher;
import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.problem.ProblemRow;
import dev.wuoj.problem.UserProblemStatusRepository;
import dev.wuoj.submission.SubmissionDtos.SubmissionDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

    private final ProblemRepository problems;
    private final SubmissionRepository submissions;
    private final UserProblemStatusRepository statuses;
    private final SlidingWindowLimiter limiter;
    private final JobPublisher jobs;
    private final OjProperties.RateLimit limits;

    public SubmissionService(ProblemRepository problems, SubmissionRepository submissions,
            UserProblemStatusRepository statuses, SlidingWindowLimiter limiter, JobPublisher jobs,
            OjProperties properties) {
        this.problems = problems;
        this.submissions = submissions;
        this.statuses = statuses;
        this.limiter = limiter;
        this.jobs = jobs;
        this.limits = properties.rateLimit();
    }

    /**
     * Stores a submission and queues it for judging once the transaction commits. Checked in this order: the problem
     * is visible (404), the user has too many unfinished submissions (429), the client address submitted too often
     * (429), the user submitted too often (429). A refused submission counts towards none of the limits.
     */
    @Transactional
    public SubmissionDetail create(Viewer viewer, String address, long problemId, Language language,
            String sourceCode) {
        ProblemRow problem = problems.findById(viewer, problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found."));

        submissions.lockUser(viewer.userId());
        int unfinished = submissions.countUnfinished(viewer.userId());
        if (unfinished >= limits.unfinished()) {
            long seconds = SlidingWindowLimiter.seconds(limits.unfinishedRetryAfter());
            throw new RateLimitedException(seconds, "You already have " + unfinished
                    + " submissions being judged. Try again in " + seconds + " seconds.");
        }
        String addressKey = "oj:rl:submit-ip:" + address;
        Duration wait = limiter.check(addressKey, limits.submissionsPerAddress(), limits.addressWindow());
        if (!wait.isZero()) {
            long seconds = SlidingWindowLimiter.seconds(wait);
            throw new RateLimitedException(seconds,
                    "Too many submissions from your network. Try again in " + seconds + " seconds.");
        }
        wait = limiter.tryAcquire("oj:rl:submit:" + viewer.userId(), limits.submissions(), limits.window());
        if (!wait.isZero()) {
            long seconds = SlidingWindowLimiter.seconds(wait);
            throw new RateLimitedException(seconds, "Too many submissions. Try again in " + seconds + " seconds.");
        }

        long id = submissions.insert(viewer.userId(), problem.id(), language, sourceCode, problem.testdataVersion());
        limiter.record(addressKey, limits.addressWindow());
        statuses.markAttempted(viewer.userId(), problem.id());
        jobs.publishAfterCommit(id);
        return detail(viewer, id);
    }

    public SubmissionDetail detail(Viewer viewer, long id) {
        SubmissionRow row = submissions.find(viewer, id)
                .orElseThrow(() -> new NotFoundException("Submission not found."));
        return SubmissionDetail.of(row, submissions.testResults(id), viewer.readsSourceOf(row.userId()));
    }
}
