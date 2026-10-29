package dev.wuoj.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.FieldErrorDto;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.NotFoundException;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import dev.wuoj.problem.ProblemRepository.Stored;
import dev.wuoj.testdata.TestCaseFiles;
import dev.wuoj.testdata.TestDataRepository;
import dev.wuoj.testdata.TestDataStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Problem administration; the security configuration admits admins only. The multipart operations carry their JSON as
 * text parts (as the generated web client sends them), which are parsed and checked here so that field errors use the
 * contract's field names.
 */
@RestController
@RequestMapping("/api/admin/problems")
@ConditionalOnRole(Role.API)
public class AdminProblemController {

    public record AdminProblemSummary(long id, String displayId, String slug, String title, Difficulty difficulty,
            boolean visible, int testCount, Instant updatedAt) {

        static AdminProblemSummary of(ProblemRepository.AdminSummary row) {
            return new AdminProblemSummary(row.id(), row.displayId(), row.slug(), row.title(), row.difficulty(),
                    row.visible(), row.testCount(), row.updatedAt());
        }
    }

    /** As received; {@link #check} reports what is missing or invalid. */
    public record ProblemContent(@Nullable String displayId, @Nullable String slug, @Nullable String title,
            @Nullable Difficulty difficulty, @Nullable Integer timeLimitMs, @Nullable Integer memoryLimitMb,
            @Nullable String statement, @Nullable Boolean visible, @Nullable List<@Nullable Sample> samples) {

        static ProblemContent of(ProblemData data, List<Sample> samples) {
            return new ProblemContent(data.displayId(), data.slug(), data.title(), data.difficulty(),
                    data.timeLimitMs(), data.memoryLimitMb(), data.statement(), data.visible(),
                    List.copyOf(samples));
        }

        ProblemData data() {
            return new ProblemData(displayId, slug, title, difficulty, timeLimitMs == null ? 0 : timeLimitMs,
                    memoryLimitMb == null ? 0 : memoryLimitMb, statement, Boolean.TRUE.equals(visible));
        }

        /** Every invalid field, or nothing; samples must each have an input and an output. */
        void check() {
            List<FieldErrorDto> errors = new ArrayList<>(ProblemRules.validate(data()));
            if (visible == null) errors.add(new FieldErrorDto("visible", "is required"));
            if (samples == null) {
                errors.add(new FieldErrorDto("samples", "is required"));
            } else {
                for (int i = 0; i < samples.size(); i++) {
                    Sample sample = samples.get(i);
                    if (sample == null || sample.input() == null || sample.output() == null) {
                        errors.add(new FieldErrorDto("samples", "sample " + (i + 1) + " needs an input and an output"));
                    }
                }
            }
            if (!errors.isEmpty()) throw new InvalidFieldsException(errors);
        }

        /** After {@link #check}. */
        List<Sample> sampleList() {
            return samples == null ? List.of() : List.copyOf(samples);
        }
    }

    public record AdminProblem(long id, int revision, String testdataVersion, int testCount, Instant updatedAt,
            ProblemContent content) {}

    public record UpdateProblemRequest(@Nullable Integer revision, @Nullable ProblemContent content) {}

    public record AdminTest(int number, long inputBytes, long outputBytes) {}

    public record AdminTestSet(String version, List<AdminTest> tests) {}

    public record AdminTestSaveResult(boolean changed, AdminTestSet testSet) {}

    private final ProblemRepository problems;
    private final ProblemEditor editor;
    private final TestSetAssembler assembler;
    private final TestUploads uploads;
    private final TestDataRepository testdata;
    private final TestDataStore store;
    private final JsonMapper json;

    public AdminProblemController(ProblemRepository problems, ProblemEditor editor, TestSetAssembler assembler,
            TestUploads uploads, TestDataRepository testdata, TestDataStore store, JsonMapper json) {
        this.problems = problems;
        this.editor = editor;
        this.assembler = assembler;
        this.uploads = uploads;
        this.testdata = testdata;
        this.store = store;
        this.json = json;
    }

