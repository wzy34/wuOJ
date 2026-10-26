package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import dev.wuoj.common.domain.Verdict;
import dev.wuoj.judge.JudgeRun.TestOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real C++ programs compiled and run in the judge image, one scenario of the judging spec each. */
@Tag("sandbox")
class JudgingVerdictsSandboxTest {

    @TempDir
    static Path temp;

    static SandboxHarness harness;
    static SandboxHarness.Problem aPlusB;

    @BeforeAll
    static void setUp() throws IOException {
        harness = new SandboxHarness(temp);
        aPlusB = harness.aPlusB(5);
    }

    @AfterAll
    static void tearDown() throws IOException {
        harness.close();
    }

    @Test
    void accepted() throws Exception {
        var result = harness.judge("ac.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.AC);
        assertThat(result.outcomes()).hasSize(5).allSatisfy(o -> {
            assertThat(o.verdict()).isEqualTo(Verdict.AC);
            assertThat(o.memoryKb()).isPositive();
        });
        assertThat(result.progress()).containsExactly(1, 2, 3, 4, 5);
        assertThat(result.compileOutput()).isNull();
    }

    @Test
    void wrongAnswerOnTestThree() throws Exception {
        var result = harness.judge("wa_on_three.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.outcomes()).extracting(TestOutcome::verdict)
                .containsExactly(Verdict.AC, Verdict.AC, Verdict.WA);
    }

    @Test
    void trailingWhitespaceIsTolerated() throws Exception {
        assertThat(harness.judge("trailing_whitespace.cpp", aPlusB).verdict()).isEqualTo(Verdict.AC);
    }

    @Test
    void syntaxError() throws Exception {
        var result = harness.judge("syntax_error.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.CE);
        assertThat(result.outcomes()).isEmpty();
        assertThat(result.compileOutput()).contains("main.cpp").contains("error");
    }

    @Test
    void missingMain() throws Exception {
        var result = harness.judge("no_main.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.CE);
        assertThat(result.compileOutput()).contains("undefined reference to `main'");
    }

    @Test
    void compilerBomb() throws Exception {
        var result = harness.judge("compile_bomb.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.CE);
        assertThat(result.compileOutput()).contains("Compilation time limit (10 seconds) exceeded.");
    }

    @Test
    void infiniteLoop() throws Exception {
        var result = harness.judge("infinite_loop.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.TLE);
        assertThat(result.outcomes()).singleElement().satisfies(o -> assertThat(o.timeMs()).isEqualTo(1000));
    }

    @Test
    void sleepingProgram() throws Exception {
        var result = harness.judge("sleep.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.TLE);
        assertThat(result.outcomes()).singleElement().satisfies(o -> assertThat(o.timeMs()).isEqualTo(1000));
    }

    @Test
    void largeAllocation() throws Exception {
        var result = harness.judge("big_alloc.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.MLE);
        assertThat(result.outcomes()).singleElement().satisfies(o -> assertThat(o.memoryKb()).isEqualTo(262_144));
    }

    @Test
    void endlessOutput() throws Exception {
        var result = harness.judge("endless_output.cpp", aPlusB);
        assertThat(result.verdict()).isIn(Verdict.OLE, Verdict.TLE);
    }

    @Test
    void crash() throws Exception {
        var result = harness.judge("null_deref.cpp", aPlusB);
        assertThat(result.verdict()).isEqualTo(Verdict.RE);
    }
}
