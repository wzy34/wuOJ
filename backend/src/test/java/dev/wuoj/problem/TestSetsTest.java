package dev.wuoj.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ConflictException;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.PayloadTooLargeException;
import dev.wuoj.problem.TestManifest.Entry;
import dev.wuoj.problem.TestManifest.FileRef;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.testdata.TestCaseFiles;
import dev.wuoj.testdata.TestDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/** Saving test sets through the assembler and the editor, as the admin API does, without HTTP. */
class TestSetsTest extends IntegrationTest {

    @TempDir
    Path temp;

    @Autowired
    TestSetAssembler assembler;

    @Autowired
    ProblemEditor editor;

    @Autowired
    ProblemRepository problems;

    @Autowired
    TestDataStore store;

    private Viewer admin;
    private ProblemRepository.Stored problem;

    private Path file(String name, String content) throws IOException {
        return Files.writeString(temp.resolve(name), content);
    }

    private static FileRef keep(int n) {
        return new FileRef(n, null);
    }

    private static FileRef upload(int i) {
        return new FileRef(null, i);
    }

    private static TestManifest manifest(String base, Entry... entries) {
        return new TestManifest(base, List.of(entries));
    }

    private TestSetAssembler.Base base() {
        return new TestSetAssembler.Base(problem.id(), problem.testdataVersion(), problem.testCount());
    }

    private ProblemEditor.TestsResult save(TestManifest manifest, List<Path> uploads) {
        List<TestCaseFiles> tests = assembler.assemble(manifest, base(), uploads);
        return editor.replaceTests(admin, problem.id(), manifest.baseVersion(), tests);
    }

    private String content(ProblemRepository.Stored stored, String file) throws IOException {
        return Files.readString(store.materialize(stored.id(), stored.testdataVersion()).resolve(file));
    }

