package dev.wuoj.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.importer.ProblemImporter.Outcome;
import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.testdata.TestDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class ProblemImporterTest extends IntegrationTest {

    @TempDir
    Path temp;

    @Autowired
    ProblemImporter importer;

    @Autowired
    ProblemRepository problems;

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    TestDataStore store;

    private long count(String sql) {
        return fixtures.jdbc().sql(sql).query(Long.class).single();
    }

    @Test
    void validPackageIsCreated() throws Exception {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("a-plus-b"), "1001", "a-plus-b", 10);
        var report = importer.importPackage(pkg.dir());

        assertThat(report.outcome()).isEqualTo(Outcome.CREATED);
        var stored = problems.findStoredBySlug("a-plus-b").orElseThrow();
        assertThat(stored.data().displayId()).isEqualTo("1001");
        assertThat(stored.data().title()).isEqualTo("Title of a-plus-b");
        assertThat(stored.data().difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(stored.data().statement()).isEqualTo("Print $a + b$.\n");
        assertThat(problems.samples(stored.id())).hasSize(2);
        assertThat(count("SELECT test_count FROM problem_testdata")).isEqualTo(10);

        Path versionDir = store.materialize(stored.id(), stored.testdataVersion());
        assertThat(versionDir.resolve("input/10.in")).hasContent("10 10");
        assertThat(versionDir.resolve("output/10.out")).hasContent("20");
    }

    @Test
    void fixingATestCreatesANewVersionAndKeepsOldVerdicts() throws Exception {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("a-plus-b"), "1001", "a-plus-b", 5);
        importer.importPackage(pkg.dir());
        var before = problems.findStoredBySlug("a-plus-b").orElseThrow();
        long user = fixtures.user("alice");
        long old = submissions.insert(user, before.id(), Language.CPP17, "int main(){}", before.testdataVersion());
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'WA' WHERE id = :id")
                .param("id", old).update();

        pkg.file("tests/4.out", "8 \n");
        var report = importer.importPackage(pkg.dir());

        assertThat(report.outcome()).isEqualTo(Outcome.UPDATED);
        var after = problems.findStoredBySlug("a-plus-b").orElseThrow();
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.testdataVersion()).isNotEqualTo(before.testdataVersion());
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(2);
        assertThat(store.materialize(after.id(), after.testdataVersion()).resolve("output/4.out")).hasContent("8 ");
        assertThat(store.materialize(before.id(), before.testdataVersion())).isDirectory();
        assertThat(fixtures.jdbc().sql("SELECT verdict || ' ' || testdata_version FROM submissions WHERE id = :id")
                .param("id", old).query(String.class).single())
                .isEqualTo("WA " + before.testdataVersion());

        long fresh = submissions.insert(user, after.id(), Language.CPP17, "x", after.testdataVersion());
        assertThat(fixtures.jdbc().sql("SELECT testdata_version FROM submissions WHERE id = :id")
                .param("id", fresh).query(String.class).single()).isEqualTo(after.testdataVersion());
    }

    @Test
    void metadataChangeKeepsTheTestVersion() throws Exception {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1001", "p", 3);
        importer.importPackage(pkg.dir());
        String version = problems.findStoredBySlug("p").orElseThrow().testdataVersion();

        pkg.file("statement.md", "New statement\n").file("samples/2.out", "8\n");
        assertThat(importer.importPackage(pkg.dir()).outcome()).isEqualTo(Outcome.UPDATED);
        var after = problems.findStoredBySlug("p").orElseThrow();
        assertThat(after.testdataVersion()).isEqualTo(version);
        assertThat(after.data().statement()).isEqualTo("New statement\n");
        assertThat(problems.samples(after.id()).get(1).output()).isEqualTo("8\n");
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(1);
    }

    @Test
    void importIsIdempotent() throws Exception {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1001", "p", 3);
        assertThat(importer.importPackage(pkg.dir()).outcome()).isEqualTo(Outcome.CREATED);
        assertThat(importer.importPackage(pkg.dir()).outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(count("SELECT count(*) FROM problem_testdata")).isEqualTo(1);
    }

    @Test
    void oneBadPackageAmongMany() throws Exception {
        Path all = Files.createDirectories(temp.resolve("problems"));
        for (int i = 1; i <= 5; i++) PackageWriter.valid(all.resolve("p" + i), "100" + i, "p" + i, 2);
        PackageWriter.valid(all.resolve("p9-broken"), "1009", "p9", 2).delete("tests/2.in");

        var reports = importer.importPath(all);
        assertThat(reports).hasSize(6);
        assertThat(reports).filteredOn(r -> r.outcome() == Outcome.CREATED).hasSize(5);
        assertThat(reports).filteredOn(r -> r.outcome() == Outcome.REJECTED).singleElement()
                .satisfies(r -> {
                    assertThat(r.name()).isEqualTo("p9-broken");
                    assertThat(r.errors()).anySatisfy(e -> assertThat(e).startsWith("tests/2.in: missing"));
                });
        assertThat(count("SELECT count(*) FROM problems")).isEqualTo(5);
    }

    @Test
    void displayIdOfAnotherProblemIsRejected() throws Exception {
        importer.importPackage(PackageWriter.valid(temp.resolve("a"), "1001", "a", 1).dir());
        var report = importer.importPackage(PackageWriter.valid(temp.resolve("b"), "1001", "b", 1).dir());

        assertThat(report.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(report.errors()).containsExactly("problem.yaml: displayId '1001' is already used by problem 'a'");
        assertThat(problems.findStoredBySlug("b")).isEmpty();
    }

    @Test
    void rejectedPackageChangesNothing() throws Exception {
        PackageWriter pkg = PackageWriter.valid(temp.resolve("p"), "1001", "p", 3).delete("tests/3.out");
        assertThat(importer.importPackage(pkg.dir()).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(count("SELECT count(*) FROM problems")).isZero();
        assertThat(count("SELECT count(*) FROM problem_testdata")).isZero();
    }
}
