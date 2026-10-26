package dev.wuoj.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

/** The few Docker operations judging needs, with Docker failures turned into {@link SandboxException}s. */
public class DockerSandbox {

    static final String RUNNER = "/usr/local/bin/oj-runner";

    private final DockerClient docker;
    private final String image;

    public DockerSandbox(DockerClient docker, String image) {
        this.docker = docker;
        this.image = image;
    }

    public String image() {
        return image;
    }

    /** Creates and starts a container that idles until commands are run in it with {@link #exec}. */
    public String start(ContainerSpec spec) {
        try {
            remove(spec.name());
            CreateContainerResponse created = docker.createContainerCmd(image)
                    .withName(spec.name())
                    .withLabels(spec.labels())
                    .withCmd(RUNNER, "idle")
                    .withUser("0:0")
                    .withWorkingDir("/work")
                    .withNetworkDisabled(true)
                    .withHostConfig(spec.hostConfig())
                    .exec();
            docker.startContainerCmd(created.getId()).exec();
            return created.getId();
        } catch (RuntimeException e) {
            throw new SandboxException("Could not start judge container " + spec.name(), e);
        }
    }

    /** Runs {@code oj-runner run <args>} in the container and returns its report. */
    public RunnerResult run(String containerId, List<String> runnerArgs, Duration timeout) {
        String[] command = new String[runnerArgs.size() + 2];
        command[0] = RUNNER;
        command[1] = "run";
        for (int i = 0; i < runnerArgs.size(); i++) command[i + 2] = runnerArgs.get(i);
        try {
            String execId = docker.execCreateCmd(containerId)
                    .withCmd(command)
                    .withWorkingDir("/work")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec()
                    .getId();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            ResultCallback.Adapter<Frame> callback = docker.execStartCmd(execId).exec(new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    byte[] payload = frame.getPayload();
                    if (frame.getStreamType() == StreamType.STDERR) stderr.writeBytes(payload);
                    else stdout.writeBytes(payload);
                }
            });
            if (!callback.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                callback.close();
                throw new SandboxException("The runner did not finish within " + timeout);
            }
            String output = stdout.toString(StandardCharsets.UTF_8);
            if (output.isBlank()) {
                throw new SandboxException("The runner printed nothing; stderr: "
                        + stderr.toString(StandardCharsets.UTF_8));
            }
            return RunnerResult.parse(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while judging", e);
        } catch (SandboxException e) {
            throw e;
        } catch (Exception e) {
            throw new SandboxException("Could not run the runner in " + containerId, e);
        }
    }

    /** Removes a container (by id or name) if it exists, whatever its state. */
    public void remove(String containerIdOrName) {
        try {
            docker.removeContainerCmd(containerIdOrName).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException e) {
            // Already gone.
        }
    }

    /** Every judge container on this Docker host, running or not. */
    public List<Container> judgeContainers() {
        return docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(ContainerSpec.JUDGE_LABEL, "true"))
                .exec();
    }

    public boolean imagePresent() {
        try {
            docker.inspectImageCmd(image).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    public String serverVersion() {
        return docker.versionCmd().exec().getVersion();
    }
}
