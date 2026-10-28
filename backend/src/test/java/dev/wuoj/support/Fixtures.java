package dev.wuoj.support;

import java.util.List;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.problem.ProblemData;
import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.problem.Sample;
import dev.wuoj.testdata.TestDataRepository;
import dev.wuoj.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates test data directly in the database and wipes it between tests. */
@TestComponent
public class Fixtures {

    /** Test-data version used for problems created by {@link #problem}; no files exist for it. */
    public static final String VERSION = "v1";

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final JdbcClient jdbc;
    private final UserRepository users;
    private final ProblemRepository problems;
    private final TestDataRepository testdata;

    public Fixtures(JdbcClient jdbc, UserRepository users, ProblemRepository problems, TestDataRepository testdata) {
        this.jdbc = jdbc;
        this.users = users;
        this.problems = problems;
        this.testdata = testdata;
    }

    public void clear() {
        jdbc.sql("TRUNCATE users, problems, problem_samples, problem_testdata, submissions, "
                + "submission_test_results, user_problem_status, audit_log RESTART IDENTITY CASCADE").update();
    }

    /** A user whose password equals the username. */
    public long user(String username) {
        return user(username, UserRole.USER);
    }

    public long user(String username, UserRole role) {
        return users.insert(username, ENCODER.encode(username), role, true);
    }

    public long problem(String displayId, String slug, String title, Difficulty difficulty, boolean visible) {
        return problem(new ProblemData(displayId, slug, title, difficulty, 1000, 256,
                "Statement of $" + title + "$", visible), 5);
    }

    public long problem(ProblemData data, int testCount) {
        // The version row references the problem, so the problem is created first with the version name.
        long id = problems.insert(data, VERSION);
        testdata.saveVersion(id, VERSION, testCount, 0);
        problems.replaceSamples(id, List.of(new Sample("1 2\n", "3\n"), new Sample("-5 12\n", "7\n")));
        return id;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }
}
