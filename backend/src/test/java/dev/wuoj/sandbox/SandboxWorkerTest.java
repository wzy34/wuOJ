package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.importer.ProblemImporter;
import dev.wuoj.judge.JudgeRepository;
import dev.wuoj.problem.ProblemData;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.ApiSession;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** The whole application judging with Docker: health, a real submission, and the cleaner. */
@Tag("sandbox")
@TestPropertySource(properties = "oj.judge.simulated=false")
class SandboxWorkerTest extends IntegrationTest {

    @Autowired
    ProblemImporter importer;

    @Autowired
    DockerSandbox sandbox;

    @Autowired
    SandboxCleaner cleaner;

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    JudgeRepository judge;

    @Autowired
    OjProperties properties;

    @Test
    void healthIncludesDocker() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.docker.status").value("UP"));
    }

    @Test
    void realSubmissionIsJudged(@TempDir Path temp) throws Exception {
        Path pkg = Files.createDirectories(temp.resolve("a-plus-b"));
        Files.writeString(pkg.resolve("problem.yaml"), "displayId: 1001\nslug: a-plus-b\ntitle: A + B\n"
                + "difficulty: EASY\ntimeLimitMs: 1000\nmemoryLimitMb: 256\nvisible: true\n");
        Files.writeString(pkg.resolve("statement.md"), "Print $a + b$.\n");
        Files.createDirectories(pkg.resolve("tests"));
        for (int n = 1; n <= 3; n++) {
            Files.writeString(pkg.resolve("tests/" + n + ".in"), n + " " + (n * 10) + "\n");
            Files.writeString(pkg.resolve("tests/" + n + ".out"), (n * 11) + "\n");
        }
        assertThat(importer.importPackage(pkg).outcome()).isEqualTo(ProblemImporter.Outcome.CREATED);
        fixtures.user("alice");
        ApiSession alice = signedIn("alice");
        long problemId = fixtures.jdbc().sql("SELECT id FROM problems").query(Long.class).single();

        String source = SandboxHarness.program("ac.cpp");
        String body = alice.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problemId + ",\"language\":\"CPP17\",\"sourceCode\":"
                                + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(source)
                                + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> alice
                .perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.status").value("FINISHED")));
        alice.perform(get("/api/submissions/" + id))
                .andExpect(jsonPath("$.verdict").value("AC"))
                .andExpect(jsonPath("$.testResults.length()").value(3));
        alice.perform(get("/api/problems/a-plus-b")).andExpect(jsonPath("$.status").value("SOLVED"));
    }

    @Test
    void cleanerRemovesLeftoversButNotLiveAttempts() throws Exception {
        Path workRoot = properties.judge().workDir().toAbsolutePath().normalize();
        long user = fixtures.user("alice");
        long problem = fixtures.problem(new ProblemData("1001", "p", "P", Difficulty.EASY, 1000, 256, "x", true), 1);

        // Left behind by a worker that died: its submission was later finished by another attempt.
        long finished = submissions.insert(user, problem, Language.CPP17, "x", Fixtures.VERSION);
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'AC' WHERE id = :id")
                .param("id", finished).update();
        String leftover = startIdle(finished, 1);
        Path leftoverDir = Files.createDirectories(workRoot.resolve(finished + "-1/work"));

        // Being judged right now under a live lease.
        long live = submissions.insert(user, problem, Language.CPP17, "x", Fixtures.VERSION);
        int attempt = judge.claim(live, "other-worker", Duration.ofMinutes(5)).orElseThrow().attempt();
        String liveContainer = startIdle(live, attempt);
        Path liveDir = Files.createDirectories(workRoot.resolve(live + "-" + attempt + "/work"));

        try {
            cleaner.clean();
            List<String> remaining = sandbox.judgeContainers().stream().map(c -> c.getId()).toList();
            assertThat(remaining).contains(liveContainer).doesNotContain(leftover);
            assertThat(leftoverDir.getParent()).doesNotExist();
            assertThat(liveDir).isDirectory();
        } finally {
            sandbox.remove(leftover);
            sandbox.remove(liveContainer);
            DockerJudgeEngine.deleteRecursively(liveDir.getParent());
        }
    }

    private String startIdle(long submissionId, int attempt) {
        return sandbox.start(new ContainerSpec("wuoj-" + submissionId + "-" + attempt + "-test",
                ContainerSpec.labels(submissionId, attempt), List.of(), 64L << 20, 64, null, null));
    }
}
