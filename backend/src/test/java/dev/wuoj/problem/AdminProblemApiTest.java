package dev.wuoj.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class AdminProblemApiTest extends IntegrationTest {

    @Autowired
    JsonMapper json;

    private ApiSession admin;

    @BeforeEach
    void accounts() throws Exception {
        fixtures.user("admin", UserRole.ADMIN);
        fixtures.user("alice");
        admin = signedIn("admin");
    }

    // --- Request helpers -----------------------------------------------------------------------------------------

    private static Map<String, Object> problem(String displayId, String slug, String title) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("displayId", displayId);
        content.put("slug", slug);
        content.put("title", title);
        content.put("difficulty", "EASY");
        content.put("timeLimitMs", 1000);
        content.put("memoryLimitMb", 256);
        content.put("statement", "Print $a + b$.\n");
        content.put("visible", true);
        content.put("samples", List.of(Map.of("input", "1 2\n", "output", "3\n"),
                Map.of("input", "5 5\n", "output", "10\n")));
        return content;
    }

    private static Map<String, Object> keep(int n) {
        return Map.of("keep", n);
    }

    private static Map<String, Object> upload(int i) {
        return Map.of("upload", i);
    }

    private static Map<String, Object> test(Map<String, Object> input, Map<String, Object> output) {
        return Map.of("input", input, "output", output);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, MediaType.APPLICATION_OCTET_STREAM_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartHttpServletRequestBuilder create(Map<String, Object> content, List<Map<String, Object>> tests,
            MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/problems");
        request.param("problem", json.writeValueAsString(content));
        request.param("manifest", json.writeValueAsString(Map.of("tests", tests)));
        for (MockMultipartFile file : files) request.file(file);
        return request;
    }

    /** {@code n} tests where test {@code i} is "i i" → "2i". */
    private long createProblem(String slug, int n) throws Exception {
        List<Map<String, Object>> tests = new ArrayList<>();
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            tests.add(test(upload(files.size()), upload(files.size() + 1)));
            files.add(file(i + ".in", i + " " + i));
            files.add(file(i + ".out", Integer.toString(2 * i)));
        }
        String body = admin.perform(create(problem("D" + slug, slug, "Title " + slug), tests,
                        files.toArray(MockMultipartFile[]::new)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private MockMultipartHttpServletRequestBuilder saveTests(long id, String baseVersion,
            List<Map<String, Object>> tests, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/problems/" + id + "/tests");
        request.param("manifest", json.writeValueAsString(Map.of("baseVersion", baseVersion, "tests", tests)));
        for (MockMultipartFile file : files) request.file(file);
        return request;
    }

    private MockHttpServletRequestBuilder update(long id, int revision, Map<String, Object> content) {
        return put("/api/admin/problems/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("revision", revision, "content", content)));
    }

    private String version(long id) throws Exception {
        return JsonPath.read(admin.perform(get("/api/admin/problems/" + id)).andReturn().getResponse()
                .getContentAsString(), "$.testdataVersion");
    }

    private long count(String sql) {
        return fixtures.jdbc().sql(sql).query(Long.class).single();
    }

    // --- List ----------------------------------------------------------------------------------------------------

    @Test
    void listIncludesHiddenProblems() throws Exception {
        fixtures.problem("1001", "visible", "Visible", dev.wuoj.common.domain.Difficulty.EASY, true);
        fixtures.problem("1002", "hidden", "Hidden", dev.wuoj.common.domain.Difficulty.HARD, false);

        admin.perform(get("/api/admin/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug").value(contains("visible", "hidden")))
                .andExpect(jsonPath("$.items[1].visible").value(false))
                .andExpect(jsonPath("$.items[1].testCount").value(5))
                .andExpect(jsonPath("$.items[1].updatedAt").isString())
                .andExpect(jsonPath("$.totalItems").value(2));
        admin.perform(get("/api/admin/problems").param("visible", "false"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("hidden")));
        admin.perform(get("/api/admin/problems").param("q", "VIS"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("visible")));
    }

    // --- Create --------------------------------------------------------------------------------------------------

    @Test
    void createWithTests() throws Exception {
        List<Map<String, Object>> tests = List.of(test(upload(0), upload(1)), test(upload(2), upload(3)),
                test(upload(4), upload(5)));
        admin.perform(create(problem("1001", "a-plus-b", "A + B"), tests, file("1.in", "1 1"), file("1.out", "2"),
                        file("2.in", "2 2"), file("2.out", "4"), file("3.in", "3 3"), file("3.out", "6")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/problems/")))
                .andExpect(jsonPath("$.content.visible").value(false))
                .andExpect(jsonPath("$.content.slug").value("a-plus-b"))
                .andExpect(jsonPath("$.content.samples.length()").value(2))
                .andExpect(jsonPath("$.testCount").value(3))
                .andExpect(jsonPath("$.revision").value(1));

        session().perform(get("/api/problems/a-plus-b")).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'PROBLEM_CREATED'")).isEqualTo(1);
    }

    @Test
    void createWithoutTestsCreatesNothing() throws Exception {
        admin.performInvalid(create(problem("1001", "a-plus-b", "A + B"), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("manifest"));
        assertThat(count("SELECT count(*) FROM problems")).isZero();
    }

    @Test
    void invalidFieldsAreAllReported() throws Exception {
        Map<String, Object> content = problem("1001", "a-plus-b", "");
        content.put("timeLimitMs", 50);
        admin.perform(create(content, List.of(test(upload(0), upload(1))), file("1.in", "1"), file("1.out", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("title", "timeLimitMs")));

        long id = createProblem("p", 1);
        admin.perform(update(id, 1, content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("title", "timeLimitMs")));
        admin.perform(get("/api/admin/problems/" + id)).andExpect(jsonPath("$.content.title").value("Title p"));
    }

    @Test
    void malformedJsonPartIsAFieldError() throws Exception {
        admin.performInvalid(multipart("/api/admin/problems").param("problem", "{").param("manifest", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("problem"));
    }

    @Test
    void displayIdAndSlugMustBeUnused() throws Exception {
        createProblem("taken", 1);
        long id = createProblem("other", 1);

        admin.perform(update(id, 1, problem("Dtaken", "taken", "T")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("displayId", "slug")))
                .andExpect(jsonPath("$.errors[0].message").value("is already used by problem 'taken'"));
    }

    // --- Read and update -----------------------------------------------------------------------------------------

    @Test
    void readHiddenProblem() throws Exception {
        long id = createProblem("p", 2);
        admin.perform(get("/api/admin/problems/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.visible").value(false))
                .andExpect(jsonPath("$.content.samples[0].input").value("1 2\n"))
                .andExpect(jsonPath("$.content.samples[1].output").value("10\n"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.testdataVersion").isString())
                .andExpect(jsonPath("$.testCount").value(2));
        admin.perform(get("/api/admin/problems/999")).andExpect(status().isNotFound());
    }

    @Test
    void updateChecksTheRevision() throws Exception {
        long id = createProblem("p", 1);
        Map<String, Object> first = problem("Dp", "p", "First admin's title");
        Map<String, Object> second = problem("Dp", "p", "Second admin's title");

        admin.perform(update(id, 1, first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.content.title").value("First admin's title"));
        admin.perform(update(id, 1, second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Someone else changed this problem")));
        admin.perform(get("/api/admin/problems/" + id))
                .andExpect(jsonPath("$.content.title").value("First admin's title"));
    }

    @Test
    void unchangedUpdateKeepsTheRevisionAndRecordsNothing() throws Exception {
        long id = createProblem("p", 1);
        Map<String, Object> current = problem("Dp", "p", "Title p");
        current.put("visible", false);
        long audits = count("SELECT count(*) FROM audit_log");

        admin.perform(update(id, 1, current)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(audits);
    }

    @Test
    void publishingAndRenamingTakeEffectImmediately() throws Exception {
        long id = createProblem("p", 1);
        admin.perform(update(id, 1, problem("Dp", "renamed", "Title p"))).andExpect(status().isOk());

        session().perform(get("/api/problems")).andExpect(jsonPath("$.items[*].slug").value(contains("renamed")));
        session().perform(get("/api/problems/renamed")).andExpect(status().isOk());
        session().perform(get("/api/problems/p")).andExpect(status().isNotFound());

        admin.perform(get("/api/admin/audit"))
                .andExpect(jsonPath("$.items[0].action").value("PROBLEM_UPDATED"))
                .andExpect(jsonPath("$.items[0].problem.slug").value("renamed"))
                .andExpect(jsonPath("$.items[0].target").doesNotExist());
    }

    // --- Tests ---------------------------------------------------------------------------------------------------

    @Test
    void listAndDownloadTests() throws Exception {
        long id = createProblem("p", 5);
        admin.perform(get("/api/admin/problems/" + id + "/tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version(id)))
                .andExpect(jsonPath("$.tests.length()").value(5))
                .andExpect(jsonPath("$.tests[2].number").value(3))
                .andExpect(jsonPath("$.tests[2].inputBytes").value(3))
                .andExpect(jsonPath("$.tests[2].outputBytes").value(1));

        admin.perform(get("/api/admin/problems/" + id + "/tests/3/output"))
                .andExpect(status().isOk())
                .andExpect(content().string("6"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"3.out\""));
        admin.perform(get("/api/admin/problems/" + id + "/tests/4/input").header("Range", "bytes=0-1"))
                .andExpect(status().isPartialContent())
                .andExpect(content().string("4 "))
                .andExpect(header().string("Content-Range", "bytes 0-1/3"));
        admin.perform(get("/api/admin/problems/" + id + "/tests/6/input")).andExpect(status().isNotFound());
    }

    @Test
    void replaceOneExpectedOutput() throws Exception {
        long id = createProblem("p", 10);
        String before = version(id);
        admin.perform(update(id, 1, problem("Dp", "p", "Title p"))).andExpect(status().isOk());
        signedIn("alice").perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + id + ",\"language\":\"CPP17\",\"sourceCode\":\"int main(){}\"}"))
                .andExpect(status().isCreated());

        List<Map<String, Object>> tests = new ArrayList<>();
        for (int n = 1; n <= 10; n++) tests.add(test(keep(n), n == 4 ? upload(0) : keep(n)));
        admin.perform(saveTests(id, before, tests, file("4.out", "9\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.testSet.tests.length()").value(10))
                .andExpect(jsonPath("$.testSet.tests[3].outputBytes").value(2));

        String after = version(id);
        assertThat(after).isNotEqualTo(before);
        admin.perform(get("/api/admin/problems/" + id + "/tests/4/output")).andExpect(content().string("9\n"));
        assertThat(fixtures.jdbc().sql("SELECT testdata_version FROM submissions").query(String.class).single())
                .as("queued before the save").isEqualTo(before);
        admin.perform(get("/api/admin/audit"))
                .andExpect(jsonPath("$.items[0].action").value("TESTS_UPDATED"))
                .andExpect(jsonPath("$.items[0].problem.slug").value("p"))
                .andExpect(jsonPath("$.items[0].fromTestCount").value(10))
                .andExpect(jsonPath("$.items[0].toTestCount").value(10));
    }

    @Test
    void deleteAndReorder() throws Exception {
        long id = createProblem("p", 3);
        admin.perform(saveTests(id, version(id), List.of(test(keep(3), keep(3)), test(keep(1), keep(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testSet.tests.length()").value(2));
        admin.perform(get("/api/admin/problems/" + id + "/tests/1/input")).andExpect(content().string("3 3"));
        admin.perform(get("/api/admin/problems/" + id + "/tests/2/input")).andExpect(content().string("1 1"));
    }

    @Test
    void unchangedTestsAreNotSaved() throws Exception {
        long id = createProblem("p", 2);
        String before = version(id);
        long audits = count("SELECT count(*) FROM audit_log");

        admin.perform(saveTests(id, before, List.of(test(keep(1), keep(1)), test(keep(2), keep(2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.testSet.version").value(before));
        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(audits);
    }

    @Test
    void staleBaseVersionIsAConflict() throws Exception {
        long id = createProblem("p", 2);
        String base = version(id);
        admin.perform(saveTests(id, base, List.of(test(keep(1), keep(1))))).andExpect(status().isOk());

        admin.perform(saveTests(id, base, List.of(test(keep(2), keep(2)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Someone else saved new tests")));
        admin.perform(get("/api/admin/problems/" + id + "/tests")).andExpect(jsonPath("$.tests.length()").value(1));

        admin.perform(saveTests(id, "unknown", List.of(test(keep(1), keep(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("baseVersion")));
    }

    @Test
    void oversizedTestFileIsRefused() throws Exception {
        long id = createProblem("p", 1);
        String before = version(id);
        MockMultipartFile huge = new MockMultipartFile("files", "1.in", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[70 * 1024 * 1024]);

        admin.perform(saveTests(id, before, List.of(test(upload(0), keep(1))), huge))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value(containsString("64 MB")));
        assertThat(version(id)).isEqualTo(before);
    }

    // --- Access --------------------------------------------------------------------------------------------------

    @Test
    void onlyAdminsMayManageProblems() throws Exception {
        long id = createProblem("p", 1);
        String base = version(id);
        ApiSession user = signedIn("alice");
        ApiSession visitor = session().start();
        List<Supplier<AbstractMockHttpServletRequestBuilder<?>>> requests = List.of(
                () -> get("/api/admin/problems"),
                () -> get("/api/admin/problems/" + id),
                () -> get("/api/admin/problems/" + id + "/tests"),
                () -> get("/api/admin/problems/" + id + "/tests/1/input"),
                () -> get("/api/admin/problems/" + id + "/tests/1/output"),
                () -> update(id, 1, problem("Dp", "p", "x")),
                () -> create(problem("1", "x", "x"), List.of(test(upload(0), upload(1))), file("a", "a"),
                        file("b", "b")),
                () -> saveTests(id, base, List.of(test(keep(1), keep(1)))));
        for (Supplier<AbstractMockHttpServletRequestBuilder<?>> request : requests) {
            String userBody = user.perform(request.get()).andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            assertThat(userBody).doesNotContain("1 1");
            visitor.perform(request.get()).andExpect(status().isUnauthorized());
        }
        assertThat(count("SELECT count(*) FROM problems")).isEqualTo(1);
        admin.perform(get("/api/admin/audit"))
                .andExpect(jsonPath("$.items[*].action").value(hasItem("PROBLEM_CREATED")));
    }
}
