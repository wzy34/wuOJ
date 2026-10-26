package dev.wuoj.submission;

import java.time.Instant;

import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * A submission with its user and problem. {@code sourceCode} and {@code compileOutput} are only loaded for single
 * submissions, never for lists.
 */
public record SubmissionRow(
        long id,
        long userId,
        String username,
        long problemId,
        String problemDisplayId,
        String problemSlug,
        String problemTitle,
        Language language,
        SubmissionStatus status,
        @Nullable Verdict verdict,
        @Nullable Integer timeMs,
        @Nullable Integer memoryKb,
        @Nullable Integer currentTest,
        @Nullable Integer totalTests,
        Instant createdAt,
        long eventSeq,
        @Nullable String sourceCode,
        @Nullable String compileOutput) {}
