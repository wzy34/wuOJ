package dev.wuoj.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.web.FieldErrorDto;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.PayloadTooLargeException;
import dev.wuoj.testdata.TestCaseFiles;
import dev.wuoj.testdata.TestDataStore;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link TestManifest} and the files uploaded with it into the tests to store, numbered 1..n in manifest
 * order, after checking everything: references, sizes and counts. Nothing is written; kept files are read from the
 * base version's directory and uploads from where they were staged.
 */
@Component
public class TestSetAssembler {

    /** The version kept files come from, with its number of tests. */
    public record Base(long problemId, String version, int testCount) {}

    private final TestDataStore store;
    private final long maxVersionBytes;

    public TestSetAssembler(TestDataStore store, OjProperties properties) {
        this.store = store;
        this.maxVersionBytes = properties.testdata().maxVersionBytes().toBytes();
    }

    /** @param base null when creating a problem, which allows uploads only */
    public List<TestCaseFiles> assemble(TestManifest manifest, @Nullable Base base, List<Path> uploads) {
        List<TestManifest.@Nullable Entry> entries = manifest.tests();
        if (entries == null || entries.isEmpty()) {
            throw new InvalidFieldsException("manifest", "must contain at least one test");
        }
        if (entries.size() > ProblemRules.MAX_TESTS) {
            throw new PayloadTooLargeException("A problem can have at most " + ProblemRules.MAX_TESTS + " tests.");
        }

        List<String> errors = new ArrayList<>();
        int[] uses = new int[uploads.size()];
        List<TestCaseFiles> tests = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TestManifest.Entry entry = entries.get(i);
            String label = "tests[" + i + "]";
            if (entry == null) {
                errors.add(label + " is missing");
                continue;
            }
            Path input = resolve(entry.input(), label + ".input", "in", base, uploads, uses, errors);
            Path output = resolve(entry.output(), label + ".output", "out", base, uploads, uses, errors);
            if (input != null && output != null) tests.add(new TestCaseFiles(i + 1, input, output));
        }
        for (int i = 0; i < uses.length; i++) {
            if (uses[i] != 1) errors.add("files[" + i + "] must be used exactly once, but is used " + uses[i] + " times");
        }
        if (!errors.isEmpty()) throw new InvalidFieldsException(errors.stream()
                .map(message -> new FieldErrorDto("manifest", message))
                .toList());

        checkSizes(tests, uploads);
        return tests;
    }

    private @Nullable Path resolve(TestManifest.@Nullable FileRef ref, String label, String extension,
            @Nullable Base base, List<Path> uploads, int[] uses, List<String> errors) {
        if (ref == null || (ref.keep() == null) == (ref.upload() == null)) {
            errors.add(label + " must give exactly one of keep and upload");
            return null;
        }
        Integer keep = ref.keep();
        if (keep != null) {
            if (base == null) {
                errors.add(label + ": keep is only allowed when saving the tests of an existing problem");
                return null;
            }
            if (keep < 1 || keep > base.testCount()) {
                errors.add(label + ": keep " + keep + " is not a test of the base version, which has "
                        + base.testCount() + " tests");
                return null;
            }
            Path dir = store.materialize(base.problemId(), base.version());
            return dir.resolve(extension.equals("in") ? "input" : "output").resolve(keep + "." + extension);
        }
        int upload = ref.upload();
        if (upload < 0 || upload >= uploads.size()) {
            errors.add(label + ": upload " + upload + " is not one of the " + uploads.size() + " uploaded files");
            return null;
        }
        uses[upload]++;
        return uploads.get(upload);
    }

    private void checkSizes(List<TestCaseFiles> tests, List<Path> uploads) {
        try {
            for (Path upload : uploads) {
                if (Files.size(upload) > ProblemRules.MAX_TEST_FILE_BYTES) {
                    throw new PayloadTooLargeException("Each test file can be at most 64 MB.");
                }
            }
            long total = 0;
            for (TestCaseFiles test : tests) total += Files.size(test.input()) + Files.size(test.output());
            if (total > maxVersionBytes) {
                throw new PayloadTooLargeException("The tests of a problem can be at most "
                        + maxVersionBytes / (1024 * 1024) + " MB together.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
