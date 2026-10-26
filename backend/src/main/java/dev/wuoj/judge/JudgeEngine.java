package dev.wuoj.judge;

/**
 * Judges one attempt: compiles, runs the tests in order, and reports progress and the result through the
 * {@link JudgeRun}. Throwing means the infrastructure failed (not the program); the attempt is then retried.
 */
public interface JudgeEngine {

    void judge(JudgeJob job, JudgeRun run) throws Exception;
}
