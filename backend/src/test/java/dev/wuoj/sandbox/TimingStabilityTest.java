package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.wuoj.common.domain.Verdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The judging spec's measurement stability: the same program judged repeatedly on a pinned core, while another
 * submission is judged on another core, reports CPU times within 20% or 10 ms of each other.
 */
@Tag("sandbox")
class TimingStabilityTest {

    @Test
    void repeatedRunsReportStableTimes(@TempDir Path temp) throws Exception {
        try (SandboxHarness harness = new SandboxHarness(temp)) {
            SandboxHarness.Problem problem = harness.aPlusB(1);
            List<Integer> times = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                CompletableFuture<SandboxHarness.Result> neighbour = CompletableFuture.supplyAsync(() -> {
                    try {
                        return harness.judge("busy_200ms.cpp", problem, 5000, 256, "4");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
                SandboxHarness.Result result = harness.judge("busy_200ms.cpp", problem, 5000, 256, "3");
                assertThat(result.verdict()).isEqualTo(Verdict.AC);
                assertThat(neighbour.get().verdict()).isEqualTo(Verdict.AC);
                times.add(result.maxTimeMs());
            }
            IntSummaryStatistics stats = times.stream().mapToInt(Integer::intValue).summaryStatistics();
            double tolerance = Math.max(0.2 * stats.getAverage(), 10);
            System.out.printf("CPU times (ms): %s, spread %d ms, tolerance %.0f ms%n", times,
                    stats.getMax() - stats.getMin(), tolerance);
            assertThat((double) stats.getMax() - stats.getMin()).isLessThanOrEqualTo(tolerance);
        }
    }
}
