package dev.wuoj.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ConflictException;
import dev.wuoj.common.web.FieldErrorDto;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.NotFoundException;
import dev.wuoj.problem.ProblemRepository.EditSource;
import dev.wuoj.problem.ProblemRepository.Stored;
import dev.wuoj.testdata.TestCaseFiles;
import dev.wuoj.testdata.TestDataRepository;
import dev.wuoj.testdata.TestDataStore;
import dev.wuoj.user.AuditRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write to a problem, from the importer and from the admin API. Each write locks the problem first, so writes
 * apply one at a time. Tests that change become a new test-data version; submissions keep the version they were
 * created with. Web writes are audited in the same transaction and mark the problem as edited in the web app, which
 * re-imports then refuse to overwrite unless forced.
 */
@Service
public class ProblemEditor {

    public enum ImportResult {
        CREATED,
        UPDATED,
        UNCHANGED,
        /** Nothing was changed: the problem was last edited in the web app and the import was not forced. */
        EDITED_ON_WEB
    }

    /** A problem's tests after a save; {@code changed} is false when they were identical to the current ones. */
    public record TestsResult(boolean changed, Stored problem) {}

    private final ProblemRepository problems;
    private final TestDataRepository testdata;
    private final TestDataStore store;
    private final AuditRepository audit;

    public ProblemEditor(ProblemRepository problems, TestDataRepository testdata, TestDataStore store,
            AuditRepository audit) {
        this.problems = problems;
        this.testdata = testdata;
        this.store = store;
        this.audit = audit;
    }

    // --- Admin API -------------------------------------------------------------------------------------------------

    /** Creates a hidden problem, whatever {@code data.visible()} says. */
    @Transactional
    public Stored create(Viewer admin, ProblemData data, List<Sample> samples, List<TestCaseFiles> tests) {
        ProblemData hidden = new ProblemData(data.displayId(), data.slug(), data.title(), data.difficulty(),
                data.timeLimitMs(), data.memoryLimitMb(), data.statement(), false);
        check(hidden, null);
        String version = TestDataStore.version(tests);
        long id = problems.insert(hidden, version, EditSource.WEB);
        saveTests(id, version, tests);
        problems.replaceSamples(id, samples);
        audit.recordProblemCreated(admin.userId(), id);
        return problems.findStored(id).orElseThrow();
    }

    /** Replaces the content; nothing changes when it equals the current content. */
    @Transactional
    public Stored updateContent(Viewer admin, long id, int expectedRevision, ProblemData data, List<Sample> samples) {
        List<FieldErrorDto> errors = ProblemRules.validate(data);
        if (!errors.isEmpty()) throw new InvalidFieldsException(errors);
        Stored current = lock(id);
        if (current.revision() != expectedRevision) {
            throw new ConflictException("Someone else changed this problem after you opened it. "
                    + "Reload it to see their changes.");
        }
        List<String> changed = changedFields(current.data(), data);
        if (!problems.samples(id).equals(samples)) changed.add("samples");
        if (changed.isEmpty()) return current;

        check(data, id);
        problems.updateContent(id, data, EditSource.WEB);
        problems.replaceSamples(id, samples);
        audit.recordProblemUpdated(admin.userId(), id, changed);
        return problems.findStored(id).orElseThrow();
    }

    /** Makes {@code tests} (numbered 1..n) the current tests, if they differ from those of {@code baseVersion}. */
    @Transactional
    public TestsResult replaceTests(Viewer admin, long id, String baseVersion, List<TestCaseFiles> tests) {
        String version = TestDataStore.version(tests);
        Stored current = lock(id);
        if (!current.testdataVersion().equals(baseVersion)) {
            throw new ConflictException("Someone else saved new tests for this problem after you opened it. "
                    + "Reload the tests to see their changes.");
        }
        if (version.equals(baseVersion)) return new TestsResult(false, current);

        saveTests(id, version, tests);
        problems.updateTestdataVersion(id, version, EditSource.WEB);
        audit.recordTestsUpdated(admin.userId(), id, baseVersion, current.testCount(), version, tests.size());
        return new TestsResult(true, problems.findStored(id).orElseThrow());
    }

    private Stored lock(long id) {
        return problems.lock(id).orElseThrow(() -> new NotFoundException("Problem not found."));
    }

    /** The content rules plus uniqueness; {@code self} is the problem being changed, if any. */
    private void check(ProblemData data, @Nullable Long self) {
        List<FieldErrorDto> errors = new ArrayList<>(ProblemRules.validate(data));
        if (errors.stream().noneMatch(e -> e.field().equals("displayId"))) {
            problems.ownerOfDisplayId(data.displayId())
                    .filter(owner -> self == null || owner.id() != self)
                    .ifPresent(owner -> errors.add(new FieldErrorDto("displayId",
                            "is already used by problem '" + owner.slug() + "'")));
        }
        if (errors.stream().noneMatch(e -> e.field().equals("slug"))) {
            problems.ownerOfSlug(data.slug())
                    .filter(owner -> self == null || owner.id() != self)
                    .ifPresent(owner -> errors.add(new FieldErrorDto("slug", "is already used by another problem")));
        }
        if (!errors.isEmpty()) throw new InvalidFieldsException(errors);
    }

    private static List<String> changedFields(ProblemData from, ProblemData to) {
        List<String> fields = new ArrayList<>();
        if (!from.displayId().equals(to.displayId())) fields.add("displayId");
        if (!from.slug().equals(to.slug())) fields.add("slug");
        if (!from.title().equals(to.title())) fields.add("title");
        if (from.difficulty() != to.difficulty()) fields.add("difficulty");
        if (from.timeLimitMs() != to.timeLimitMs()) fields.add("timeLimitMs");
        if (from.memoryLimitMb() != to.memoryLimitMb()) fields.add("memoryLimitMb");
        if (!from.statement().equals(to.statement())) fields.add("statement");
        if (from.visible() != to.visible()) fields.add("visible");
        return fields;
    }

    // --- Import ----------------------------------------------------------------------------------------------------

    /**
     * Creates or updates the problem with the package's slug. Display-id conflicts are checked by the importer, which
     * reports them with the package's other problems.
     */
    @Transactional
    public ImportResult importPackage(ProblemData data, List<Sample> samples, List<TestCaseFiles> tests,
            boolean force) {
        String version = TestDataStore.version(tests);
        Optional<Stored> existing = problems.lockBySlug(data.slug());
        if (existing.isEmpty()) {
            long id = problems.insert(data, version, EditSource.IMPORT);
            saveTests(id, version, tests);
            problems.replaceSamples(id, samples);
            return ImportResult.CREATED;
        }

        Stored current = existing.get();
        boolean contentChanged = !current.data().equals(data) || !problems.samples(current.id()).equals(samples);
        boolean testsChanged = !current.testdataVersion().equals(version);
        if (!contentChanged && !testsChanged) return ImportResult.UNCHANGED;
        if (current.lastEditSource() == EditSource.WEB && !force) return ImportResult.EDITED_ON_WEB;

        if (contentChanged) {
            problems.updateContent(current.id(), data, EditSource.IMPORT);
            problems.replaceSamples(current.id(), samples);
        }
        if (testsChanged) {
            saveTests(current.id(), version, tests);
            problems.updateTestdataVersion(current.id(), version, EditSource.IMPORT);
        }
        return ImportResult.UPDATED;
    }

    private void saveTests(long id, String version, List<TestCaseFiles> tests) {
        try {
            TestDataStore.Stored stored = store.store(id, version, tests);
            testdata.saveVersion(id, version, stored.testCount(), stored.totalBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
