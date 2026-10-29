package dev.wuoj.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The record of admin actions on accounts and problems. Temporary passwords are never written here. */
@Repository
public class AuditRepository {

    /**
     * Exactly one of {@code target} (an account) and {@code problem} is set. {@code fromRole} and {@code toRole} are
     * set for {@link AuditAction#ROLE_CHANGED} only, the test counts for {@link AuditAction#TESTS_UPDATED} only.
     */
    public record Entry(long id, Instant createdAt, long actorId, String actorUsername, @Nullable Account target,
            @Nullable Problem problem, AuditAction action, @Nullable UserRole fromRole, @Nullable UserRole toRole,
            @Nullable Integer fromTestCount, @Nullable Integer toTestCount) {}

    public record Account(long id, String username) {}

    public record Problem(long id, String displayId, String slug, String title) {}

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long actorId, long targetId, AuditAction action) {
        insert(actorId, targetId, null, action, null);
    }

    public void recordRoleChange(long actorId, long targetId, UserRole from, UserRole to) {
        insert(actorId, targetId, null, AuditAction.ROLE_CHANGED,
                "{\"from\":\"" + from.name() + "\",\"to\":\"" + to.name() + "\"}");
    }

    public void recordProblemCreated(long actorId, long problemId) {
        insert(actorId, null, problemId, AuditAction.PROBLEM_CREATED, null);
    }

    /** {@code fields} are the names of the changed {@code ProblemContent} fields. */
    public void recordProblemUpdated(long actorId, long problemId, List<String> fields) {
        String names = String.join(",", fields.stream().map(f -> "\"" + f + "\"").toList());
        insert(actorId, null, problemId, AuditAction.PROBLEM_UPDATED, "{\"fields\":[" + names + "]}");
    }

    public void recordTestsUpdated(long actorId, long problemId, String fromVersion, int fromCount, String toVersion,
            int toCount) {
        insert(actorId, null, problemId, AuditAction.TESTS_UPDATED, "{\"fromVersion\":\"" + fromVersion
                + "\",\"toVersion\":\"" + toVersion + "\",\"fromCount\":" + fromCount + ",\"toCount\":" + toCount
                + "}");
    }

    private void insert(long actorId, @Nullable Long targetUserId, @Nullable Long targetProblemId, AuditAction action,
            @Nullable String details) {
        jdbc.sql("""
                        INSERT INTO audit_log (actor_id, action, target_user_id, target_problem_id, details)
                        VALUES (:actor, :action, :target, :problem, CAST(:details AS jsonb))""")
                .param("actor", actorId)
                .param("action", action.name())
                .param("target", targetUserId)
                .param("problem", targetProblemId)
                .param("details", details)
                .update();
    }

    /** Newest first. */
    public Page<Entry> list(PageRequest page) {
        long total = jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
        List<Entry> items = jdbc.sql("""
                        SELECT a.id, a.created_at, a.action, a.details ->> 'from' AS from_role,
                               a.details ->> 'to' AS to_role, (a.details ->> 'fromCount')::int AS from_count,
                               (a.details ->> 'toCount')::int AS to_count, actor.id AS actor_id,
                               actor.username AS actor_username, target.id AS target_id,
                               target.username AS target_username, p.id AS problem_id,
                               p.display_id AS problem_display_id, p.slug AS problem_slug, p.title AS problem_title
                          FROM audit_log a
                          JOIN users actor ON actor.id = a.actor_id
                          LEFT JOIN users target ON target.id = a.target_user_id
                          LEFT JOIN problems p ON p.id = a.target_problem_id
                         ORDER BY a.id DESC LIMIT :limit OFFSET :offset""")
                .param("limit", page.size())
                .param("offset", page.offset())
                .query((rs, n) -> new Entry(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("actor_id"), rs.getString("actor_username"), account(rs), problem(rs),
                        AuditAction.valueOf(rs.getString("action")), role(rs.getString("from_role")),
                        role(rs.getString("to_role")), rs.getObject("from_count", Integer.class),
                        rs.getObject("to_count", Integer.class)))
                .list();
        return Page.of(items, page, total);
    }

    private static @Nullable Account account(ResultSet rs) throws SQLException {
        long id = rs.getLong("target_id");
        return rs.wasNull() ? null : new Account(id, rs.getString("target_username"));
    }

    private static @Nullable Problem problem(ResultSet rs) throws SQLException {
        long id = rs.getLong("problem_id");
        return rs.wasNull() ? null : new Problem(id, rs.getString("problem_display_id"), rs.getString("problem_slug"),
                rs.getString("problem_title"));
    }

    private static @Nullable UserRole role(@Nullable String name) {
        return name == null ? null : UserRole.valueOf(name);
    }
}
