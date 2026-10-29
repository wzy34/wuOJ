package dev.wuoj.testdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.config.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes what an interrupted test save can leave behind: staged uploads, and half-written versions
 * ({@code <problem id>/.tmp-*}). Both are normally deleted by the request that made them, so only entries older than
 * an hour are touched. Runs in API processes, which write test data, at startup and then hourly.
 */
@Component
@ConditionalOnRole(Role.API)
public class TestDataStagingCleaner {

    static final Duration MAX_AGE = Duration.ofHours(1);
    private static final Logger log = LoggerFactory.getLogger(TestDataStagingCleaner.class);

    private final Path root;
    private final Path staging;

    public TestDataStagingCleaner(OjProperties properties) {
        this.root = properties.testdata().root().toAbsolutePath().normalize();
        this.staging = properties.testdata().staging().toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        clean();
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void clean() {
        clean(Instant.now().minus(MAX_AGE));
    }

    /** Removes leftovers last modified before {@code cutoff}. */
    void clean(Instant cutoff) {
        try {
            for (Path entry : list(staging)) removeIfOlder(entry, cutoff);
            for (Path problem : list(root)) {
                if (!Files.isDirectory(problem) || problem.equals(staging)) continue;
                for (Path entry : list(problem)) {
                    if (entry.getFileName().toString().startsWith(".tmp-")) removeIfOlder(entry, cutoff);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not clean up test-data leftovers: {}", e.getMessage());
        }
    }

    private static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.toList();
        }
    }

    private static void removeIfOlder(Path entry, Instant cutoff) throws IOException {
        if (Files.getLastModifiedTime(entry).toInstant().isBefore(cutoff)) {
            log.info("Removing test-data leftover {}", entry);
            FileSystemTestDataStore.deleteRecursively(entry);
        }
    }
}
