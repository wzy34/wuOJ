package dev.wuoj.problem;

import dev.wuoj.common.domain.Difficulty;

/** The editable content of a problem, as imported from a package. */
public record ProblemData(
        String displayId,
        String slug,
        String title,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitMb,
        String statement,
        boolean visible) {}
