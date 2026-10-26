package dev.wuoj.sandbox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ulimit;
import com.github.dockerjava.api.model.Volume;
import org.jspecify.annotations.Nullable;

/**
 * A judge container: no network, read-only root, a small /tmp, every capability dropped except the ones the runner
 * needs (switching to the unprivileged judge user, and killing it at the wall-clock limit), no privilege gain, and
 * limits on memory (no swap), processes and open files. Submitted programs run without any capability. See design.md,
 * decision 9.
 */
public record ContainerSpec(
        String name,
        Map<String, String> labels,
        List<Mount> mounts,
        long memoryBytes,
        long openFiles,
        @Nullable String cpuset,
        @Nullable String runtime) {

    /** Labels that mark judge containers, so leftovers can be found and removed. */
    public static final String JUDGE_LABEL = "wuoj.judge";
    public static final String SUBMISSION_LABEL = "wuoj.submission";
    public static final String ATTEMPT_LABEL = "wuoj.attempt";

    public static final long PIDS_LIMIT = 64;
    public static final String TMPFS_OPTIONS = "rw,nosuid,nodev,size=64m";

    public record Mount(Path hostPath, String containerPath, boolean writable) {}

    public static Map<String, String> labels(long submissionId, int attempt) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(JUDGE_LABEL, "true");
        labels.put(SUBMISSION_LABEL, Long.toString(submissionId));
        labels.put(ATTEMPT_LABEL, Integer.toString(attempt));
        return labels;
    }

    public HostConfig hostConfig() {
        List<Bind> binds = new ArrayList<>();
        for (Mount mount : mounts) {
            binds.add(new Bind(mount.hostPath().toString(), new Volume(mount.containerPath()),
                    mount.writable() ? AccessMode.rw : AccessMode.ro));
        }
        HostConfig config = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", TMPFS_OPTIONS))
                .withBinds(binds)
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withPidsLimit(PIDS_LIMIT)
                .withCapDrop(Capability.ALL)
                .withCapAdd(Capability.SETUID, Capability.SETGID, Capability.KILL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withUlimits(List.of(new Ulimit("nofile", openFiles, openFiles)))
                .withAutoRemove(false);
        if (cpuset != null) {
            config.withCpusetCpus(cpuset);
        } else {
            config.withNanoCPUs(1_000_000_000L);
        }
        if (runtime != null && !runtime.isBlank()) config.withRuntime(runtime);
        return config;
    }
}
