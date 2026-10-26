package dev.wuoj.sandbox;

import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * Decides a test's verdict from the runner's report, by the first matching rule (see the judging spec):
 * TLE, then MLE, then OLE, then RE. A null result means the program ran cleanly and its output must be checked.
 */
public final class VerdictClassifier {

    /** Raised when RLIMIT_FSIZE is exceeded; the same number on x86-64 and arm64. */
    static final int SIGXFSZ = 25;

    private VerdictClassifier() {}

    public static @Nullable Verdict classify(RunnerResult result, int timeLimitMs, int memoryLimitMb) {
        if (result.cpuMs() > timeLimitMs || result.wallTimedOut()) return Verdict.TLE;
        if (result.maxRssKb() > memoryLimitMb * 1024L || result.oomKilled()) return Verdict.MLE;
        if (result.signal() == SIGXFSZ) return Verdict.OLE;
        if (!result.exitedCleanly()) return Verdict.RE;
        return null;
    }

    /** The time to report: the limit for TLE (the run was cut short), else the CPU time used. */
    public static int reportedTimeMs(Verdict verdict, RunnerResult result, int timeLimitMs) {
        return verdict == Verdict.TLE ? timeLimitMs : (int) Math.min(result.cpuMs(), timeLimitMs);
    }

    /** The memory to report: the limit for MLE, else the peak resident memory. */
    public static int reportedMemoryKb(Verdict verdict, RunnerResult result, int memoryLimitMb) {
        return verdict == Verdict.MLE ? memoryLimitMb * 1024 : (int) Math.min(result.maxRssKb(), memoryLimitMb * 1024L);
    }
}
