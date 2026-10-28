package dev.wuoj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

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
