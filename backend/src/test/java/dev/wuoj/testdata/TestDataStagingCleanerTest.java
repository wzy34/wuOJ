package dev.wuoj.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.wuoj.common.config.OjProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class TestDataStagingCleanerTest {

    @TempDir
    Path root;

    private Path old(Path path) throws Exception {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        return path;
    }

    @Test
    void removesOnlyOldLeftovers() throws Exception {
        Path staging = Files.createDirectories(root.resolve(".staging"));
        Path oldUpload = old(Files.createDirectories(staging.resolve("a")));
        Path freshUpload = Files.createDirectories(staging.resolve("b"));
        Path problem = Files.createDirectories(root.resolve("7"));
        Path oldTemp = Files.createDirectories(problem.resolve(".tmp-1"));
        Files.writeString(oldTemp.resolve("x"), "x");
        old(oldTemp);
        Path freshTemp = Files.createDirectories(problem.resolve(".tmp-2"));
        Path version = old(Files.createDirectories(problem.resolve("abc123")));

        OjProperties properties = new OjProperties(List.of(),
                new OjProperties.TestData(root, DataSize.ofMegabytes(512)), null, null, null, null);
        new TestDataStagingCleaner(properties).clean();

        assertThat(oldUpload).doesNotExist();
        assertThat(oldTemp).doesNotExist();
        assertThat(freshUpload).isDirectory();
        assertThat(freshTemp).isDirectory();
        assertThat(version).as("versions are never removed").isDirectory();
    }

    @Test
    void missingRootIsFine() {
        OjProperties properties = new OjProperties(List.of(),
                new OjProperties.TestData(root.resolve("absent"), DataSize.ofMegabytes(512)), null, null, null, null);
        new TestDataStagingCleaner(properties).clean();
        assertThat(root.resolve("absent")).doesNotExist();
    }
}
