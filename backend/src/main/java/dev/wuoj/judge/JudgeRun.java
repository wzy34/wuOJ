package dev.wuoj.judge;

import dev.wuoj.common.domain.Verdict;
import org.jspecify.annotations.Nullable;

/** How a {@link JudgeEngine} reports the progress and result of one attempt. */
public interface JudgeRun {

    /** The result of one test as reported by an engine. */
    record TestOutcome(int testNumber, Verdict verdict, int timeMs, int memoryKb) {}

    /** Test {@code current} of {@code total} is about to run. */
    void running(int current, int total);

    void testFinished(TestOutcome outcome);

    /** Compilation failed: no tests ran. */
    void compileError(@Nullable String compileOutput);

    /**
     * Ends the attempt with the verdict; time and memory are the maximum over the tests reported so far.
     *
     * @param compileOutput compiler diagnostics (also warnings on success), or null
     */
    void finish(Verdict verdict, @Nullable String compileOutput);

    boolean isFinished();
}
