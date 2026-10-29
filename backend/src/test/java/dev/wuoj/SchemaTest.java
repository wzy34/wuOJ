package dev.wuoj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class SchemaTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    DataSource dataSource;

    private List<String> indexDefinitions(String table) {
        return fixtures.jdbc()
                .sql("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = :table")
                .param("table", table)
                .query(String.class)
                .list();
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        assertThat(indexDefinitions("users")).anySatisfy(def -> assertThat(def)
                .contains("UNIQUE INDEX users_username_lower_key").contains("lower((username)::text)"));
        users.insert("alice", "{noop}x", UserRole.USER, true);
        assertThatThrownBy(() -> users.insert("ALICE", "{noop}x", UserRole.USER, true))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void unfinishedSubmissionIndexesArePartial() {
        assertThat(indexDefinitions("submissions"))
                .anySatisfy(def -> assertThat(def).contains("submissions_unfinished_user_idx")
                        .contains("WHERE ((status)::text <> 'FINISHED'::text)"))
                .anySatisfy(def -> assertThat(def).contains("submissions_unfinished_lease_idx")
                        .contains("(status, lease_until)").contains("WHERE"));
    }

    @Test
    void problemsHaveUniqueSlugAndDisplayId() {
        assertThat(indexDefinitions("problems"))
                .anySatisfy(def -> assertThat(def).contains("UNIQUE").contains("(slug)"))
                .anySatisfy(def -> assertThat(def).contains("UNIQUE").contains("(display_id)"));
    }

    @Test
    void rolesAreUserOrAdmin() {
        assertThat(fixtures.jdbc()
                .sql("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'users_role_check'")
                .query(String.class)
                .single()).contains("'USER'").contains("'ADMIN'").doesNotContain("STUDENT");
        assertThatThrownBy(() -> fixtures.jdbc()
                .sql("INSERT INTO users (username, password_hash, role) VALUES ('x', 'x', 'STUDENT')")
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newAccountColumnsHaveDefaults() {
        long id = users.insert("alice", "{noop}x", UserRole.USER, true);
        Map<String, Object> row = fixtures.jdbc()
                .sql("SELECT session_epoch, must_change_password, last_login_at FROM users WHERE id = :id")
                .param("id", id)
                .query()
                .singleRow();
        assertThat(row).containsEntry("session_epoch", 0).containsEntry("must_change_password", false);
        assertThat(row.get("last_login_at")).isNull();
    }

    @Test
    void auditActionsAreChecked() {
        long id = users.insert("alice", "{noop}x", UserRole.ADMIN, true);
        fixtures.jdbc().sql("INSERT INTO audit_log (actor_id, action, target_user_id) VALUES (:id, 'DISABLED', :id)")
                .param("id", id)
                .update();
        assertThatThrownBy(() -> fixtures.jdbc()
                .sql("INSERT INTO audit_log (actor_id, action, target_user_id) VALUES (:id, 'DELETED', :id)")
                .param("id", id)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> fixtures.jdbc()
                .sql("INSERT INTO audit_log (actor_id, action, target_user_id) VALUES (:id, 'ENABLED', 999)")
                .param("id", id)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditRecordsTargetExactlyOneAccountOrProblem() {
        long id = users.insert("alice", "{noop}x", UserRole.ADMIN, true);
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        var jdbc = fixtures.jdbc();
        jdbc.sql("""
                INSERT INTO audit_log (actor_id, action, target_problem_id)
                VALUES (:id, 'TESTS_UPDATED', :problem)""").param("id", id).param("problem", problem).update();
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO audit_log (actor_id, action) VALUES (:id, 'PROBLEM_UPDATED')")
                .param("id", id)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO audit_log (actor_id, action, target_user_id, target_problem_id)
                        VALUES (:id, 'PROBLEM_UPDATED', :id, :problem)""")
                .param("id", id)
                .param("problem", problem)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'audit_log_action_check'")
                .query(String.class)
                .single()).contains("'PROBLEM_CREATED'").contains("'PROBLEM_UPDATED'").contains("'TESTS_UPDATED'");
    }

    @Test
    void problemEditColumnsHaveDefaults() {
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        Map<String, Object> row = fixtures.jdbc()
                .sql("SELECT revision, last_edit_source FROM problems WHERE id = :id")
                .param("id", problem)
                .query()
                .singleRow();
        assertThat(row).containsEntry("revision", 1).containsEntry("last_edit_source", "IMPORT");
        assertThatThrownBy(() -> fixtures.jdbc().sql("UPDATE problems SET last_edit_source = 'CLI'").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void submissionsRecordTheirLimits() {
        long user = users.insert("alice", "{noop}x", UserRole.USER, true);
        long problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        var jdbc = fixtures.jdbc();
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO submissions (user_id, problem_id, language, source_code, testdata_version)
                        VALUES (:user, :problem, 'CPP17', 'x', 'v1')""")
                .param("user", user)
                .param("problem", problem)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** An existing database is migrated from V2: submissions take their problem's limits. */
    @Test
    void upgradeFromV2BackfillsSubmissionLimits() {
        var jdbc = fixtures.jdbc();
        jdbc.sql("DROP SCHEMA IF EXISTS upgrade_check CASCADE").update();
        try {
            Flyway.configure().dataSource(dataSource).schemas("upgrade_check").target("2").load().migrate();
            jdbc.sql("""
                    INSERT INTO upgrade_check.users (username, password_hash, role) VALUES ('old', 'x', 'USER');
                    INSERT INTO upgrade_check.problems (display_id, slug, title, difficulty, time_limit_ms,
                                                        memory_limit_mb, statement_md, testdata_version)
                    VALUES ('1', 'p', 'P', 'EASY', 1500, 128, 's', 'v1');
                    INSERT INTO upgrade_check.problem_testdata (problem_id, version, test_count, total_bytes)
                    VALUES (1, 'v1', 1, 0);
                    INSERT INTO upgrade_check.submissions (user_id, problem_id, language, source_code,
                                                           testdata_version)
                    VALUES (1, 1, 'CPP17', 'x', 'v1')""").update();

            Flyway.configure().dataSource(dataSource).schemas("upgrade_check").load().migrate();

            assertThat(jdbc.sql("SELECT time_limit_ms || ':' || memory_limit_mb FROM upgrade_check.submissions")
                    .query(String.class)
                    .single()).isEqualTo("1500:128");
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS upgrade_check CASCADE").update();
        }
    }

    /** An existing database is migrated from V1: students become users. */
    @Test
    void upgradeFromV1RenamesStudents() {
        var jdbc = fixtures.jdbc();
        jdbc.sql("DROP SCHEMA IF EXISTS upgrade_check CASCADE").update();
        try {
            Flyway.configure().dataSource(dataSource).schemas("upgrade_check").target("1").load().migrate();
            jdbc.sql("""
                    INSERT INTO upgrade_check.users (username, password_hash, role)
                    VALUES ('old', 'x', 'STUDENT'), ('boss', 'x', 'ADMIN')""").update();

            Flyway.configure().dataSource(dataSource).schemas("upgrade_check").load().migrate();

            assertThat(jdbc.sql("SELECT username || ':' || role || ':' || session_epoch FROM upgrade_check.users "
                    + "ORDER BY id").query(String.class).list()).containsExactly("old:USER:0", "boss:ADMIN:0");
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS upgrade_check CASCADE").update();
        }
    }
}
