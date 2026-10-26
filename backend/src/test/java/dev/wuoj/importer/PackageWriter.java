package dev.wuoj.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes problem packages for tests. */
final class PackageWriter {

    private final Path dir;

    private PackageWriter(Path dir) {
        this.dir = dir;
    }

    /** A valid package: 2 samples and the given number of tests (test n expects n + n). */
    static PackageWriter valid(Path dir, String displayId, String slug, int tests) throws IOException {
        PackageWriter writer = new PackageWriter(Files.createDirectories(dir));
        writer.file("problem.yaml", """
                displayId: "%s"
                slug: %s
                title: Title of %s
                difficulty: EASY
                timeLimitMs: 1000
                memoryLimitMb: 256
                visible: true
                """.formatted(displayId, slug, slug));
        writer.file("statement.md", "Print $a + b$.\n");
        writer.file("samples/1.in", "1 2\n").file("samples/1.out", "3\n");
        writer.file("samples/2.in", "-5 12\n").file("samples/2.out", "7\n");
        for (int n = 1; n <= tests; n++) {
            writer.file("tests/" + n + ".in", n + " " + n + "\n").file("tests/" + n + ".out", (2 * n) + "\n");
        }
        return writer;
    }

    PackageWriter file(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    PackageWriter delete(String path) throws IOException {
        Files.delete(dir.resolve(path));
        return this;
    }

    Path dir() {
        return dir;
    }
}
