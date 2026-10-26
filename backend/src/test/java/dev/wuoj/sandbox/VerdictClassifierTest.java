package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wuoj.common.domain.Verdict;
import org.junit.jupiter.api.Test;

class VerdictClassifierTest {

    private static RunnerResult result(int exit, int signal, long cpu, long rss, boolean wallTimedOut) {
        return new RunnerResult(exit, signal, cpu, cpu + 1, rss, wallTimedOut, false, null);
    }

    private static RunnerResult oomKilled(long rss) {
        return new RunnerResult(0, 9, 10, 11, rss, false, true, null);
    }

    private static Verdict classify(RunnerResult r) {
        return VerdictClassifier.classify(r, 1000, 256);
    }

    @Test
    void cleanRunIsLeftToTheChecker() {
        assertThat(classify(result(0, 0, 10, 3000, false))).isNull();
    }

    @Test
    void cpuOverTheLimitOrWallTimeoutIsTle() {
        assertThat(classify(result(0, 0, 1001, 3000, false))).isEqualTo(Verdict.TLE);
        assertThat(classify(result(0, 24, 2100, 3000, false))).isEqualTo(Verdict.TLE); // SIGXCPU
        assertThat(classify(result(0, 9, 0, 1500, true))).isEqualTo(Verdict.TLE); // sleeping
    }

    @Test
    void tleWinsOverEverythingElse() {
        assertThat(classify(result(0, 9, 1500, 900_000, false))).isEqualTo(Verdict.TLE);
        assertThat(classify(result(0, 25, 1500, 3000, false))).isEqualTo(Verdict.TLE);
    }

    @Test
    void memoryOverTheLimitOrOomKillIsMle() {
        assertThat(classify(result(0, 0, 10, 262_145, false))).isEqualTo(Verdict.MLE);
        assertThat(classify(oomKilled(200_000))).isEqualTo(Verdict.MLE);
        assertThat(classify(result(139, 0, 10, 262_145, false))).isEqualTo(Verdict.MLE);
    }

    @Test
    void outputLimitIsOle() {
        assertThat(classify(result(0, 25, 10, 3000, false))).isEqualTo(Verdict.OLE);
    }

    @Test
    void crashesAndNonZeroExitAreRe() {
        assertThat(classify(result(0, 11, 10, 3000, false))).isEqualTo(Verdict.RE); // SIGSEGV
        assertThat(classify(result(0, 6, 10, 3000, false))).isEqualTo(Verdict.RE); // SIGABRT
        // Killed, but not by the out-of-memory killer (e.g. the program killed itself).
        assertThat(classify(result(0, 9, 10, 3000, false))).isEqualTo(Verdict.RE);
        assertThat(classify(result(1, 0, 10, 3000, false))).isEqualTo(Verdict.RE);
    }

    @Test
    void reportedMeasurements() {
        RunnerResult slow = result(0, 9, 0, 1500, true);
        assertThat(VerdictClassifier.reportedTimeMs(Verdict.TLE, slow, 1000)).isEqualTo(1000);
        RunnerResult big = oomKilled(300_000);
        assertThat(VerdictClassifier.reportedMemoryKb(Verdict.MLE, big, 256)).isEqualTo(262_144);
        RunnerResult fine = result(0, 0, 12, 3184, false);
        assertThat(VerdictClassifier.reportedTimeMs(Verdict.AC, fine, 1000)).isEqualTo(12);
        assertThat(VerdictClassifier.reportedMemoryKb(Verdict.AC, fine, 256)).isEqualTo(3184);
    }

    @Test
    void parsesTheRunnerLine() {
        RunnerResult r = RunnerResult.parse("{\"exitCode\":1,\"signal\":0,\"cpuMs\":3,\"wallMs\":4,\"maxRssKb\":700,"
                + "\"wallTimedOut\":false,\"oomKilled\":false,\"error\":null}\n");
        assertThat(r).isEqualTo(new RunnerResult(1, 0, 3, 4, 700, false, false, null));
    }
}
