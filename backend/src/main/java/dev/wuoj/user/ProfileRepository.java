package dev.wuoj.user;

import java.util.List;
import java.util.Map;

import dev.wuoj.submission.SubmissionDtos.ProblemRef;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A user's progress for their profile. Only visible problems count, whoever asks. */
@Repository
public class ProfileRepository {

    /** Far more than the catalogue holds; only guards against unbounded responses. */
    static final int SOLVED_LIMIT = 500;

    public record Counts(int solved, int attempted, long submissions, long accepted) {}

    private final JdbcClient jdbc;

    public ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts(long userId) {
        Map<String, Object> problems = jdbc.sql("""
                        SELECT count(*) FILTER (WHERE s.solved) AS solved,
                               count(*) FILTER (WHERE NOT s.solved) AS attempted
                          FROM user_problem_status s JOIN problems p ON p.id = s.problem_id
                         WHERE s.user_id = :id AND p.visible""")
                .param("id", userId)
                .query()
                .singleRow();
        Map<String, Object> submissions = jdbc.sql("""
                        SELECT count(*) AS total, count(*) FILTER (WHERE s.verdict = 'AC') AS accepted
                          FROM submissions s JOIN problems p ON p.id = s.problem_id
                         WHERE s.user_id = :id AND p.visible""")
                .param("id", userId)
                .query()
                .singleRow();
        return new Counts(((Number) problems.get("solved")).intValue(), ((Number) problems.get("attempted")).intValue(),
                ((Number) submissions.get("total")).longValue(), ((Number) submissions.get("accepted")).longValue());
    }

    /** Most recently first solved first. */
    public List<ProblemRef> solved(long userId) {
        return jdbc.sql("""
                        SELECT p.id, p.display_id, p.slug, p.title
                          FROM user_problem_status s JOIN problems p ON p.id = s.problem_id
                         WHERE s.user_id = :id AND s.solved AND p.visible
                         ORDER BY s.first_solved_at DESC, p.id
                         LIMIT :limit""")
                .param("id", userId)
                .param("limit", SOLVED_LIMIT)
                .query((rs, n) -> new ProblemRef(rs.getLong("id"), rs.getString("display_id"), rs.getString("slug"),
                        rs.getString("title")))
                .list();
    }
}
