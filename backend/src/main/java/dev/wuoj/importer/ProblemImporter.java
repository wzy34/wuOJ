package dev.wuoj.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.wuoj.problem.ProblemEditor;
import dev.wuoj.problem.ProblemRepository;
import org.springframework.stereotype.Service;

/**
 * Imports problem packages. A package is matched to an existing problem by slug and updated in place; tests that
 * changed become a new test-data version, while older submissions keep the version (and verdict) they were judged
 * with. A problem last edited in the web app is only overwritten when the import is forced.
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
    private final ProblemEditor editor;

    public ProblemImporter(ProblemRepository problems, ProblemEditor editor) {
        this.problems = problems;
        this.editor = editor;
    }

    public List<Report> importPath(Path path) throws IOException {
        return importPath(path, false);
    }

    /**
     * One package (a directory with {@code problem.yaml}), or every package directly inside a directory.
     *
     * @param force overwrite problems that were last edited in the web app
     */
    public List<Report> importPath(Path path, boolean force) throws IOException {
        if (Files.isRegularFile(path.resolve("problem.yaml")) || !Files.isDirectory(path)) {
            return List.of(importPackage(path, force));
        }
        List<Report> reports = new ArrayList<>();
        try (Stream<Path> entries = Files.list(path)) {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList()) {
                reports.add(importPackage(dir, force));
            }
        }
        return reports;
    }

    public Report importPackage(Path dir) {
        return importPackage(dir, false);
    }

    public Report importPackage(Path dir, boolean force) {
        String name = dir.getFileName().toString();
        if (!Files.isDirectory(dir)) return new Report(name, Outcome.REJECTED, List.of("not a directory"));

        ProblemPackageReader.Result result = reader.read(dir);
        List<String> errors = new ArrayList<>(result.errors());
        ProblemPackage pkg = result.problemPackage();
        if (pkg != null) {
            problems.ownerOfDisplayId(pkg.data().displayId())
                    .filter(owner -> !owner.slug().equals(pkg.data().slug()))
                    .ifPresent(owner -> errors.add("problem.yaml: displayId '" + pkg.data().displayId()
                            + "' is already used by problem '" + owner.slug() + "'"));
        }
        if (pkg == null || !errors.isEmpty()) return new Report(name, Outcome.REJECTED, List.copyOf(errors));

        return switch (editor.importPackage(pkg.data(), pkg.samples(), pkg.tests(), force)) {
            case CREATED -> new Report(name, Outcome.CREATED, List.of());
            case UPDATED -> new Report(name, Outcome.UPDATED, List.of());
            case UNCHANGED -> new Report(name, Outcome.UNCHANGED, List.of());
            case EDITED_ON_WEB -> new Report(name, Outcome.REJECTED, List.of("problem '" + pkg.data().slug()
                    + "' was edited in the web app since it was last imported; import with --force to overwrite"
                    + " those edits"));
        };
    }
}
