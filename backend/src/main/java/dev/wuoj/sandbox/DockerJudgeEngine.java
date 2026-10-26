package dev.wuoj.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import dev.wuoj.common.domain.Verdict;
import dev.wuoj.judge.JudgeEngine;
import dev.wuoj.judge.JudgeJob;
import dev.wuoj.judge.JudgeRun;
import dev.wuoj.testdata.TestDataStore;
import org.jspecify.annotations.Nullable;

/**
 * Judges in Docker: a one-shot compile container, then one run container per submission in which each test is a
 * {@code docker exec} of the runner. Per-submission files live in {@code <work-dir>/<id>-<attempt>/{work,out}};
 * submitted programs see the source and binary read-only, only the inputs of the problem's tests, and a writable
 * output directory. See design.md, decision 9.
 */
public class DockerJudgeEngine implements JudgeEngine {

    static final int COMPILE_TIME_MS = 10_000;
    static final int COMPILE_MEMORY_MB = 512;
    static final int COMPILE_OUTPUT_LIMIT = 64 * 1024;
    static final long OUTPUT_LIMIT_BYTES = 64L * 1024 * 1024;
    /** Above the problem's limit for the runner and /tmp, which count against the container's memory. */
    static final long MEMORY_HEADROOM_BYTES = 128L * 1024 * 1024;
    static final int JUDGE_UID = 1000;
    /** Margin on top of the runner's own wall-clock limit before the runner itself is considered stuck. */
    static final Duration EXEC_MARGIN = Duration.ofSeconds(10);

    static final List<String> COMPILE_COMMAND = List.of("/usr/bin/g++", "-std=gnu++17", "-O2", "-pipe", "-static",
            "-DONLINE_JUDGE", "-o", "main", "main.cpp");

    private final DockerSandbox sandbox;
    private final TestDataStore testdata;
    private final Path workRoot;
    private final @Nullable String runtime;

    public DockerJudgeEngine(DockerSandbox sandbox, TestDataStore testdata, Path workRoot, @Nullable String runtime) {
        this.sandbox = sandbox;
        this.testdata = testdata;
        this.workRoot = workRoot.toAbsolutePath().normalize();
        this.runtime = runtime;
    }

    public static String directoryName(long submissionId, int attempt) {
        return submissionId + "-" + attempt;
    }

    @Override
    public void judge(JudgeJob job, JudgeRun run) throws IOException {
        Path dir = workRoot.resolve(directoryName(job.submissionId(), job.attempt()));
        Path work = dir.resolve("work");
        Path out = dir.resolve("out");
        List<String> containers = new ArrayList<>();
        try {
            createWritableDirectory(work);
            createWritableDirectory(out);
            Files.writeString(work.resolve("main.cpp"), job.sourceCode(), StandardCharsets.UTF_8);

            Compilation compilation = compile(job, work, containers);
            if (!compilation.succeeded()) {
                run.compileError(compilation.output());
                return;
            }
            runTests(job, run, work, out, containers, compilation.output().isEmpty() ? null : compilation.output());
        } finally {
            for (String container : containers) {
                try {
                    sandbox.remove(container);
                } catch (RuntimeException e) {
                    // The cleaner removes it later.
                }
            }
            deleteRecursively(dir);
        }
    }

    /** The compiler's diagnostics: the error for a failed compilation, warnings (usually none) otherwise. */
    record Compilation(boolean succeeded, String output) {}

    private Compilation compile(JudgeJob job, Path work, List<String> containers) throws IOException {
        ContainerSpec spec = new ContainerSpec(containerName(job, "compile"),
                ContainerSpec.labels(job.submissionId(), job.attempt()),
                List.of(new ContainerSpec.Mount(work, "/work", true)),
                COMPILE_MEMORY_MB * 1024L * 1024 + MEMORY_HEADROOM_BYTES, 256, job.slot().cpuset(), runtime);
        String container = sandbox.start(spec);
        containers.add(container);

        List<String> args = new ArrayList<>(List.of(
                "--cpu-ms", Integer.toString(COMPILE_TIME_MS),
                "--wall-ms", Integer.toString(COMPILE_TIME_MS),
                "--fsize-bytes", Long.toString(256L * 1024 * 1024),
                "--nofile", "256",
                "--uid", Integer.toString(JUDGE_UID), "--gid", Integer.toString(JUDGE_UID),
                "--stdout", "/work/compile.log", "--stderr", "/work/compile.err", "--"));
        args.addAll(COMPILE_COMMAND);
        RunnerResult result = sandbox.run(container, args, Duration.ofMillis(COMPILE_TIME_MS).plus(EXEC_MARGIN));
        if (result.error() != null) throw new SandboxException("Could not start the compiler: " + result.error());
        sandbox.remove(container);
        containers.remove(container);

        String output = (readCompileLog(work.resolve("compile.log")) + readCompileLog(work.resolve("compile.err")))
                .strip();
        if (result.wallTimedOut() || result.cpuMs() > COMPILE_TIME_MS) {
            return new Compilation(false, append(output, "Compilation time limit (10 seconds) exceeded."));
        }
        if (result.oomKilled() || result.maxRssKb() > COMPILE_MEMORY_MB * 1024L) {
            return new Compilation(false, append(output, "Compilation memory limit (512 MB) exceeded."));
        }
        if (!result.exitedCleanly() || !Files.isRegularFile(work.resolve("main"))) {
            return new Compilation(false, output.isEmpty() ? "Compilation failed." : output);
        }
        return new Compilation(true, output);
    }

