package dev.wuoj.problem;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.ProblemUserStatus;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.jdbc.SqlBuilder;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProblemRepository {

    /** Numeric display ids sort numerically: shorter first, then by text. */
    private static final String ORDER = "ORDER BY length(p.display_id), p.display_id";

    private static final String SELECT = """
            SELECT p.*,
                   CASE WHEN s.solved THEN 'SOLVED' WHEN s.user_id IS NOT NULL THEN 'ATTEMPTED' ELSE 'NONE' END
                       AS user_status
              FROM problems p
              LEFT JOIN user_problem_status s ON s.problem_id = p.id AND s.user_id = :userId""";

    private static final RowMapper<ProblemRow> ROW = (rs, n) -> new ProblemRow(rs.getLong("id"),
            rs.getString("display_id"), rs.getString("slug"), rs.getString("title"),
            Difficulty.valueOf(rs.getString("difficulty")), rs.getInt("time_limit_ms"),
            rs.getInt("memory_limit_mb"), rs.getString("statement_md"), rs.getBoolean("visible"),
            rs.getString("testdata_version"), ProblemUserStatus.valueOf(rs.getString("user_status")));

    private final JdbcClient jdbc;

    public ProblemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param query case-insensitive title substring; {@code %} and {@code _} match literally; null for no filter
     */
    public Page<ProblemRow> list(Viewer viewer, @Nullable String query, @Nullable Difficulty difficulty,
            PageRequest page) {
        SqlBuilder filter = new SqlBuilder("").param("userId", viewer.userId());
        if (!viewer.seesHiddenProblems()) filter.where("p.visible");
        if (query != null) filter.where("p.title ILIKE :q ESCAPE '\\'", "q", SqlBuilder.likeContains(query));
        if (difficulty != null) filter.where("p.difficulty = :difficulty", "difficulty", difficulty.name());

        long total = jdbc.sql("SELECT count(*) FROM problems p" + filter.sql())
                .params(filter.params())
                .query(Long.class)
                .single();
        List<ProblemRow> items = jdbc.sql(SELECT + filter.sql() + " " + ORDER + " LIMIT :limit OFFSET :offset")
                .params(filter.params())
                .param("limit", page.size())
                .param("offset", page.offset())
                .query(ROW)
                .list();
        return Page.of(items, page, total);
    }

    public Optional<ProblemRow> findBySlug(Viewer viewer, String slug) {
        return find(viewer, "p.slug = :key", slug);
    }

    public Optional<ProblemRow> findById(Viewer viewer, long id) {
        return find(viewer, "p.id = :key", id);
    }

    private Optional<ProblemRow> find(Viewer viewer, String condition, Object key) {
        SqlBuilder sql = new SqlBuilder(SELECT).param("userId", viewer.userId()).where(condition, "key", key);
        if (!viewer.seesHiddenProblems()) sql.where("p.visible");
        return jdbc.sql(sql.sql()).params(sql.params()).query(ROW).optional();
    }

    public List<Sample> samples(long problemId) {
        return jdbc.sql("SELECT input, output FROM problem_samples WHERE problem_id = :id ORDER BY ordinal")
                .param("id", problemId)
                .query((rs, n) -> new Sample(rs.getString("input"), rs.getString("output")))
                .list();
    }

    // --- Import --------------------------------------------------------------------------------------------------

    /** A stored problem with its current content, for comparing against an imported package. */
    public record Stored(long id, ProblemData data, String testdataVersion) {}

    public Optional<Stored> findStoredBySlug(String slug) {
        return jdbc.sql("SELECT * FROM problems WHERE slug = :slug")
                .param("slug", slug)
                .query((rs, n) -> new Stored(rs.getLong("id"), new ProblemData(rs.getString("display_id"),
                        rs.getString("slug"), rs.getString("title"), Difficulty.valueOf(rs.getString("difficulty")),
                        rs.getInt("time_limit_ms"), rs.getInt("memory_limit_mb"), rs.getString("statement_md"),
                        rs.getBoolean("visible")), rs.getString("testdata_version")))
                .optional();
    }

    /** The slug of the problem that uses this display id, if any. */
    public Optional<String> slugOfDisplayId(String displayId) {
        return jdbc.sql("SELECT slug FROM problems WHERE display_id = :displayId")
                .param("displayId", displayId)
                .query(String.class)
                .optional();
    }

    public long insert(ProblemData data, String testdataVersion) {
        return jdbc.sql("""
                        INSERT INTO problems (display_id, slug, title, difficulty, time_limit_ms, memory_limit_mb,
                                              statement_md, visible, testdata_version)
                        VALUES (:displayId, :slug, :title, :difficulty, :timeLimitMs, :memoryLimitMb,
                                :statement, :visible, :version)
                        RETURNING id""")
                .params(dataParams(data))
                .param("version", testdataVersion)
                .query(Long.class)
                .single();
    }

    public void update(long id, ProblemData data, String testdataVersion) {
        jdbc.sql("""
                        UPDATE problems
                           SET display_id = :displayId, slug = :slug, title = :title, difficulty = :difficulty,
                               time_limit_ms = :timeLimitMs, memory_limit_mb = :memoryLimitMb,
                               statement_md = :statement, visible = :visible, testdata_version = :version,
                               updated_at = now()
                         WHERE id = :id""")
                .params(dataParams(data))
                .param("version", testdataVersion)
                .param("id", id)
                .update();
    }

    public void replaceSamples(long problemId, List<Sample> samples) {
        jdbc.sql("DELETE FROM problem_samples WHERE problem_id = :id").param("id", problemId).update();
        for (int i = 0; i < samples.size(); i++) {
            jdbc.sql("""
                            INSERT INTO problem_samples (problem_id, ordinal, input, output)
                            VALUES (:id, :ordinal, :input, :output)""")
                    .param("id", problemId)
                    .param("ordinal", i + 1)
                    .param("input", samples.get(i).input())
                    .param("output", samples.get(i).output())
                    .update();
        }
    }

    private static Map<String, Object> dataParams(ProblemData data) {
        return Map.of("displayId", data.displayId(), "slug", data.slug(), "title", data.title(),
                "difficulty", data.difficulty().name(), "timeLimitMs", data.timeLimitMs(),
                "memoryLimitMb", data.memoryLimitMb(), "statement", data.statement(), "visible", data.visible());
    }
}
