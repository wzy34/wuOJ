package dev.wuoj;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.ProblemUserStatus;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.PageRequest;
import dev.wuoj.problem.ProblemData;
import dev.wuoj.problem.ProblemRepository;
import dev.wuoj.problem.ProblemRow;
import dev.wuoj.problem.UserProblemStatusRepository;
import dev.wuoj.submission.SubmissionRepository;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.testdata.TestDataRepository;
import dev.wuoj.user.AuditAction;
import dev.wuoj.user.AuditRepository;
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

    @Autowired
    TestDataRepository testdata;

    @Autowired
    AuditRepository audit;

    @Test
    void userRoundTripAndCaseInsensitiveLookup() {
        long id = users.insert("Alice", "{noop}pw", UserRole.ADMIN, false);
        var found = users.findByUsername("aLiCe").orElseThrow();
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.username()).isEqualTo("Alice");
        assertThat(found.role()).isEqualTo(UserRole.ADMIN);
        assertThat(found.enabled()).isFalse();

        assertThat(found.sessionEpoch()).isZero();
        assertThat(found.mustChangePassword()).isFalse();
        assertThat(found.createdAt()).isNotNull();
        assertThat(found.lastLoginAt()).isNull();

        users.update(id, "{noop}new", UserRole.USER, true);
        assertThat(users.findById(id).orElseThrow())
                .extracting("passwordHash", "role", "enabled", "sessionEpoch")
                .containsExactly("{noop}new", UserRole.USER, true, 1);
    }

    @Test
    void numericDisplayIdsSortNumerically() {
        long user = fixtures.user("student");
        fixtures.problem("1001", "c", "C", Difficulty.HARD, true);
        fixtures.problem("999", "a", "A", Difficulty.EASY, true);
        fixtures.problem("1000", "b", "B", Difficulty.MEDIUM, true);

        var page = problems.list(new Viewer(user, UserRole.USER), null, null, new PageRequest(1, 20));
        assertThat(page.items()).extracting(ProblemRow::displayId).containsExactly("999", "1000", "1001");
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void visitorsSeeVisibleProblemsWithoutStatus() {
        long user = fixtures.user("student");
        long solved = fixtures.problem("1001", "a", "A", Difficulty.EASY, true);
        fixtures.problem("1002", "hidden", "Hidden", Difficulty.EASY, false);
        statuses.markAttempted(user, solved);

        var page = problems.list(Viewer.visitor(), null, null, new PageRequest(1, 20));
        assertThat(page.items()).extracting(ProblemRow::slug).containsExactly("a");
        assertThat(page.items()).extracting(ProblemRow::status).containsExactly(ProblemUserStatus.NONE);
        assertThat(problems.findBySlug(Viewer.visitor(), "hidden")).isEmpty();
        assertThat(problems.findById(Viewer.visitor(), solved).orElseThrow().status())
                .isEqualTo(ProblemUserStatus.NONE);
    }

    @Test
    void adminListIncludesHiddenProblemsInPublicOrder() {
        fixtures.problem("1001", "c", "Gamma", Difficulty.HARD, false);
        fixtures.problem("999", "a", "Alpha", Difficulty.EASY, true);
        fixtures.problem(new ProblemData("1000", "b", "Beta 100%", Difficulty.MEDIUM, 1000, 256, "s", false), 7);

        var all = problems.adminList(null, null, new PageRequest(1, 20));
        assertThat(all.items()).extracting(ProblemRepository.AdminSummary::slug).containsExactly("a", "b", "c");
        assertThat(all.items().get(1)).extracting("visible", "testCount").containsExactly(false, 7);
        assertThat(all.items().get(1).updatedAt()).isNotNull();

        assertThat(problems.adminList(null, false, new PageRequest(1, 20)).items())
                .extracting(ProblemRepository.AdminSummary::slug).containsExactly("b", "c");
        assertThat(problems.adminList("100%", null, new PageRequest(1, 20)).items())
                .extracting(ProblemRepository.AdminSummary::slug).containsExactly("b");
        assertThat(problems.adminList("ALPHA", true, new PageRequest(1, 20)).totalItems()).isEqualTo(1);
    }

    @Test
    void contentUpdatesIncreaseTheRevisionAndTestUpdatesDoNot() {
        long id = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, false);
        var before = problems.findStored(id).orElseThrow();
        assertThat(before).extracting("revision", "lastEditSource", "testCount", "testdataVersion")
                .containsExactly(1, ProblemRepository.EditSource.IMPORT, 5, Fixtures.VERSION);

        ProblemData changed = new ProblemData("1001", "a-plus-b", "A plus B", Difficulty.EASY, 2000, 256, "s", true);
        problems.updateContent(id, changed, ProblemRepository.EditSource.WEB);
        var after = problems.findStored(id).orElseThrow();
        assertThat(after.data()).isEqualTo(changed);
        assertThat(after).extracting("revision", "lastEditSource")
                .containsExactly(2, ProblemRepository.EditSource.WEB);

        testdata.saveVersion(id, "v2", 3, 0);
        problems.updateTestdataVersion(id, "v2", ProblemRepository.EditSource.IMPORT);
        assertThat(problems.lock(id).orElseThrow())
                .extracting("revision", "lastEditSource", "testCount", "testdataVersion")
                .containsExactly(2, ProblemRepository.EditSource.IMPORT, 3, "v2");
        assertThat(problems.lockBySlug("a-plus-b")).isPresent();
        assertThat(problems.lock(999)).isEmpty();
    }

    @Test
    void auditRecordsAccountAndProblemTargets() {
        long admin = fixtures.user("admin", UserRole.ADMIN);
        long bob = fixtures.user("bob");
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        audit.recordRoleChange(admin, bob, UserRole.USER, UserRole.ADMIN);
        audit.recordProblemUpdated(admin, problem, List.of("title", "samples"));
        audit.recordTestsUpdated(admin, problem, "v1", 10, "v2", 11);

        var entries = audit.list(new PageRequest(1, 20)).items();
        assertThat(entries).extracting(AuditRepository.Entry::action).containsExactly(AuditAction.TESTS_UPDATED,
                AuditAction.PROBLEM_UPDATED, AuditAction.ROLE_CHANGED);
        assertThat(entries.getFirst()).satisfies(e -> {
            assertThat(e.actorUsername()).isEqualTo("admin");
            assertThat(e.target()).isNull();
            assertThat(e.problem()).isEqualTo(new AuditRepository.Problem(problem, "1001", "a-plus-b", "A + B"));
            assertThat(e.fromTestCount()).isEqualTo(10);
            assertThat(e.toTestCount()).isEqualTo(11);
            assertThat(e.fromRole()).isNull();
        });
        assertThat(entries.get(1).fromTestCount()).isNull();
        assertThat(entries.get(2)).satisfies(e -> {
            assertThat(e.target()).isEqualTo(new AuditRepository.Account(bob, "bob"));
            assertThat(e.problem()).isNull();
            assertThat(e.fromRole()).isEqualTo(UserRole.USER);
            assertThat(e.toRole()).isEqualTo(UserRole.ADMIN);
        });
        assertThat(fixtures.jdbc().sql("SELECT details::text FROM audit_log WHERE action = 'PROBLEM_UPDATED'")
                .query(String.class).single()).isEqualTo("{\"fields\": [\"title\", \"samples\"]}");
    }

    @Test
    void ownersOfDisplayIdsAndSlugs() {
        long id = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, false);
        assertThat(problems.ownerOfDisplayId("1001")).contains(new ProblemRepository.Owner(id, "a-plus-b"));
        assertThat(problems.ownerOfSlug("a-plus-b")).contains(new ProblemRepository.Owner(id, "a-plus-b"));
        assertThat(problems.ownerOfDisplayId("1002")).isEmpty();
        assertThat(problems.ownerOfSlug("other")).isEmpty();
    }

    @Test
    void problemRoundTripWithSamplesAndStatus() {
        long user = fixtures.user("student");
        long id = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        Viewer viewer = new Viewer(user, UserRole.USER);

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

        var row = submissions.find(new Viewer(user, UserRole.USER), id).orElseThrow();
        assertThat(row.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(row.sourceCode()).isEqualTo("int main(){}");
        assertThat(row.problemSlug()).isEqualTo("a-plus-b");
        assertThat(row.username()).isEqualTo("student");
        assertThat(submissions.testResults(id)).isEmpty();
        assertThat(submissions.countUnfinished(user)).isEqualTo(1);

        var listed = submissions.list(new Viewer(user, UserRole.USER), true, null, null, null,
                new PageRequest(1, 20));
        assertThat(listed.items()).singleElement().satisfies(s -> assertThat(s.sourceCode()).isNull());
    }
}
