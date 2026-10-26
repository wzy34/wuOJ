package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.github.dockerjava.api.DockerClient;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.importer.ProblemPackage;
import dev.wuoj.judge.JudgeJob;
import dev.wuoj.judge.JudgeRun;
import dev.wuoj.judge.JudgeSlots;
import dev.wuoj.testdata.FileSystemTestDataStore;
import dev.wuoj.testdata.TestCaseFiles;
import dev.wuoj.testdata.TestDataStore;
import org.jspecify.annotations.Nullable;

/** The real Docker engine without Spring or a database, for the sandbox tests. */
final class SandboxHarness implements AutoCloseable {

    static final String IMAGE = System.getProperty("oj.judge.image", "wuoj/judge-cpp:dev");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() % 1_000_000 * 1000);

    final DockerClient docker = SandboxConfig.createClient(SandboxConfig.dockerHost(null));
    final DockerSandbox sandbox = new DockerSandbox(docker, IMAGE);
    final Path workRoot;
    final TestDataStore store;
    final DockerJudgeEngine engine;

    SandboxHarness(Path temp) throws IOException {
        workRoot = Files.createDirectories(temp.resolve("judge"));
        store = new FileSystemTestDataStore(temp.resolve("testdata"));
        engine = new DockerJudgeEngine(sandbox, store, workRoot, null);
    }

    /** A problem whose test n has input {@code "n n"} and expects {@code 2n}, like A + B. */
    Problem aPlusB(int tests) throws IOException {
        List<String[]> cases = new ArrayList<>();
        for (int n = 1; n <= tests; n++) cases.add(new String[] {n + " " + n + "\n", (2 * n) + "\n"});
        return problem(cases);
    }

    /** A problem whose every test expects the same output, e.g. "isolated". */
    Problem expecting(String output, int tests) throws IOException {
        List<String[]> cases = new ArrayList<>();
        for (int n = 1; n <= tests; n++) cases.add(new String[] {n + "\n", output + "\n"});
        return problem(cases);
    }

    /** A problem with the tests of a package. */
    Problem problem(ProblemPackage pkg) throws IOException {
        long id = IDS.incrementAndGet();
        String version = TestDataStore.version(pkg.tests());
        store.store(id, version, pkg.tests());
        return new Problem(id, version, pkg.tests().size());
    }

    Problem problem(List<String[]> cases) throws IOException {
        long id = IDS.incrementAndGet();
        Path dir = Files.createTempDirectory("cases");
        List<TestCaseFiles> tests = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            Path in = Files.writeString(dir.resolve((i + 1) + ".in"), cases.get(i)[0]);
            Path out = Files.writeString(dir.resolve((i + 1) + ".out"), cases.get(i)[1]);
            tests.add(new TestCaseFiles(i + 1, in, out));
        }
        String version = TestDataStore.version(tests);
        store.store(id, version, tests);
        return new Problem(id, version, tests.size());
    }

    record Problem(long id, String version, int tests) {}

    static String program(String name) {
        try (InputStream in = SandboxHarness.class.getResourceAsStream("/programs/" + name)) {
            if (in == null) throw new IllegalArgumentException("No test program " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Result judge(String program, Problem problem) throws Exception {
        return judge(program, problem, 1000, 256, null);
    }

    Result judge(String program, Problem problem, int timeLimitMs, int memoryLimitMb, @Nullable String cpuset)
            throws Exception {
        return judgeSource(program(program), problem, timeLimitMs, memoryLimitMb, cpuset);
    }

    Result judgeSource(String source, Problem problem, int timeLimitMs, int memoryLimitMb, @Nullable String cpuset)
            throws Exception {
        long submissionId = IDS.incrementAndGet();
        RecordingRun run = new RecordingRun();
        engine.judge(new JudgeJob(submissionId, 1, source, problem.id(), problem.version(), problem.tests(),
                timeLimitMs, memoryLimitMb, new JudgeSlots.Slot(0, cpuset)), run);
        assertThat(run.isFinished()).as("the engine reported a verdict").isTrue();
        assertThat(leftoverContainers(submissionId)).as("containers left behind").isZero();
        assertThat(workRoot.resolve(DockerJudgeEngine.directoryName(submissionId, 1))).doesNotExist();
        return new Result(submissionId, run.verdict, run.outcomes, run.compileOutput, run.progress);
    }

    long leftoverContainers(long submissionId) {
        return sandbox.judgeContainers().stream()
                .filter(c -> String.valueOf(submissionId).equals(c.getLabels().get(ContainerSpec.SUBMISSION_LABEL)))
                .count();
    }

    record Result(long submissionId, Verdict verdict, List<JudgeRun.TestOutcome> outcomes,
            @Nullable String compileOutput, List<Integer> progress) {

        int maxTimeMs() {
            return outcomes.stream().mapToInt(JudgeRun.TestOutcome::timeMs).max().orElse(0);
        }
    }

    /** Records what the engine reports. */
    static final class RecordingRun implements JudgeRun {
        final List<TestOutcome> outcomes = new ArrayList<>();
        final List<Integer> progress = new ArrayList<>();
        Verdict verdict;
        String compileOutput;

        @Override
        public void running(int current, int total) {
            progress.add(current);
        }

        @Override
        public void testFinished(TestOutcome outcome) {
            outcomes.add(outcome);
        }

        @Override
        public void compileError(@Nullable String output) {
            finish(Verdict.CE, output);
        }

        @Override
        public void finish(Verdict verdict, @Nullable String output) {
            assertThat(this.verdict).as("finished only once").isNull();
            this.verdict = verdict;
            this.compileOutput = output;
        }

        @Override
        public boolean isFinished() {
            return verdict != null;
        }
    }

    @Override
    public void close() throws IOException {
        docker.close();
    }
}
