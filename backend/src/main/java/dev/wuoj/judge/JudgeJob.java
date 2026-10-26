package dev.wuoj.judge;

/** Everything a judge engine needs to judge one attempt of a submission. */
public record JudgeJob(
        long submissionId,
        int attempt,
        String sourceCode,
        long problemId,
        String testdataVersion,
        int testCount,
        int timeLimitMs,
        int memoryLimitMb,
        JudgeSlots.Slot slot) {}