    private void runTests(JudgeJob job, JudgeRun run, Path work, Path out, List<String> containers,
            @Nullable String compileOutput) throws IOException {
        Path version = testdata.materialize(job.problemId(), job.testdataVersion());
        Path inputs = version.resolve("input");
        Path outputs = version.resolve("output");
        if (!Files.isDirectory(inputs)) throw new SandboxException("Test data missing: " + inputs);

        ContainerSpec spec = new ContainerSpec(containerName(job, "run"),
                ContainerSpec.labels(job.submissionId(), job.attempt()),
                List.of(new ContainerSpec.Mount(work, "/work", false),
                        new ContainerSpec.Mount(inputs, "/data", false),
                        new ContainerSpec.Mount(out, "/out", true)),
                job.memoryLimitMb() * 1024L * 1024 + MEMORY_HEADROOM_BYTES, 64, job.slot().cpuset(), runtime);
        String container = sandbox.start(spec);
        containers.add(container);

        int wallMs = 3 * job.timeLimitMs() + 1000;
        for (int test = 1; test <= job.testCount(); test++) {
            run.running(test, job.testCount());
            List<String> args = List.of(
                    "--cpu-ms", Integer.toString(job.timeLimitMs()),
                    "--wall-ms", Integer.toString(wallMs),
                    "--fsize-bytes", Long.toString(OUTPUT_LIMIT_BYTES),
                    "--stack-kb", Long.toString(job.memoryLimitMb() * 1024L),
                    "--nofile", "64",
                    "--uid", Integer.toString(JUDGE_UID), "--gid", Integer.toString(JUDGE_UID),
                    "--stdin", "/data/" + test + ".in",
                    "--stdout", "/out/" + test + ".out",
                    "--stderr", "/dev/null",
                    "--", "/work/main");
            RunnerResult result = sandbox.run(container, args, Duration.ofMillis(wallMs).plus(EXEC_MARGIN));
            if (result.error() != null) {
                throw new SandboxException("Could not run test " + test + ": " + result.error());
            }

            Path actual = out.resolve(test + ".out");
            Verdict verdict = VerdictClassifier.classify(result, job.timeLimitMs(), job.memoryLimitMb());
            if (verdict == null) {
                verdict = OutputChecker.matches(outputs.resolve(test + ".out"), actual) ? Verdict.AC : Verdict.WA;
            }
            Files.deleteIfExists(actual);
            run.testFinished(new JudgeRun.TestOutcome(test, verdict,
                    VerdictClassifier.reportedTimeMs(verdict, result, job.timeLimitMs()),
                    VerdictClassifier.reportedMemoryKb(verdict, result, job.memoryLimitMb())));
            if (verdict != Verdict.AC) {
                run.finish(verdict, compileOutput);
                return;
            }
        }
        run.finish(Verdict.AC, compileOutput);
    }

    private static String containerName(JudgeJob job, String purpose) {
        return "wuoj-" + directoryName(job.submissionId(), job.attempt()) + "-" + purpose;
    }

    private static String append(String output, String line) {
        return output.isEmpty() ? line : output + "\n" + line;
    }

    private static void createWritableDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        // The judge user (uid 1000) inside the container must be able to write here on a Linux host.
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
    }

    static String readCompileLog(Path log) throws IOException {
        if (!Files.exists(log)) return "";
        byte[] bytes;
        try (InputStream in = Files.newInputStream(log)) {
            bytes = in.readNBytes(COMPILE_OUTPUT_LIMIT + 1);
        }
        String text = new String(bytes, 0, Math.min(bytes.length, COMPILE_OUTPUT_LIMIT), StandardCharsets.UTF_8);
        if (bytes.length > COMPILE_OUTPUT_LIMIT) text += "\n[compiler output truncated to 64 KB]";
        return text;
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
