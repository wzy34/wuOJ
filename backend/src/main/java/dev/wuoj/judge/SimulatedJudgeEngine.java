package dev.wuoj.judge;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * Judges without compiling or running anything, for development and tests. A port of the web app's mock judge
 * ({@code frontend/src/mocks/mockJudge.ts}) so both behave the same: {@code // @mock-verdict: TLE} (or any verdict)
 * forces that verdict, code without {@code main(} is a compilation error, anything else is accepted.
 */
public class SimulatedJudgeEngine implements JudgeEngine {

    private static final Pattern VERDICT_MARKER = Pattern.compile("@mock-verdict:\\s*(AC|WA|TLE|MLE|RE|CE|OLE|SE)\\b");
    private static final Pattern MAIN = Pattern.compile("\\bmain\\s*\\(");

    static final String FORCED_CE_OUTPUT = "main.cpp: In function 'int main()':\n"
            + "main.cpp:1:1: error: compilation failed as requested by @mock-verdict";
    static final String MISSING_MAIN_OUTPUT = "/usr/bin/ld: in function `_start':\n"
            + "(.text+0x1b): undefined reference to `main'\ncollect2: error: ld returned 1 exit status";

    /** The verdict to produce and, unless accepted, the test that fails. */
    record Plan(Verdict verdict, @Nullable Integer failAt, @Nullable String compileOutput) {}

    private final Duration step;

    public SimulatedJudgeEngine(Duration step) {
        this.step = step;
    }

    static Plan plan(String sourceCode, int testCount) {
        Matcher marker = VERDICT_MARKER.matcher(sourceCode);
        Verdict forced = marker.find() ? Verdict.valueOf(marker.group(1)) : null;
        if (forced == Verdict.CE) return new Plan(Verdict.CE, null, FORCED_CE_OUTPUT);
        if (forced == null && !MAIN.matcher(sourceCode).find()) return new Plan(Verdict.CE, null, MISSING_MAIN_OUTPUT);
        Verdict verdict = forced == null ? Verdict.AC : forced;
        if (verdict == Verdict.AC) return new Plan(verdict, null, null);
        if (verdict == Verdict.SE) return new Plan(verdict, 1, null);
        return new Plan(verdict, Math.min(3, testCount), null);
    }

    /** Deterministic, plausible measurements, the same formula as the mock judge. */
    static JudgeRun.TestOutcome outcome(JudgeJob job, int testNumber, Verdict verdict) {
        long id = job.submissionId();
        int timeMs = (int) (2 + ((id * 7 + testNumber * 13) % 60));
        int memoryKb = (int) (3200 + ((id * 31 + testNumber * 17) % 1800));
        if (verdict == Verdict.TLE) timeMs = job.timeLimitMs();
        if (verdict == Verdict.MLE) memoryKb = job.memoryLimitMb() * 1024;
        if (verdict == Verdict.SE) timeMs = 0;
        return new JudgeRun.TestOutcome(testNumber, verdict, timeMs, memoryKb);
    }

    @Override
    public void judge(JudgeJob job, JudgeRun run) throws InterruptedException {
        Plan plan = plan(job.sourceCode(), job.testCount());
        pause();
        if (plan.verdict() == Verdict.CE) {
            run.compileError(plan.compileOutput());
            return;
        }
        for (int test = 1; test <= job.testCount(); test++) {
            run.running(test, job.testCount());
            pause();
            boolean failed = plan.failAt() != null && test == plan.failAt();
            run.testFinished(outcome(job, test, failed ? plan.verdict() : Verdict.AC));
            if (failed) {
                run.finish(plan.verdict(), null);
                return;
            }
        }
        run.finish(Verdict.AC, null);
    }

    private void pause() throws InterruptedException {
        if (!step.isZero()) Thread.sleep(step);
    }
}
