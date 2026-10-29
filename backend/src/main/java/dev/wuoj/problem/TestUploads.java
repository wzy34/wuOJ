package dev.wuoj.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.web.PayloadTooLargeException;
import dev.wuoj.testdata.TestDataStagingCleaner;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Moves the files of one upload into a private staging directory on the test-data store's filesystem, where the
 * store copies them from. Closing the result deletes them; {@link TestDataStagingCleaner} removes any left over by a
 * crash.
 */
@Component
public class TestUploads {

    /** The staged files, in upload order. */
    public static final class Staged implements AutoCloseable {

        private final Path dir;
        private final List<Path> files;

        private Staged(Path dir, List<Path> files) {
            this.dir = dir;
            this.files = List.copyOf(files);
        }

        public List<Path> files() {
            return files;
        }

        @Override
        public void close() {
            delete(dir);
        }
    }

    private final Path staging;

    public TestUploads(OjProperties properties) {
        this.staging = properties.testdata().staging().toAbsolutePath().normalize();
    }

    public Staged stage(List<MultipartFile> uploads) {
        for (MultipartFile upload : uploads) {
            if (upload.getSize() > ProblemRules.MAX_TEST_FILE_BYTES) {
                throw new PayloadTooLargeException("Each test file can be at most 64 MB.");
            }
        }
        Path dir = staging.resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(dir);
            List<Path> files = new ArrayList<>();
            for (int i = 0; i < uploads.size(); i++) {
                Path file = dir.resolve(Integer.toString(i));
                uploads.get(i).transferTo(file);
                files.add(file);
            }
            return new Staged(dir, files);
        } catch (IOException | RuntimeException e) {
            delete(dir);
            if (e instanceof IOException io) throw new UncheckedIOException(io);
            throw (RuntimeException) e;
        }
    }

    private static void delete(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
