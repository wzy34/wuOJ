package dev.wuoj.testdata;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import dev.wuoj.common.config.OjProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stores tests under {@code <root>/<problem id>/<version>/{input,output}}. A version is written to a temporary
 * directory and renamed into place in one step, so a crash never leaves a partial version behind.
 */
@Component
public class FileSystemTestDataStore implements TestDataStore {

    /** Readable by the unprivileged user inside judge containers, which the input directory is mounted into. */
    private static final Set<PosixFilePermission> DIR = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final Set<PosixFilePermission> FILE = PosixFilePermissions.fromString("rw-r--r--");

    private final Path root;

    @Autowired
    public FileSystemTestDataStore(OjProperties properties) {
        this(properties.testdata().root());
    }

    public FileSystemTestDataStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Stored store(long problemId, String version, List<TestCaseFiles> tests) throws IOException {
        Path target = materialize(problemId, version);
        if (Files.isDirectory(target)) return new Stored(version, tests.size(), sizeOf(target));

        Path problemDir = createDirectory(root.resolve(Long.toString(problemId)));
        Path temp = problemDir.resolve(".tmp-" + UUID.randomUUID());
        try {
            Path input = createDirectory(temp.resolve("input"));
            Path output = createDirectory(temp.resolve("output"));
            for (TestCaseFiles test : tests) {
                copy(test.input(), input.resolve(test.number() + ".in"));
                copy(test.output(), output.resolve(test.number() + ".out"));
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | AtomicMoveNotSupportedException e) {
                // Another import stored the same version first; it has identical content.
                if (!Files.isDirectory(target)) throw e;
            }
        } finally {
            deleteRecursively(temp);
        }
        return new Stored(version, tests.size(), sizeOf(target));
    }

    private static long sizeOf(Path version) throws IOException {
        try (Stream<Path> files = Files.walk(version)) {
            long total = 0;
            for (Path file : files.filter(Files::isRegularFile).toList()) total += Files.size(file);
            return total;
        }
    }

    @Override
    public Path materialize(long problemId, String version) {
        if (!version.matches("[0-9a-zA-Z]+")) throw new IllegalArgumentException("Invalid version " + version);
        return root.resolve(Long.toString(problemId)).resolve(version);
    }

    private static Path createDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        setPermissions(dir, DIR);
        return dir;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.copy(from, to);
        setPermissions(to, FILE);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
