package dev.wuoj.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.wuoj.WuojApplication;
import dev.wuoj.support.RealServerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/** The importers as the command-line tool runs them: a separate process-like application run with an exit code. */
class ImportCommandTest extends RealServerTest {

    @TempDir
    Path temp;

    @Autowired
    Environment environment;

    private record Run(int exitCode, String output) {}

    private Run run(String... command) {
        List<String> args = new ArrayList<>(List.of(command));
        connectionProperties().forEach((key, value) -> args.add("--" + key + "=" + value));
        args.add("--oj.testdata.root=" + environment.getProperty("oj.testdata.root"));
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true));
        try {
            int exitCode = WuojApplication.runCommand(args.toArray(String[]::new));
            return new Run(exitCode, captured.toString());
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void importsUsersAndProblems() throws Exception {
        Path users = Files.writeString(temp.resolve("users.csv"), "username,password,role\nstudent,secret,STUDENT\n");
        Run userRun = run("import-users", users.toString());
        assertThat(userRun.exitCode()).isZero();
        assertThat(userRun.output()).contains("1 created, 0 updated").doesNotContain("secret");

        Path problems = Files.createDirectories(temp.resolve("problems"));
        PackageWriter.valid(problems.resolve("a-plus-b"), "1001", "a-plus-b", 3);
        Run problemRun = run("import-problems", problems.toString());
        assertThat(problemRun.exitCode()).isZero();
        assertThat(problemRun.output()).contains("CREATED    a-plus-b").contains("1 package(s): 0 rejected");

        assertThat(new Browser().signIn(port, "student").statusCode()).isEqualTo(401);
        Browser browser = new Browser();
        browser.get(port, "/api/auth/me");
        assertThat(browser.postJson(port, "/api/auth/login", "{\"username\":\"student\",\"password\":\"secret\"}")
                .statusCode()).isEqualTo(200);
        assertThat(browser.get(port, "/api/problems/a-plus-b").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectedPackageGivesNonZeroExit() throws Exception {
        Path problems = Files.createDirectories(temp.resolve("problems"));
        PackageWriter.valid(problems.resolve("good"), "1001", "good", 1);
        PackageWriter.valid(problems.resolve("bad"), "1002", "bad", 1).delete("tests/1.out");
        Run result = run("import-problems", problems.toString());
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("REJECTED   bad").contains("tests/1.out: missing")
                .contains("CREATED    good");
    }

    @Test
    void invalidCsvGivesNonZeroExit() throws Exception {
        Path users = Files.writeString(temp.resolve("users.csv"), "username,password,role\nx,y,TEACHER\n");
        Run result = run("import-users", users.toString());
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("No users were imported").contains("line 2: role 'TEACHER'");
    }
}
