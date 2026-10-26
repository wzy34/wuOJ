package dev.wuoj.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.github.dockerjava.api.model.Container;
import dev.wuoj.judge.JudgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Removes judge containers and per-submission directories that are no longer needed: the submission finished, or
 * the attempt that created them lost its lease (its worker died). Runs at startup and then every minute.
 */
public class SandboxCleaner {

    private static final Logger log = LoggerFactory.getLogger(SandboxCleaner.class);
    private static final Pattern DIRECTORY = Pattern.compile("(\\d+)-(\\d+)");

    private final DockerSandbox sandbox;
    private final JudgeRepository judge;
    private final Path workRoot;

    public SandboxCleaner(DockerSandbox sandbox, JudgeRepository judge, Path workRoot) {
        this.sandbox = sandbox;
        this.judge = judge;
        this.workRoot = workRoot.toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        clean();
    }

    @Scheduled(initialDelayString = "${oj.judge.clean-interval:60s}",
            fixedDelayString = "${oj.judge.clean-interval:60s}")
    public void clean() {
        try {
            for (Container container : sandbox.judgeContainers()) {
                Map<String, String> labels = container.getLabels();
                if (!isLive(labels.get(ContainerSpec.SUBMISSION_LABEL), labels.get(ContainerSpec.ATTEMPT_LABEL))) {
                    log.info("Removing leftover judge container {}", String.join(",", container.getNames()));
                    sandbox.remove(container.getId());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not clean up judge containers: {}", e.getMessage());
        }
        if (!Files.isDirectory(workRoot)) return;
        try (Stream<Path> entries = Files.list(workRoot)) {
            for (Path dir : entries.toList()) {
                Matcher m = DIRECTORY.matcher(dir.getFileName().toString());
                if (m.matches() && !isLive(m.group(1), m.group(2))) {
                    log.info("Removing leftover judge directory {}", dir);
                    DockerJudgeEngine.deleteRecursively(dir);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not clean up judge directories: {}", e.getMessage());
        }
    }

    private boolean isLive(String submission, String attempt) {
        try {
            return judge.isLiveAttempt(Long.parseLong(submission), Integer.parseInt(attempt));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
