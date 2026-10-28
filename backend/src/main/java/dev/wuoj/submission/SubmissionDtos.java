package dev.wuoj.submission;

import java.time.Instant;
import java.util.List;

import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;

/** The contract's submission schemas. Null fields are omitted from JSON. */
public final class SubmissionDtos {

    private SubmissionDtos() {}

    public record UserRef(long id, String username) {}

    public record ProblemRef(long id, String displayId, String slug, String title) {}

    public record TestResult(int testNumber, Verdict verdict, int timeMs, int memoryKb) {}

    public record SubmissionSummary(long id, UserRef user, ProblemRef problem, Language language,
            SubmissionStatus status, @Nullable Verdict verdict, @Nullable Integer timeMs, @Nullable Integer memoryKb,
            Instant createdAt) {

        public static SubmissionSummary of(SubmissionRow row) {
            return new SubmissionSummary(row.id(), userRef(row), problemRef(row), row.language(), row.status(),
                    row.verdict(), row.timeMs(), row.memoryKb(), row.createdAt());
        }
    }

    public record SubmissionDetail(long id, UserRef user, ProblemRef problem, Language language,
            SubmissionStatus status, @Nullable Verdict verdict, @Nullable Integer timeMs, @Nullable Integer memoryKb,
            Instant createdAt, @Nullable Integer currentTest, @Nullable Integer totalTests,
            List<TestResult> testResults, @Nullable String sourceCode, @Nullable String compileOutput) {

        /** Source code and compiler output only if the viewer may read them; compiler output only if there is any. */
        public static SubmissionDetail of(SubmissionRow row, List<TestResultRow> results, boolean withSource) {
            String compileOutput = withSource && row.compileOutput() != null && !row.compileOutput().isEmpty()
                    ? row.compileOutput()
                    : null;
            return new SubmissionDetail(row.id(), userRef(row), problemRef(row), row.language(), row.status(),
                    row.verdict(), row.timeMs(), row.memoryKb(), row.createdAt(),
                    row.status() == SubmissionStatus.RUNNING ? row.currentTest() : null, row.totalTests(),
                    results.stream()
                            .map(r -> new TestResult(r.testNumber(), r.verdict(), r.timeMs(), r.memoryKb()))
                            .toList(),
                    withSource ? row.sourceCode() : null, compileOutput);
        }
    }

    /** The {@code data} of a {@code status} event on the live stream. */
    public record SubmissionStatusEvent(long submissionId, SubmissionStatus status, @Nullable Integer currentTest,
            @Nullable Integer totalTests, @Nullable Verdict verdict, @Nullable Integer timeMs,
            @Nullable Integer memoryKb) {

        public static SubmissionStatusEvent of(SubmissionRow row) {
            return new SubmissionStatusEvent(row.id(), row.status(),
                    row.status() == SubmissionStatus.RUNNING ? row.currentTest() : null, row.totalTests(),
                    row.verdict(), row.timeMs(), row.memoryKb());
        }
    }

    private static UserRef userRef(SubmissionRow row) {
        return new UserRef(row.userId(), row.username());
    }

    private static ProblemRef problemRef(SubmissionRow row) {
        return new ProblemRef(row.problemId(), row.problemDisplayId(), row.problemSlug(), row.problemTitle());
    }
}
