package dev.wuoj.judge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wuoj.common.domain.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The same rules as the web app's mock judge ({@code frontend/src/mocks/mockJudge.ts}). */
class SimulatedJudgeEngineTest {

    private static final JudgeJob JOB = new JudgeJob(7, 1, "", 1, "v", 12, 1000, 256, new JudgeSlots.Slot(0, null));

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"WA", "TLE", "MLE", "RE", "OLE"})
    void markerFailsOnTestThree(Verdict verdict) {
        var plan = SimulatedJudgeEngine.plan("int main() {} // @mock-verdict: " + verdict, 12);
        assertThat(plan.verdict()).isEqualTo(verdict);
        assertThat(plan.failAt()).isEqualTo(3);
    }

    @Test
    void failsOnTheLastTestWhenThereAreFewerThanThree() {
        assertThat(SimulatedJudgeEngine.plan("int main() {} // @mock-verdict: WA", 2).failAt()).isEqualTo(2);
    }

    @Test
    void systemErrorOnTestOne() {
        assertThat(SimulatedJudgeEngine.plan("int main(){} //@mock-verdict:SE", 12).failAt()).isEqualTo(1);
    }

    @Test
    void forcedCompilationError() {
        var plan = SimulatedJudgeEngine.plan("int main() {} // @mock-verdict: CE", 12);
        assertThat(plan.verdict()).isEqualTo(Verdict.CE);
        assertThat(plan.compileOutput()).contains("compilation failed as requested by @mock-verdict");
    }

    @Test
    void missingMainIsACompilationError() {
        var plan = SimulatedJudgeEngine.plan("int helper() { return 1; }", 12);
        assertThat(plan.verdict()).isEqualTo(Verdict.CE);
        assertThat(plan.compileOutput()).contains("undefined reference to `main'");
    }

    @Test
    void markerWinsOverMissingMain() {
        assertThat(SimulatedJudgeEngine.plan("// @mock-verdict: AC", 12).verdict()).isEqualTo(Verdict.AC);
    }

    @Test
    void plainCodeIsAccepted() {
        var plan = SimulatedJudgeEngine.plan("int main ( ) { return 0; }", 12);
        assertThat(plan.verdict()).isEqualTo(Verdict.AC);
        assertThat(plan.failAt()).isNull();
    }

    @Test
    void measurementsFollowTheMockFormula() {
        assertThat(SimulatedJudgeEngine.outcome(JOB, 2, Verdict.AC))
                .isEqualTo(new JudgeRun.TestOutcome(2, Verdict.AC, 2 + (7 * 7 + 2 * 13) % 60,
                        3200 + (7 * 31 + 2 * 17) % 1800));
        assertThat(SimulatedJudgeEngine.outcome(JOB, 3, Verdict.TLE).timeMs()).isEqualTo(1000);
        assertThat(SimulatedJudgeEngine.outcome(JOB, 3, Verdict.MLE).memoryKb()).isEqualTo(256 * 1024);
        assertThat(SimulatedJudgeEngine.outcome(JOB, 1, Verdict.SE).timeMs()).isZero();
    }
}
