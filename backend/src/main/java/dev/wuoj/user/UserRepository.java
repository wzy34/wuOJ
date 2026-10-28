package dev.wuoj.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.jdbc.SqlBuilder;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<UserAccount> ROW = (rs, n) -> new UserAccount(rs.getLong("id"),
            rs.getString("username"), rs.getString("password_hash"), UserRole.valueOf(rs.getString("role")),
            rs.getBoolean("enabled"), rs.getInt("session_epoch"), rs.getBoolean("must_change_password"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("last_login_at", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Usernames are unique and matched ignoring case. */
    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM users WHERE lower(username) = lower(:username)")
                .param("username", username)
                .query(ROW)
                .optional();
    }

    public Optional<UserAccount> findById(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id").param("id", id).query(ROW).optional();
    }

    /** Locks the row for the rest of the transaction, so concurrent admin changes apply one after the other. */
    public Optional<UserAccount> findByIdForUpdate(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id FOR UPDATE").param("id", id).query(ROW).optional();
    }

    /**
     * Newest first. {@code query} is a case-insensitive username substring ({@code %} and {@code _} literal);
     * {@code role} and {@code enabled} are optional filters.
     */
    public Page<UserAccount> list(@Nullable String query, @Nullable UserRole role, @Nullable Boolean enabled,
            PageRequest page) {
        SqlBuilder filter = new SqlBuilder(" FROM users");
        if (query != null) filter.where("username ILIKE :q ESCAPE '\\'", "q", SqlBuilder.likeContains(query));
        if (role != null) filter.where("role = :role", "role", role.name());
        if (enabled != null) filter.where("enabled = :enabled", "enabled", enabled);
        long total = jdbc.sql("SELECT count(*)" + filter.sql()).params(filter.params()).query(Long.class).single();
        List<UserAccount> items = jdbc.sql("SELECT *" + filter.sql() + " ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .params(filter.params())
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(ROW)
                .list();
        return Page.of(items, page, total);
    }

    public long insert(String username, String passwordHash, UserRole role, boolean enabled) {
        return jdbc.sql("""
                        INSERT INTO users (username, password_hash, role, enabled)
                        VALUES (:username, :hash, :role, :enabled) RETURNING id""")
                .param("username", username)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("enabled", enabled)
                .query(Long.class)
                .single();
    }

    /** An admin's change of role and enabled flag, ending the account's sessions. */
    public UserAccount updateAccess(long id, UserRole role, boolean enabled) {
        jdbc.sql("""
                        UPDATE users SET role = :role, enabled = :enabled, session_epoch = session_epoch + 1,
                                         updated_at = now()
                         WHERE id = :id""")
                .param("id", id)
                .param("role", role.name())
                .param("enabled", enabled)
                .update();
        return findById(id).orElseThrow();
    }

    /** An admin's password reset: the account must choose a new password, and its sessions end. */
    public void resetPassword(long id, String passwordHash) {
        jdbc.sql("""
                        UPDATE users SET password_hash = :hash, must_change_password = true,
                                         session_epoch = session_epoch + 1, updated_at = now()
                         WHERE id = :id""")
                .param("id", id)
                .param("hash", passwordHash)
                .update();
    }

    /** Sets a password the account chose itself, ending all of its sessions; returns the account as stored now. */
    public UserAccount changePassword(long id, String passwordHash) {
        jdbc.sql("""
                        UPDATE users SET password_hash = :hash, must_change_password = false,
                                         session_epoch = session_epoch + 1, updated_at = now()
                         WHERE id = :id""")
                .param("id", id)
                .param("hash", passwordHash)
                .update();
        return findById(id).orElseThrow();
    }

    public void recordSignIn(long id) {
        jdbc.sql("UPDATE users SET last_login_at = now() WHERE id = :id").param("id", id).update();
    }

    /** Replaces credentials, role and enabled flag (the importer's update), ending the account's sessions. */
    public void update(long id, String passwordHash, UserRole role, boolean enabled) {
        jdbc.sql("""
                        UPDATE users SET password_hash = :hash, role = :role, enabled = :enabled,
                                         session_epoch = session_epoch + 1, updated_at = now()
                         WHERE id = :id""")
                .param("id", id)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("enabled", enabled)
                .update();
    }
}
