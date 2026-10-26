package dev.wuoj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.support.IntegrationTest;
import dev.wuoj.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

class SchemaTest extends IntegrationTest {

    @Autowired
    UserRepository users;

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
        users.insert("alice", "{noop}x", UserRole.STUDENT, true);
        assertThatThrownBy(() -> users.insert("ALICE", "{noop}x", UserRole.STUDENT, true))
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
}
