package dev.wuoj;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.ProblemUserStatus;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.PageRequest;
import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.problem.ProblemRow;
import dev.wuoj.problem.UserProblemStatusRepository;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RepositoryTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    ProblemRepository problems;

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    UserProblemStatusRepository statuses;

    @Test
    void userRoundTripAndCaseInsensitiveLookup() {
        long id = users.insert("Alice", "{noop}pw", UserRole.ADMIN, false);
        var found = users.findByUsername("aLiCe").orElseThrow();
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.username()).isEqualTo("Alice");
        assertThat(found.role()).isEqualTo(UserRole.ADMIN);
        assertThat(found.enabled()).isFalse();

        users.update(id, "{noop}new", UserRole.STUDENT, true);
        assertThat(users.findById(id).orElseThrow())
                .extracting("passwordHash", "role", "enabled")
                .containsExactly("{noop}new", UserRole.STUDENT, true);
    }

    @Test
    void numericDisplayIdsSortNumerically() {
        long user = fixtures.user("student");
        fixtures.problem("1001", "c", "C", Difficulty.HARD, true);
        fixtures.problem("999", "a", "A", Difficulty.EASY, true);
        fixtures.problem("1000", "b", "B", Difficulty.MEDIUM, true);

        var page = problems.list(new Viewer(user, UserRole.STUDENT), null, null, new PageRequest(1, 20));
        assertThat(page.items()).extracting(ProblemRow::displayId).containsExactly("999", "1000", "1001");
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void problemRoundTripWithSamplesAndStatus() {
        long user = fixtures.user("student");
        long id = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        Viewer viewer = new Viewer(user, UserRole.STUDENT);

        ProblemRow row = problems.findBySlug(viewer, "a-plus-b").orElseThrow();
        assertThat(row.id()).isEqualTo(id);
        assertThat(row.status()).isEqualTo(ProblemUserStatus.NONE);
        assertThat(problems.samples(id)).hasSize(2).first().extracting("input").isEqualTo("1 2\n");

        statuses.markAttempted(user, id);
        assertThat(problems.findById(viewer, id).orElseThrow().status()).isEqualTo(ProblemUserStatus.ATTEMPTED);
        statuses.markSolved(user, id);
        statuses.markAttempted(user, id);
        assertThat(problems.findById(viewer, id).orElseThrow().status()).isEqualTo(ProblemUserStatus.SOLVED);
    }

    @Test
    void submissionRoundTrip() {
        long user = fixtures.user("student");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        long id = submissions.insert(user, problem, Language.CPP17, "int main(){}", Fixtures.VERSION);

        var row = submissions.find(new Viewer(user, UserRole.STUDENT), id).orElseThrow();
        assertThat(row.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(row.sourceCode()).isEqualTo("int main(){}");
        assertThat(row.problemSlug()).isEqualTo("a-plus-b");
        assertThat(row.username()).isEqualTo("student");
        assertThat(submissions.testResults(id)).isEmpty();
        assertThat(submissions.countUnfinished(user)).isEqualTo(1);

        var listed = submissions.list(new Viewer(user, UserRole.STUDENT), true, null, null, new PageRequest(1, 20));
        assertThat(listed.items()).singleElement().satisfies(s -> assertThat(s.sourceCode()).isNull());
    }
}
