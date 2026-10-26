package dev.wuoj.problem;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.ProblemUserStatus;

/** A problem as seen by one user (status is that user's). */
public record ProblemRow(
        long id,
        String displayId,
        String slug,
        String title,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitMb,
        String statement,
        boolean visible,
        String testdataVersion,
        ProblemUserStatus status) {}
