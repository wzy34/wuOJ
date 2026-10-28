package dev.wuoj.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The repository's development data imports cleanly and gives a usable platform. */
class DevDataImportTest extends IntegrationTest {

    static final Path DEV_DATA = Path.of("../dev-data").toAbsolutePath().normalize();

    @Autowired
    UserImporter users;

    @Autowired
    ProblemImporter problems;

    @Test
    void freshDevelopmentSetup() throws Exception {
        assertThat(users.importCsv(DEV_DATA.resolve("users.csv"))).isEqualTo(new UserImporter.Summary(4, 0));
        assertThat(problems.importPath(DEV_DATA.resolve("problems")))
                .hasSize(6)
                .allSatisfy(r -> assertThat(r.outcome()).as(r.name() + " " + r.errors())
                        .isEqualTo(ProblemImporter.Outcome.CREATED));

        var student = signedIn("student");
        student.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.items[0].displayId").value("1001"))
                .andExpect(jsonPath("$.items[0].slug").value("a-plus-b"))
                .andExpect(jsonPath("$.items[*].difficulty", hasItem("EASY")))
                .andExpect(jsonPath("$.items[*].difficulty", hasItem("MEDIUM")))
                .andExpect(jsonPath("$.items[*].difficulty", hasItem("HARD")));
        student.perform(get("/api/problems/a-plus-b"))
                .andExpect(jsonPath("$.title").value("A + B"))
                .andExpect(jsonPath("$.samples.length()").value(2));
        student.perform(get("/api/auth/me")).andExpect(jsonPath("$.role").value("USER"));
        session().signIn("admin", "admin").perform(get("/api/auth/me")).andExpect(jsonPath("$.role").value("ADMIN"));
        session().signIn("alice", "alice");
        session().signIn("bob", "bob");
        assertThat(problems.importPath(DEV_DATA.resolve("problems")))
                .allSatisfy(r -> assertThat(r.outcome()).isEqualTo(ProblemImporter.Outcome.UNCHANGED));
    }
}
