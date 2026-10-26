package dev.wuoj.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.testdata.TestDataRepository;
import dev.wuoj.testdata.TestDataStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports problem packages. A package is matched to an existing problem by slug and updated in place; tests that
 * changed become a new test-data version, while older submissions keep the version (and verdict) they were judged
 * with.
 */
@Service
public class ProblemImporter {

    public enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED,
        REJECTED
    }

    public record Report(String name, Outcome outcome, List<String> errors) {}

    private final ProblemPackageReader reader = new ProblemPackageReader();
    private final ProblemRepository problems;
    private final TestDataRepository testdata;
    private final TestDataStore store;
    private final TransactionTemplate transactions;

    public ProblemImporter(ProblemRepository problems, TestDataRepository testdata, TestDataStore store,
            TransactionTemplate transactions) {
        this.problems = problems;
        this.testdata = testdata;
        this.store = store;
        this.transactions = transactions;
    }

    /** One package (a directory with {@code problem.yaml}), or every package directly inside a directory. */
    public List<Report> importPath(Path path) throws IOException {
        if (Files.isRegularFile(path.resolve("problem.yaml")) || !Files.isDirectory(path)) {
            return List.of(importPackage(path));
        }
        List<Report> reports = new ArrayList<>();
        try (Stream<Path> entries = Files.list(path)) {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList()) reports.add(importPackage(dir));
        }
        return reports;
    }

    public Report importPackage(Path dir) {
        String name = dir.getFileName().toString();
        if (!Files.isDirectory(dir)) return new Report(name, Outcome.REJECTED, List.of("not a directory"));

        ProblemPackageReader.Result result = reader.read(dir);
        List<String> errors = new ArrayList<>(result.errors());
        ProblemPackage pkg = result.problemPackage();
        if (pkg != null) {
            problems.slugOfDisplayId(pkg.data().displayId())
                    .filter(slug -> !slug.equals(pkg.data().slug()))
                    .ifPresent(slug -> errors.add("problem.yaml: displayId '" + pkg.data().displayId()
                            + "' is already used by problem '" + slug + "'"));
        }
        if (pkg == null || !errors.isEmpty()) return new Report(name, Outcome.REJECTED, List.copyOf(errors));

        String version = TestDataStore.version(pkg.tests());
        Outcome outcome = transactions.execute(status -> save(pkg, version));
        return new Report(name, outcome, List.of());
    }

    private Outcome save(ProblemPackage pkg, String version) {
        Optional<ProblemRepository.Stored> existing = problems.findStoredBySlug(pkg.data().slug());
        long id;
        Outcome outcome;
        if (existing.isEmpty()) {
            id = problems.insert(pkg.data(), version);
            outcome = Outcome.CREATED;
        } else {
            id = existing.get().id();
            boolean unchanged = existing.get().data().equals(pkg.data())
                    && existing.get().testdataVersion().equals(version)
                    && problems.samples(id).equals(pkg.samples());
            if (unchanged) return Outcome.UNCHANGED;
            problems.update(id, pkg.data(), version);
            outcome = Outcome.UPDATED;
        }
        try {
            TestDataStore.Stored stored = store.store(id, version, pkg.tests());
            testdata.saveVersion(id, version, stored.testCount(), stored.totalBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        problems.replaceSamples(id, pkg.samples());
        return outcome;
    }
}
