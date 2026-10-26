package dev.wuoj.user;

import java.util.Optional;

import dev.wuoj.common.domain.UserRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<UserAccount> ROW = (rs, n) -> new UserAccount(rs.getLong("id"),
            rs.getString("username"), rs.getString("password_hash"), UserRole.valueOf(rs.getString("role")),
            rs.getBoolean("enabled"));

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

    public void update(long id, String passwordHash, UserRole role, boolean enabled) {
        jdbc.sql("""
                        UPDATE users SET password_hash = :hash, role = :role, enabled = :enabled, updated_at = now()
                         WHERE id = :id""")
                .param("id", id)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("enabled", enabled)
                .update();
    }
}
