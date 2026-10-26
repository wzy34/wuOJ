package dev.wuoj.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.importer.ProblemImporter;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class ProblemApiTest extends IntegrationTest {

    @Autowired
    UserProblemStatusRepository statuses;

    @Autowired
    ProblemRepository problems;

    @Autowired
    ProblemImporter importer;

    private long alice;
    private ApiSession student;

    @BeforeEach
    void setUp() throws Exception {
        alice = fixtures.user("alice");
        fixtures.user("bob");
        fixtures.user("admin", UserRole.ADMIN);
        student = signedIn("alice");
    }

    private void manyProblems(int count) {
        for (int i = 0; i < count; i++) {
            fixtures.problem(String.valueOf(1001 + i), "p-" + i, "Problem " + i, Difficulty.EASY, true);
        }
    }

    @Test
    void numericOrder() throws Exception {
        fixtures.problem("1001", "c", "C", Difficulty.EASY, true);
        fixtures.problem("999", "a", "A", Difficulty.EASY, true);
        fixtures.problem("1000", "b", "B", Difficulty.EASY, true);
        student.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].displayId").value(contains("999", "1000", "1001")));
    }

    @Test
    void secondPage() throws Exception {
        manyProblems(45);
        student.perform(get("/api/problems").param("page", "2").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].displayId").value("1021"))
                .andExpect(jsonPath("$.items[19].displayId").value("1040"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void pagePastTheEnd() throws Exception {
        manyProblems(45);
        student.perform(get("/api/problems").param("page", "9").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void invalidPaging() throws Exception {
        student.performInvalid(get("/api/problems").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("size")));
        student.performInvalid(get("/api/problems").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("page")));
    }

    @Test
    void caseInsensitiveSearch() throws Exception {
        fixtures.problem("1001", "maximum-subarray-sum", "Maximum Subarray Sum", Difficulty.MEDIUM, true);
        fixtures.problem("1002", "a-plus-b", "A + B", Difficulty.EASY, true);
        student.perform(get("/api/problems").param("q", "SUBARRAY"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("maximum-subarray-sum")));
        student.perform(get("/api/problems").param("q", "  subarray  "))
                .andExpect(jsonPath("$.totalItems").value(1));
        // The contract validator misreads a whitespace-only value as a missing parameter, so only the response
        // is checked here.
        student.performInvalid(get("/api/problems").param("q", "   "))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void wildcardsAreLiteral() throws Exception {
        fixtures.problem("1001", "percent", "100% Correct", Difficulty.EASY, true);
        fixtures.problem("1002", "plain", "Plain", Difficulty.EASY, true);
        fixtures.problem("1003", "under", "snake_case", Difficulty.EASY, true);
        student.perform(get("/api/problems").param("q", "%"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("percent")));
        student.perform(get("/api/problems").param("q", "_"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("under")));
    }

    @Test
    void difficultyFilter() throws Exception {
        fixtures.problem("1001", "e", "E", Difficulty.EASY, true);
        fixtures.problem("1002", "h", "H", Difficulty.HARD, true);
        student.perform(get("/api/problems").param("difficulty", "HARD"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("h")));
        student.performInvalid(get("/api/problems").param("difficulty", "INSANE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenProblemsAreOnlyListedForAdmins() throws Exception {
        fixtures.problem("1001", "public", "Public", Difficulty.EASY, true);
        fixtures.problem("1002", "draft", "Draft", Difficulty.EASY, false);
        student.perform(get("/api/problems")).andExpect(jsonPath("$.items[*].slug").value(contains("public")));
        signedIn("admin").perform(get("/api/problems"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("public", "draft")));
    }

    @Test
    void statusIsPerUser() throws Exception {
        long solved = fixtures.problem("1001", "solved", "Solved", Difficulty.EASY, true);
        long attempted = fixtures.problem("1002", "attempted", "Attempted", Difficulty.EASY, true);
        fixtures.problem("1003", "untouched", "Untouched", Difficulty.EASY, true);
        statuses.markSolved(alice, solved);
        statuses.markAttempted(alice, attempted);

        student.perform(get("/api/problems"))
                .andExpect(jsonPath("$.items[*].status").value(contains("SOLVED", "ATTEMPTED", "NONE")));
        signedIn("bob").perform(get("/api/problems"))
                .andExpect(jsonPath("$.items[*].status").value(contains("NONE", "NONE", "NONE")));
    }

    @Test
    void statementAndSamples() throws Exception {
        String statement = "Given $a$ and $b$, print $a + b$.\n\n## Input\n\nTwo integers.\n";
        long id = fixtures.problem(new ProblemData("1001", "a-plus-b", "A + B", Difficulty.EASY, 2000, 128,
                statement, true), 5);
        problems.replaceSamples(id, List.of(new Sample("1 2\n", "3\n"), new Sample("-5 12\r\n", "7\n")));

        student.perform(get("/api/problems/a-plus-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayId").value("1001"))
                .andExpect(jsonPath("$.title").value("A + B"))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.timeLimitMs").value(2000))
                .andExpect(jsonPath("$.memoryLimitMb").value(128))
                .andExpect(jsonPath("$.statement").value(statement))
                .andExpect(jsonPath("$.samples[0].input").value("1 2\n"))
                .andExpect(jsonPath("$.samples[1].input").value("-5 12\r\n"))
                .andExpect(jsonPath("$.samples[1].output").value("7\n"))
                .andExpect(jsonPath("$.languages").value(contains("CPP17")));
    }

    @Test
    void hiddenProblemIsNotFoundForStudentsAndPreviewableByAdmins() throws Exception {
        fixtures.problem("1002", "draft", "Draft", Difficulty.EASY, false);
        student.perform(get("/api/problems/draft")).andExpect(status().isNotFound());
        signedIn("admin").perform(get("/api/problems/draft")).andExpect(status().isOk());
    }

    @Test
    void hiddenTestsNeverAppearInResponses(@TempDir Path temp) throws Exception {
        Path pkg = Files.createDirectories(temp.resolve("secret"));
        Files.writeString(pkg.resolve("problem.yaml"), "displayId: 1001\nslug: secret\ntitle: Secret\n"
                + "difficulty: EASY\ntimeLimitMs: 1000\nmemoryLimitMb: 256\nvisible: true\n");
        Files.writeString(pkg.resolve("statement.md"), "Echo the input.\n");
        Files.createDirectories(pkg.resolve("tests"));
        for (int n = 1; n <= 10; n++) {
            Files.writeString(pkg.resolve("tests/" + n + ".in"), "HIDDEN-INPUT-" + n + "\n");
            Files.writeString(pkg.resolve("tests/" + n + ".out"), "HIDDEN-OUTPUT-" + n + "\n");
        }
        assertThat(importer.importPackage(pkg).outcome()).isEqualTo(ProblemImporter.Outcome.CREATED);

        for (String path : List.of("/api/problems", "/api/problems/secret")) {
            String body = signedIn("admin").perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("HIDDEN-").doesNotContain("testdata").doesNotContain("version");
        }
    }

    @Test
    void unknownSlug() throws Exception {
        student.perform(get("/api/problems/no-such-problem"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Problem not found."));
    }
}
