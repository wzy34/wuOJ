package dev.wuoj.submission;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.jdbc.SqlBuilder;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Submissions as the API reads and creates them. Judging writes go through the judge package. */
@Repository
public class SubmissionRepository {

    private static final String COLUMNS = """
            s.id, s.user_id, u.username, s.problem_id, p.display_id, p.slug, p.title, s.language, s.status,
            s.verdict, s.time_ms, s.memory_kb, s.current_test, s.total_tests, s.created_at, s.event_seq""";

    private static final String FROM = " " + """
             FROM submissions s
             JOIN users u ON u.id = s.user_id
             JOIN problems p ON p.id = s.problem_id""";

    private final JdbcClient jdbc;

    public SubmissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long userId, long problemId, Language language, String sourceCode, String testdataVersion) {
        return jdbc.sql("""
                        INSERT INTO submissions (user_id, problem_id, language, source_code, testdata_version)
                        VALUES (:userId, :problemId, :language, :sourceCode, :version) RETURNING id""")
                .param("userId", userId)
                .param("problemId", problemId)
                .param("language", language.name())
                .param("sourceCode", sourceCode)
                .param("version", testdataVersion)
                .query(Long.class)
                .single();
    }

    /** One submission, with source code and compiler output; empty if absent or hidden from the viewer. */
    public Optional<SubmissionRow> find(Viewer viewer, long id) {
        SqlBuilder sql = new SqlBuilder("SELECT " + COLUMNS + ", s.source_code, s.compile_output" + FROM)
                .where("s.id = :id", "id", id);
        if (!viewer.seesHiddenProblems()) sql.where("p.visible");
        return jdbc.sql(sql.sql()).params(sql.params()).query((rs, n) -> row(rs, true)).optional();
    }

    /** Newest first. {@code problemSlug}, {@code username} (ignoring case) and {@code verdict} are optional. */
    public Page<SubmissionRow> list(Viewer viewer, boolean onlyMine, @Nullable String problemSlug,
            @Nullable String username, @Nullable Verdict verdict, PageRequest page) {
        SqlBuilder filter = new SqlBuilder(FROM);
        if (onlyMine) filter.where("s.user_id = :userId", "userId", viewer.userId());
        if (!viewer.seesHiddenProblems()) filter.where("p.visible");
        if (problemSlug != null) filter.where("p.slug = :slug", "slug", problemSlug);
        if (username != null) filter.where("lower(u.username) = lower(:username)", "username", username);
        if (verdict != null) filter.where("s.verdict = :verdict", "verdict", verdict.name());

        long total = jdbc.sql("SELECT count(*)" + filter.sql()).params(filter.params()).query(Long.class).single();
        List<SubmissionRow> items = jdbc
                .sql("SELECT " + COLUMNS + filter.sql() + " ORDER BY s.id DESC LIMIT :limit OFFSET :offset")
                .params(filter.params())
                .param("limit", page.size())
                .param("offset", page.offset())
                .query((rs, n) -> row(rs, false))
                .list();
        return Page.of(items, page, total);
    }

    public List<TestResultRow> testResults(long submissionId) {
        return jdbc.sql("""
                        SELECT test_number, verdict, time_ms, memory_kb FROM submission_test_results
                         WHERE submission_id = :id ORDER BY test_number""")
                .param("id", submissionId)
                .query((rs, n) -> new TestResultRow(rs.getInt("test_number"), Verdict.valueOf(rs.getString("verdict")),
                        rs.getInt("time_ms"), rs.getInt("memory_kb")))
                .list();
    }

    public int countUnfinished(long userId) {
        return jdbc.sql("SELECT count(*) FROM submissions WHERE user_id = :userId AND status <> 'FINISHED'")
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    /** Serializes submission creation per user for the rest of the transaction, so limits can't be raced. */
    public void lockUser(long userId) {
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS lock")
                .param("key", userId)
                .query(Integer.class)
                .single();
    }

    private static SubmissionRow row(ResultSet rs, boolean withContent) throws SQLException {
        String verdict = rs.getString("verdict");
        return new SubmissionRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("username"),
                rs.getLong("problem_id"), rs.getString("display_id"), rs.getString("slug"), rs.getString("title"),
                Language.valueOf(rs.getString("language")), SubmissionStatus.valueOf(rs.getString("status")),
                verdict == null ? null : Verdict.valueOf(verdict), rs.getObject("time_ms", Integer.class),
                rs.getObject("memory_kb", Integer.class), rs.getObject("current_test", Integer.class),
                rs.getObject("total_tests", Integer.class), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("event_seq"), withContent ? rs.getString("source_code") : null,
                withContent ? rs.getString("compile_output") : null);
    }
}
