package dev.wuoj.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class UserImporterTest extends IntegrationTest {

    @TempDir
    Path temp;

    @Autowired
    UserImporter importer;

    @Autowired
    UserRepository users;

    private Path csv(String content) throws Exception {
        return Files.writeString(temp.resolve("users.csv"), content);
    }

    private int signIn(String username, String password) throws Exception {
        return session().start().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void createAndUpdate() throws Exception {
        fixtures.user("bob");
        var summary = importer.importCsv(csv("username,password,role\ncarol,carol-pw,STUDENT\nbob,new-pw,ADMIN\n"));

        assertThat(summary).isEqualTo(new UserImporter.Summary(1, 1));
        assertThat(signIn("carol", "carol-pw")).isEqualTo(200);
        assertThat(signIn("bob", "bob")).isEqualTo(401);
        assertThat(signIn("bob", "new-pw")).isEqualTo(200);
        assertThat(users.findByUsername("bob").orElseThrow().role()).isEqualTo(UserRole.ADMIN);
        assertThat(users.findByUsername("carol").orElseThrow().passwordHash()).startsWith("{bcrypt}")
                .doesNotContain("carol-pw");
    }

    @Test
    void invalidRowAppliesNothing() throws Exception {
        Path file = csv("username,password,role\ncarol,pw,STUDENT\ndave,pw,TEACHER\n");
        assertThatThrownBy(() -> importer.importCsv(file))
                .isInstanceOfSatisfying(UserImporter.InvalidCsvException.class, e -> assertThat(e.errors())
                        .containsExactly("line 3: role 'TEACHER' is invalid (STUDENT or ADMIN)"));
        assertThat(users.findByUsername("carol")).isEmpty();
    }

    @Test
    void everyInvalidRowIsReported() throws Exception {
        Path file = csv("username,password,role,enabled\nbad name,pw,STUDENT,true\nok,,STUDENT,\nOK,pw,ADMIN,maybe\n");
        assertThatThrownBy(() -> importer.importCsv(file))
                .isInstanceOfSatisfying(UserImporter.InvalidCsvException.class, e -> assertThat(e.errors())
                        .anySatisfy(m -> assertThat(m).startsWith("line 2: username 'bad name' is invalid"))
                        .contains("line 3: password is required", "line 4: enabled must be true or false"));
    }

    @Test
    void duplicateUsernamesIgnoringCase() throws Exception {
        Path file = csv("username,password,role\nAnn,pw,STUDENT\nann,pw,STUDENT\n");
        assertThatThrownBy(() -> importer.importCsv(file))
                .isInstanceOfSatisfying(UserImporter.InvalidCsvException.class, e -> assertThat(e.errors())
                        .containsExactly("line 3: username 'ann' appears more than once"));
    }

    @Test
    void missingColumn() throws Exception {
        Path file = csv("username,role\nann,STUDENT\n");
        assertThatThrownBy(() -> importer.importCsv(file))
                .isInstanceOfSatisfying(UserImporter.InvalidCsvException.class, e -> assertThat(e.errors())
                        .containsExactly("line 1: missing column 'password'"));
    }

    @Test
    void disabledUserCannotSignIn() throws Exception {
        importer.importCsv(csv("username,password,role,enabled\nerin,pw,STUDENT,false\n"));
        assertThat(users.findByUsername("erin").orElseThrow().enabled()).isFalse();
        assertThat(signIn("erin", "pw")).isEqualTo(401);
    }

    @Test
    void reenablingRestoresAccess() throws Exception {
        importer.importCsv(csv("username,password,role,enabled\nerin,pw,STUDENT,false\n"));
        importer.importCsv(csv("username,password,role\nerin,pw,STUDENT\n"));
        assertThat(signIn("erin", "pw")).isEqualTo(200);
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }
}
