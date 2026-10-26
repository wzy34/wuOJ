package dev.wuoj.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import dev.wuoj.common.domain.Difficulty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemPackageReaderTest {

    @TempDir
    Path temp;

    private final ProblemPackageReader reader = new ProblemPackageReader();

    @Test
    void validPackage() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("a-plus-b"), "1001", "a-plus-b", 10);
        var result = reader.read(pkg.dir());

        assertThat(result.errors()).isEmpty();
        var problem = result.problemPackage();
        assertThat(problem.data().displayId()).isEqualTo("1001");
        assertThat(problem.data().slug()).isEqualTo("a-plus-b");
        assertThat(problem.data().difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(problem.data().visible()).isTrue();
        assertThat(problem.data().statement()).isEqualTo("Print $a + b$.\n");
        assertThat(problem.samples()).hasSize(2).first().satisfies(s -> {
            assertThat(s.input()).isEqualTo("1 2\n");
            assertThat(s.output()).isEqualTo("3\n");
        });
        assertThat(problem.tests()).hasSize(10).extracting("number").startsWith(1, 2).endsWith(10);
    }

    @Test
    void visibleDefaultsToFalseAndSamplesAreOptional() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1)
                .file("problem.yaml", "displayId: 1\nslug: p\ntitle: P\ndifficulty: HARD\n"
                        + "timeLimitMs: 100\nmemoryLimitMb: 16\n")
                .delete("samples/1.in").delete("samples/1.out").delete("samples/2.in").delete("samples/2.out");
        var result = reader.read(pkg.dir());
        assertThat(result.errors()).isEmpty();
        assertThat(result.problemPackage().data().visible()).isFalse();
        assertThat(result.problemPackage().samples()).isEmpty();
    }

    @Test
    void missingExpectedOutputIsNamed() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 5).delete("tests/3.out");
        var result = reader.read(pkg.dir());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().startsWith("tests/3.out: missing");
    }

    @Test
    void everyProblemIsReportedTogether() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 5)
                .file("problem.yaml", "displayId: 1\nslug: p\ntitle: P\ndifficulty: INSANE\n"
                        + "timeLimitMs: 1000\nmemoryLimitMb: 256\n")
                .delete("tests/2.in").delete("tests/2.out");
        var result = reader.read(pkg.dir());
        assertThat(result.errors()).hasSize(2)
                .anySatisfy(e -> assertThat(e).contains("difficulty 'INSANE' is invalid"))
                .anySatisfy(e -> assertThat(e).startsWith("tests/2.in: missing (numbering"));
    }

    @Test
    void invalidSlug() throws IOException {
        for (String slug : new String[] {"A-Plus-B", "a--b", "-a", "a b"}) {
            PackageWriter pkg = PackageWriter.valid(temp.resolve("p" + slug.hashCode()), "1", "x", 1)
                    .file("problem.yaml", "displayId: 1\nslug: \"" + slug + "\"\ntitle: P\ndifficulty: EASY\n"
                            + "timeLimitMs: 1000\nmemoryLimitMb: 256\n");
            assertThat(reader.read(pkg.dir()).errors()).as(slug)
                    .anySatisfy(e -> assertThat(e).contains("slug '" + slug + "' is invalid"));
        }
    }

    @Test
    void limitsOutOfRange() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1)
                .file("problem.yaml", "displayId: 1\nslug: p\ntitle: P\ndifficulty: EASY\n"
                        + "timeLimitMs: 99\nmemoryLimitMb: 2048\n");
        assertThat(reader.read(pkg.dir()).errors())
                .anySatisfy(e -> assertThat(e).contains("timeLimitMs must be between 100 and 10000"))
                .anySatisfy(e -> assertThat(e).contains("memoryLimitMb must be between 16 and 1024"));
    }

    @Test
    void missingFieldsAndFiles() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1)
                .file("problem.yaml", "slug: p\n")
                .delete("statement.md");
        assertThat(reader.read(pkg.dir()).errors())
                .contains("problem.yaml: displayId is required", "problem.yaml: title is required",
                        "problem.yaml: difficulty is required", "problem.yaml: timeLimitMs is required",
                        "problem.yaml: memoryLimitMb is required", "statement.md: missing");
    }

    @Test
    void strayFilesAndMissingTests() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1)
                .file("tests/readme.txt", "hi")
                .file("samples/01.in", "x");
        assertThat(reader.read(pkg.dir()).errors())
                .anySatisfy(e -> assertThat(e).startsWith("tests/readme.txt: not a test file"))
                .anySatisfy(e -> assertThat(e).startsWith("samples/01.in: not a test file"));

        PackageWriter none = PackageWriter.valid(temp.resolve("q"), "1", "q", 0);
        assertThat(reader.read(none.dir()).errors()).anySatisfy(e -> assertThat(e).startsWith("tests/"));
    }

    @Test
    void oversizedTestFile() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1);
        try (RandomAccessFile file = new RandomAccessFile(pkg.dir().resolve("tests/1.in").toFile(), "rw")) {
            file.setLength(ProblemPackageReader.MAX_TEST_FILE_BYTES + 1); // sparse, so this is cheap
        }
        assertThat(reader.read(pkg.dir()).errors()).containsExactly("tests/1.in: larger than 64 MB");
    }

    @Test
    void invalidYaml() throws IOException {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1", "p", 1).file("problem.yaml", "a: [unclosed");
        assertThat(reader.read(pkg.dir()).errors())
                .anySatisfy(e -> assertThat(e).startsWith("problem.yaml: not valid YAML"));
    }
}
