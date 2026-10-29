package dev.wuoj.problem;

import java.time.Instant;
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

    private static final String SELECT_FOR_USER = """
            SELECT p.*,
                   CASE WHEN s.solved THEN 'SOLVED' WHEN s.user_id IS NOT NULL THEN 'ATTEMPTED' ELSE 'NONE' END
                       AS user_status
              FROM problems p
              LEFT JOIN user_problem_status s ON s.problem_id = p.id AND s.user_id = :userId""";

    /** Visitors have no status. */
    private static final String SELECT_FOR_VISITOR = "SELECT p.*, 'NONE' AS user_status FROM problems p";

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
        SqlBuilder filter = new SqlBuilder("");
        if (viewer.signedIn()) filter.param("userId", viewer.userId());
        if (!viewer.seesHiddenProblems()) filter.where("p.visible");
        if (query != null) filter.where("p.title ILIKE :q ESCAPE '\\'", "q", SqlBuilder.likeContains(query));
        if (difficulty != null) filter.where("p.difficulty = :difficulty", "difficulty", difficulty.name());

        long total = jdbc.sql("SELECT count(*) FROM problems p" + filter.sql())
                .params(filter.params())
                .query(Long.class)
                .single();
        List<ProblemRow> items = jdbc.sql(select(viewer) + filter.sql() + " " + ORDER + " LIMIT :limit OFFSET :offset")
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
        SqlBuilder sql = new SqlBuilder(select(viewer)).where(condition, "key", key);
        if (viewer.signedIn()) sql.param("userId", viewer.userId());
        if (!viewer.seesHiddenProblems()) sql.where("p.visible");
        return jdbc.sql(sql.sql()).params(sql.params()).query(ROW).optional();
    }

    private static String select(Viewer viewer) {
        return viewer.signedIn() ? SELECT_FOR_USER : SELECT_FOR_VISITOR;
    }

    public List<Sample> samples(long problemId) {
        return jdbc.sql("SELECT input, output FROM problem_samples WHERE problem_id = :id ORDER BY ordinal")
                .param("id", problemId)
                .query((rs, n) -> new Sample(rs.getString("input"), rs.getString("output")))
                .list();
    }

    // --- Editing (importer and admin API) -----------------------------------------------------------------------

    /** Where a problem's last change came from; re-imports don't overwrite web edits without {@code --force}. */
    public enum EditSource {
        IMPORT,
        WEB
    }

    /** A stored problem with its current content and edit bookkeeping, hidden or not. */
    public record Stored(long id, ProblemData data, String testdataVersion, int testCount, int revision,
            EditSource lastEditSource, Instant updatedAt) {}

    /** A row of the admin problem list. */
    public record AdminSummary(long id, String displayId, String slug, String title, Difficulty difficulty,
            boolean visible, int testCount, Instant updatedAt) {}

    /** The problem that already uses a display id or slug. */
    public record Owner(long id, String slug) {}

    private static final String SELECT_STORED = """
            SELECT p.*, t.test_count
              FROM problems p
              JOIN problem_testdata t ON t.problem_id = p.id AND t.version = p.testdata_version""";

    private static final RowMapper<Stored> STORED = (rs, n) -> new Stored(rs.getLong("id"),
            new ProblemData(rs.getString("display_id"), rs.getString("slug"), rs.getString("title"),
                    Difficulty.valueOf(rs.getString("difficulty")), rs.getInt("time_limit_ms"),
                    rs.getInt("memory_limit_mb"), rs.getString("statement_md"), rs.getBoolean("visible")),
            rs.getString("testdata_version"), rs.getInt("test_count"), rs.getInt("revision"),
            EditSource.valueOf(rs.getString("last_edit_source")), rs.getTimestamp("updated_at").toInstant());

    public Optional<Stored> findStored(long id) {
        return jdbc.sql(SELECT_STORED + " WHERE p.id = :id").param("id", id).query(STORED).optional();
    }

    public Optional<Stored> findStoredBySlug(String slug) {
        return jdbc.sql(SELECT_STORED + " WHERE p.slug = :slug").param("slug", slug).query(STORED).optional();
    }

    /** Like {@link #findStored}, and locks the problem until the transaction ends, so edits apply one at a time. */
    public Optional<Stored> lock(long id) {
        return jdbc.sql(SELECT_STORED + " WHERE p.id = :id FOR UPDATE OF p").param("id", id).query(STORED).optional();
    }

    public Optional<Stored> lockBySlug(String slug) {
        return jdbc.sql(SELECT_STORED + " WHERE p.slug = :slug FOR UPDATE OF p")
                .param("slug", slug)
                .query(STORED)
                .optional();
    }

    public Optional<Owner> ownerOfDisplayId(String displayId) {
        return jdbc.sql("SELECT id, slug FROM problems WHERE display_id = :displayId")
                .param("displayId", displayId)
                .query((rs, n) -> new Owner(rs.getLong("id"), rs.getString("slug")))
                .optional();
    }

    public Optional<Owner> ownerOfSlug(String slug) {
        return jdbc.sql("SELECT id, slug FROM problems WHERE slug = :slug")
                .param("slug", slug)
                .query((rs, n) -> new Owner(rs.getLong("id"), rs.getString("slug")))
                .optional();
    }

    /**
     * Hidden problems included, in the public list's order.
     *
     * @param query case-insensitive title substring; {@code %} and {@code _} match literally; null for no filter
     */
    public Page<AdminSummary> adminList(@Nullable String query, @Nullable Boolean visible, PageRequest page) {
        SqlBuilder filter = new SqlBuilder("");
        if (query != null) filter.where("p.title ILIKE :q ESCAPE '\\'", "q", SqlBuilder.likeContains(query));
        if (visible != null) filter.where("p.visible = :visible", "visible", visible);

        long total = jdbc.sql("SELECT count(*) FROM problems p" + filter.sql())
                .params(filter.params())
                .query(Long.class)
                .single();
        List<AdminSummary> items = jdbc.sql(SELECT_STORED + filter.sql() + " " + ORDER
                        + " LIMIT :limit OFFSET :offset")
                .params(filter.params())
                .param("limit", page.size())
                .param("offset", page.offset())
                .query((rs, n) -> new AdminSummary(rs.getLong("id"), rs.getString("display_id"),
                        rs.getString("slug"), rs.getString("title"), Difficulty.valueOf(rs.getString("difficulty")),
                        rs.getBoolean("visible"), rs.getInt("test_count"),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
        return Page.of(items, page, total);
    }

    /** The test-data version must be recorded in the same transaction. */
    public long insert(ProblemData data, String testdataVersion, EditSource source) {
        return jdbc.sql("""
                        INSERT INTO problems (display_id, slug, title, difficulty, time_limit_ms, memory_limit_mb,
                                              statement_md, visible, testdata_version, last_edit_source)
                        VALUES (:displayId, :slug, :title, :difficulty, :timeLimitMs, :memoryLimitMb,
                                :statement, :visible, :version, :source)
                        RETURNING id""")
                .params(dataParams(data))
                .param("version", testdataVersion)
                .param("source", source.name())
                .query(Long.class)
                .single();
    }

    /** Replaces the details, statement and visibility, and increases the revision. */
    public void updateContent(long id, ProblemData data, EditSource source) {
        jdbc.sql("""
                        UPDATE problems
                           SET display_id = :displayId, slug = :slug, title = :title, difficulty = :difficulty,
                               time_limit_ms = :timeLimitMs, memory_limit_mb = :memoryLimitMb,
                               statement_md = :statement, visible = :visible, revision = revision + 1,
                               last_edit_source = :source, updated_at = now()
                         WHERE id = :id""")
                .params(dataParams(data))
                .param("source", source.name())
                .param("id", id)
                .update();
    }

    /** Makes a recorded test-data version the one new submissions are judged against. */
    public void updateTestdataVersion(long id, String testdataVersion, EditSource source) {
        jdbc.sql("""
                        UPDATE problems
                           SET testdata_version = :version, last_edit_source = :source, updated_at = now()
                         WHERE id = :id""")
                .param("version", testdataVersion)
                .param("source", source.name())
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
