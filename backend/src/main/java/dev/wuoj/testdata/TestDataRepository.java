package dev.wuoj.testdata;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The database side of test-data versions; the files themselves are in the {@link TestDataStore}. */
@Repository
public class TestDataRepository {

    private final JdbcClient jdbc;

    public TestDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Records a version; recording the same version again is a no-op, since versions are immutable. */
    public void saveVersion(long problemId, String version, int testCount, long totalBytes) {
        jdbc.sql("""
                        INSERT INTO problem_testdata (problem_id, version, test_count, total_bytes)
                        VALUES (:problemId, :version, :testCount, :totalBytes)
                        ON CONFLICT (problem_id, version) DO NOTHING""")
                .param("problemId", problemId)
                .param("version", version)
                .param("testCount", testCount)
                .param("totalBytes", totalBytes)
                .update();
    }

    public Optional<Integer> testCount(long problemId, String version) {
        return jdbc.sql("SELECT test_count FROM problem_testdata WHERE problem_id = :problemId AND version = :version")
                .param("problemId", problemId)
                .param("version", version)
                .query(Integer.class)
                .optional();
    }
}
