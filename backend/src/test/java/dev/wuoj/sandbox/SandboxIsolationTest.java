package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import dev.wuoj.common.domain.Verdict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The judge-sandbox spec: what submitted programs cannot do. */
@Tag("sandbox")
class SandboxIsolationTest {

    @TempDir
    static Path temp;

    static SandboxHarness harness;
    static SandboxHarness.Problem aPlusB;

    @BeforeAll
    static void setUp() throws IOException {
        harness = new SandboxHarness(temp);
        aPlusB = harness.aPlusB(3);
    }

    @AfterAll
    static void tearDown() throws IOException {
        harness.close();
    }

    @Test
    void noNetwork() throws Exception {
        var result = harness.judge("network.cpp", harness.expecting("isolated", 1));
        assertThat(result.verdict()).as("every connection attempt fails").isEqualTo(Verdict.AC);
    }

    @Test
    void forkBombIsContainedAndOthersAreUnaffected() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var bomb = CompletableFuture.supplyAsync(() -> judge("fork_bomb.cpp", aPlusB, "1"), pool);
            var normal = CompletableFuture.supplyAsync(() -> judge("slow_ac.cpp", aPlusB, "2"), pool);
            assertThat(bomb.get().verdict()).isIn(Verdict.RE, Verdict.TLE);
            assertThat(normal.get().verdict()).isEqualTo(Verdict.AC);
        }
    }

    @Test
    void writableSpaceIsCapped() throws Exception {
        long freeBefore = Files.getFileStore(temp).getUsableSpace();
        var result = harness.judge("fill_tmp.cpp", harness.expecting("capped", 1));
        assertThat(result.verdict()).isEqualTo(Verdict.AC);
        long freeAfter = Files.getFileStore(temp).getUsableSpace();
        assertThat(freeBefore - freeAfter).isLessThan(100L << 20);
    }

    @Test
    void expectedOutputsCannotBeFound() throws Exception {
        var result = harness.judge("find_expected.cpp", aPlusB);
        assertThat(result.verdict()).isIn(Verdict.WA, Verdict.RE);
    }

    @Test
    void otherSubmissionsAreInvisible() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var other = CompletableFuture.supplyAsync(() -> judge("slow_ac.cpp", aPlusB, "1"), pool);
            Thread.sleep(800); // the other submission's files and containers exist now
            var searcher = judge("find_sources.cpp", expectingOne(), "2");
            assertThat(searcher.verdict()).as("only its own main.cpp is visible").isEqualTo(Verdict.AC);
            assertThat(other.get().verdict()).isEqualTo(Verdict.AC);
        }
    }

    @Test
    void killingEveryProcessCannotReachTheSupervisor() throws Exception {
        var result = harness.judge("kill_all.cpp", aPlusB);
        // Measured and judged on its own outcome (no output), not a System Error.
        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.outcomes()).singleElement().satisfies(o -> assertThat(o.timeMs()).isLessThan(1000));
    }

    @Test
    void runsUnprivileged() throws Exception {
        var result = harness.judge("whoami.cpp", harness.expecting("1000 0000000000000000 1", 1));
        assertThat(result.verdict()).as("uid 1000, no capabilities, no privilege gain").isEqualTo(Verdict.AC);
    }

    @Test
    void nothingIsLeftBehind() throws Exception {
        for (int i = 0; i < 20; i++) {
            harness.judge(i % 2 == 0 ? "ac.cpp" : "syntax_error.cpp", aPlusB);
        }
        try (var entries = Files.list(harness.workRoot)) {
            assertThat(entries.toList()).isEmpty();
        }
        assertThat(harness.sandbox.judgeContainers()).noneMatch(c -> c.getNames()[0].contains(
                "wuoj-" + aPlusB.id()));
    }

    @Test
    void containersAreConfiguredAsDesigned() throws Exception {
        Path work = Files.createDirectories(temp.resolve("inspect/work"));
        Path data = Files.createDirectories(temp.resolve("inspect/data"));
        ContainerSpec spec = new ContainerSpec("wuoj-inspect-1-run", ContainerSpec.labels(999_999_999L, 1),
                List.of(new ContainerSpec.Mount(work, "/work", false), new ContainerSpec.Mount(data, "/data", false),
                        new ContainerSpec.Mount(temp, "/out", true)),
                384L << 20, 64, "1", null);
        String id = harness.sandbox.start(spec);
        try {
            InspectContainerResponse inspected = harness.docker.inspectContainerCmd(id).exec();
            HostConfig host = inspected.getHostConfig();
            assertThat(host.getNetworkMode()).isEqualTo("none");
            assertThat(host.getReadonlyRootfs()).isTrue();
            assertThat(host.getTmpFs()).isEqualTo(Map.of("/tmp", ContainerSpec.TMPFS_OPTIONS));
            assertThat(host.getMemory()).isEqualTo(384L << 20);
            assertThat(host.getMemorySwap()).isEqualTo(384L << 20);
            assertThat(host.getPidsLimit()).isEqualTo(ContainerSpec.PIDS_LIMIT);
            assertThat(host.getCpusetCpus()).isEqualTo("1");
            assertThat(host.getCapDrop()).containsExactly(Capability.ALL);
            assertThat(host.getCapAdd()).containsExactlyInAnyOrder(Capability.SETUID, Capability.SETGID,
                    Capability.KILL);
            assertThat(host.getSecurityOpts()).containsExactly("no-new-privileges");
            assertThat(host.getUlimits()).singleElement().satisfies(u -> {
                assertThat(u.getName()).isEqualTo("nofile");
                assertThat(u.getHardLong()).isEqualTo(64);
            });
            assertThat(host.getBinds()).extracting(Bind::getPath, b -> b.getVolume().getPath(), Bind::getAccessMode)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(work.toString(), "/work", AccessMode.ro),
                            org.assertj.core.groups.Tuple.tuple(data.toString(), "/data", AccessMode.ro),
                            org.assertj.core.groups.Tuple.tuple(temp.toString(), "/out", AccessMode.rw));
            assertThat(inspected.getConfig().getLabels()).containsEntry(ContainerSpec.JUDGE_LABEL, "true")
                    .containsEntry(ContainerSpec.SUBMISSION_LABEL, "999999999");
        } finally {
            harness.sandbox.remove(id);
        }
    }

    private static SandboxHarness.Problem expectingOne() {
        try {
            return harness.expecting("1", 1);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static SandboxHarness.Result judge(String program, SandboxHarness.Problem problem, String cpuset) {
        try {
            return harness.judge(program, problem, 1000, 256, cpuset);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
