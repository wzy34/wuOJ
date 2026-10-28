package dev.wuoj.user;

import java.time.Instant;
import java.util.List;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The record of admin actions on accounts. Temporary passwords are never written here. */
@Repository
public class AuditRepository {

    /** {@code fromRole} and {@code toRole} are set for {@link AuditAction#ROLE_CHANGED} only. */
    public record Entry(long id, Instant createdAt, long actorId, String actorUsername, long targetId,
            String targetUsername, AuditAction action, @Nullable UserRole fromRole, @Nullable UserRole toRole) {}

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long actorId, long targetId, AuditAction action) {
        insert(actorId, targetId, action, null);
    }

    public void recordRoleChange(long actorId, long targetId, UserRole from, UserRole to) {
        insert(actorId, targetId, AuditAction.ROLE_CHANGED,
                "{\"from\":\"" + from.name() + "\",\"to\":\"" + to.name() + "\"}");
    }

    private void insert(long actorId, long targetId, AuditAction action, @Nullable String details) {
        jdbc.sql("""
                        INSERT INTO audit_log (actor_id, action, target_user_id, details)
                        VALUES (:actor, :action, :target, CAST(:details AS jsonb))""")
                .param("actor", actorId)
                .param("action", action.name())
                .param("target", targetId)
                .param("details", details)
                .update();
    }

    /** Newest first. */
    public Page<Entry> list(PageRequest page) {
        long total = jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
        List<Entry> items = jdbc.sql("""
                        SELECT a.id, a.created_at, a.action, a.details ->> 'from' AS from_role,
                               a.details ->> 'to' AS to_role, actor.id AS actor_id, actor.username AS actor_username,
                               target.id AS target_id, target.username AS target_username
                          FROM audit_log a
                          JOIN users actor ON actor.id = a.actor_id
                          JOIN users target ON target.id = a.target_user_id
                         ORDER BY a.id DESC LIMIT :limit OFFSET :offset""")
                .param("limit", page.size())
                .param("offset", page.offset())
                .query((rs, n) -> new Entry(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("actor_id"), rs.getString("actor_username"), rs.getLong("target_id"),
                        rs.getString("target_username"), AuditAction.valueOf(rs.getString("action")),
                        role(rs.getString("from_role")), role(rs.getString("to_role"))))
                .list();
        return Page.of(items, page, total);
    }

    private static @Nullable UserRole role(@Nullable String name) {
        return name == null ? null : UserRole.valueOf(name);
    }
}
