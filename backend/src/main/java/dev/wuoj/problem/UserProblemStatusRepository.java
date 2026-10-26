package dev.wuoj.problem;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Keeps each user's SOLVED / ATTEMPTED status per problem up to date as submissions are made and judged. */
@Repository
public class UserProblemStatusRepository {

    private final JdbcClient jdbc;

    public UserProblemStatusRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void markAttempted(long userId, long problemId) {
        jdbc.sql("""
                        INSERT INTO user_problem_status (user_id, problem_id) VALUES (:userId, :problemId)
                        ON CONFLICT (user_id, problem_id) DO NOTHING""")
                .param("userId", userId)
                .param("problemId", problemId)
                .update();
    }

    public void markSolved(long userId, long problemId) {
        jdbc.sql("""
                        INSERT INTO user_problem_status (user_id, problem_id, solved, first_solved_at)
                        VALUES (:userId, :problemId, true, now())
                        ON CONFLICT (user_id, problem_id) DO UPDATE
                           SET solved = true,
                               first_solved_at = coalesce(user_problem_status.first_solved_at, now())""")
                .param("userId", userId)
                .param("problemId", problemId)
                .update();
    }
}