    private Set<String> storedVersions() throws IOException {
        try (Stream<Path> dirs = Files.list(store.materialize(problem.id(), problem.testdataVersion()).getParent())) {
            return dirs.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private long count(String sql) {
        return fixtures.jdbc().sql(sql).query(Long.class).single();
    }

    @BeforeEach
    void createProblem() throws IOException {
        admin = new Viewer(fixtures.user("admin", UserRole.ADMIN), UserRole.ADMIN);
        List<Path> files = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            files.add(file("c" + n + ".in", "in" + n));
            files.add(file("c" + n + ".out", "out" + n));
        }
        TestManifest create = new TestManifest(null, List.of(new Entry(upload(0), upload(1)),
                new Entry(upload(2), upload(3)), new Entry(upload(4), upload(5))));
        problem = editor.create(admin, new ProblemData("1001", "p", "P", Difficulty.EASY, 1000, 256, "s", true),
                List.of(), assembler.assemble(create, null, files));
    }

    @Test
    void createdProblemIsHiddenAndAudited() throws IOException {
        assertThat(problem.data().visible()).isFalse();
        assertThat(problem.testCount()).isEqualTo(3);
        assertThat(problem.lastEditSource()).isEqualTo(ProblemRepository.EditSource.WEB);
        assertThat(content(problem, "output/3.out")).isEqualTo("out3");
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'PROBLEM_CREATED'")).isEqualTo(1);
    }

    @Test
    void keepUploadReorderAndDelete() throws IOException {
        ProblemEditor.TestsResult result = save(manifest(problem.testdataVersion(),
                new Entry(keep(3), keep(3)), new Entry(keep(1), upload(0))), List.of(file("new.out", "fixed")));

        assertThat(result.changed()).isTrue();
        ProblemRepository.Stored after = result.problem();
        assertThat(after.testCount()).isEqualTo(2);
        assertThat(after.testdataVersion()).isNotEqualTo(problem.testdataVersion());
        assertThat(content(after, "input/1.in")).isEqualTo("in3");
        assertThat(content(after, "output/1.out")).isEqualTo("out3");
        assertThat(content(after, "input/2.in")).isEqualTo("in1");
        assertThat(content(after, "output/2.out")).isEqualTo("fixed");
        assertThat(content(problem, "output/1.out")).as("old version kept").isEqualTo("out1");
        assertThat(fixtures.jdbc().sql("SELECT details::text FROM audit_log WHERE action = 'TESTS_UPDATED'")
                .query(String.class).single()).contains("\"fromCount\": 3").contains("\"toCount\": 2");
    }

    @Test
    void unchangedTestsCreateNoVersion() throws IOException {
        ProblemEditor.TestsResult result = save(manifest(problem.testdataVersion(), new Entry(keep(1), keep(1)),
                new Entry(keep(2), keep(2)), new Entry(upload(0), upload(1))),
                List.of(file("same.in", "in3"), file("same.out", "out3")));

        assertThat(result.changed()).isFalse();
        assertThat(result.problem().testdataVersion()).isEqualTo(problem.testdataVersion());
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'TESTS_UPDATED'")).isZero();
    }

    @Test
    void invalidManifestsChangeNothing() throws IOException {
        Path a = file("a.in", "a");
        Path b = file("b.out", "b");
        Set<String> before = storedVersions();
        String base = problem.testdataVersion();

        assertThatThrownBy(() -> save(manifest(base, new Entry(keep(4), keep(1))), List.of()))
                .isInstanceOf(InvalidFieldsException.class)
                .satisfies(e -> assertThat(((InvalidFieldsException) e).errors()).singleElement()
                        .satisfies(err -> assertThat(err.message()).contains("keep 4 is not a test")));
        assertThatThrownBy(() -> save(manifest(base, new Entry(upload(0), keep(1))), List.of(a, b)))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("files[1] must be used exactly once");
        assertThatThrownBy(() -> save(manifest(base, new Entry(upload(0), upload(0))), List.of(a)))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("used 2 times");
        assertThatThrownBy(() -> save(manifest(base, new Entry(upload(3), keep(1))), List.of(a)))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("upload 3 is not one of the 1 uploaded files");
        assertThatThrownBy(() -> save(manifest(base, new Entry(new FileRef(1, 0), keep(1))), List.of(a)))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("exactly one of keep and upload");
        assertThatThrownBy(() -> save(new TestManifest(base, List.of()), List.of()))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("at least one test");

        assertThat(problems.findStored(problem.id()).orElseThrow().testdataVersion()).isEqualTo(base);
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(1);
        assertThat(storedVersions()).isEqualTo(before);
    }

    @Test
    void keepIsNotAllowedWhenCreating() {
        assertThatThrownBy(() -> assembler.assemble(manifest(null, new Entry(keep(1), keep(1))), null, List.of()))
                .isInstanceOf(InvalidFieldsException.class)
                .hasMessageContaining("keep is only allowed");
    }

    @Test
    void limits() throws IOException {
        List<Entry> many = new ArrayList<>();
        for (int i = 0; i < 1001; i++) many.add(new Entry(keep(1), keep(1)));
        assertThatThrownBy(() -> save(new TestManifest(problem.testdataVersion(), many), List.of()))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("A problem can have at most 1000 tests.");

        Path huge = sparse("huge.in", ProblemRules.MAX_TEST_FILE_BYTES + 1);
        assertThatThrownBy(() -> save(manifest(problem.testdataVersion(), new Entry(upload(0), keep(1))),
                List.of(huge))).isInstanceOf(PayloadTooLargeException.class).hasMessageContaining("64 MB");

        List<Path> large = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            large.add(sparse("large" + i + ".in", ProblemRules.MAX_TEST_FILE_BYTES));
            entries.add(new Entry(upload(i), keep(1)));
        }
        assertThatThrownBy(() -> save(new TestManifest(problem.testdataVersion(), entries), large))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("The tests of a problem can be at most 512 MB together.");
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(1);
    }

    @Test
    void staleBaseVersionIsAConflict() throws IOException {
        String base = problem.testdataVersion();
        save(manifest(base, new Entry(keep(1), keep(1))), List.of());
        String current = problems.findStored(problem.id()).orElseThrow().testdataVersion();

        assertThatThrownBy(() -> save(manifest(base, new Entry(keep(2), keep(2))), List.of()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Someone else saved new tests");
        assertThat(problems.findStored(problem.id()).orElseThrow().testdataVersion()).isEqualTo(current);
    }

    private Path sparse(String name, long size) throws IOException {
        Path path = temp.resolve(name);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(size);
        }
        return path;
    }
}