    @GetMapping
    Page<AdminProblemSummary> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size,
            @RequestParam(required = false) @Size(max = 100) @Nullable String q,
            @RequestParam(required = false) @Nullable Boolean visible) {
        String query = q == null || q.isBlank() ? null : q.strip();
        return problems.adminList(query, visible, new PageRequest(page, size)).map(AdminProblemSummary::of);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AdminProblem> create(Viewer admin, @RequestParam("problem") String problemPart,
            @RequestParam("manifest") String manifestPart,
            @RequestParam(name = "files", required = false) @Nullable List<MultipartFile> files) {
        ProblemContent content = parse(problemPart, "problem", ProblemContent.class);
        TestManifest manifest = parse(manifestPart, "manifest", TestManifest.class);
        content.check();
        try (TestUploads.Staged staged = uploads.stage(files == null ? List.of() : files)) {
            List<TestCaseFiles> tests = assembler.assemble(manifest, null, staged.files());
            Stored created = editor.create(admin, content.data(), content.sampleList(), tests);
            return ResponseEntity.created(URI.create("/api/admin/problems/" + created.id())).body(view(created));
        }
    }

    @GetMapping("/{id}")
    AdminProblem get(@PathVariable long id) {
        return view(find(id));
    }

    @PutMapping("/{id}")
    AdminProblem update(Viewer admin, @PathVariable long id, @RequestBody UpdateProblemRequest body) {
        ProblemContent content = body.content();
        if (body.revision() == null || content == null) {
            throw new InvalidFieldsException(body.revision() == null ? "revision" : "content", "is required");
        }
        content.check();
        return view(editor.updateContent(admin, id, body.revision(), content.data(), content.sampleList()));
    }

    @GetMapping("/{id}/tests")
    AdminTestSet tests(@PathVariable long id) {
        return testSet(find(id));
    }

    @PostMapping(path = "/{id}/tests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AdminTestSaveResult saveTests(Viewer admin, @PathVariable long id, @RequestParam("manifest") String manifestPart,
            @RequestParam(name = "files", required = false) @Nullable List<MultipartFile> files) {
        TestManifest manifest = parse(manifestPart, "manifest", TestManifest.class);
        find(id);
        String baseVersion = manifest.baseVersion();
        if (baseVersion == null) throw new InvalidFieldsException("manifest", "baseVersion is required");
        int baseCount = testdata.testCount(id, baseVersion).orElseThrow(() ->
                new InvalidFieldsException("manifest", "baseVersion is not a test-data version of this problem"));
        try (TestUploads.Staged staged = uploads.stage(files == null ? List.of() : files)) {
            List<TestCaseFiles> tests = assembler.assemble(manifest,
                    new TestSetAssembler.Base(id, baseVersion, baseCount), staged.files());
            ProblemEditor.TestsResult result = editor.replaceTests(admin, id, baseVersion, tests);
            return new AdminTestSaveResult(result.changed(), testSet(result.problem()));
        }
    }

    @GetMapping("/{id}/tests/{number}/input")
    ResponseEntity<Resource> input(@PathVariable long id, @PathVariable int number) {
        return download(id, number, "input", "in");
    }

    @GetMapping("/{id}/tests/{number}/output")
    ResponseEntity<Resource> output(@PathVariable long id, @PathVariable int number) {
        return download(id, number, "output", "out");
    }

    /** The current version's file; a {@code Range} request gets 206 with that part, from Spring's resource support. */
    private ResponseEntity<Resource> download(long id, int number, String dir, String extension) {
        Stored problem = find(id);
        if (number < 1 || number > problem.testCount()) throw new NotFoundException("Test not found.");
        String name = number + "." + extension;
        Path file = store.materialize(id, problem.testdataVersion()).resolve(dir).resolve(name);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(new FileSystemResource(file));
    }

    private Stored find(long id) {
        return problems.findStored(id).orElseThrow(() -> new NotFoundException("Problem not found."));
    }

    private AdminProblem view(Stored problem) {
        return new AdminProblem(problem.id(), problem.revision(), problem.testdataVersion(), problem.testCount(),
                problem.updatedAt(), ProblemContent.of(problem.data(), problems.samples(problem.id())));
    }

    private AdminTestSet testSet(Stored problem) {
        Path dir = store.materialize(problem.id(), problem.testdataVersion());
        List<AdminTest> tests = new ArrayList<>();
        try {
            for (int n = 1; n <= problem.testCount(); n++) {
                tests.add(new AdminTest(n, Files.size(dir.resolve("input/" + n + ".in")),
                        Files.size(dir.resolve("output/" + n + ".out"))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new AdminTestSet(problem.testdataVersion(), tests);
    }

    private <T> T parse(String part, String name, Class<T> type) {
        try {
            T value = json.readValue(part, type);
            if (value == null) throw new InvalidFieldsException(name, "is required");
            return value;
        } catch (JacksonException e) {
            throw new InvalidFieldsException(name, "is not valid JSON for this request");
        }
    }
}
