package dev.wuoj.testdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Where hidden tests live. Versions are immutable and named by a hash of their content, so storing the same tests
 * twice yields the same version, and a version in use by a running judge never changes under it.
 */
public interface TestDataStore {

    /** The stored form of a version. */
    record Stored(String version, int testCount, long totalBytes) {}

    /**
     * Stores the tests of a problem (numbered 1..n, in order) as {@code version}, which must be
     * {@link #version(List)} of the same tests; a no-op if that version already exists.
     */
    Stored store(long problemId, String version, List<TestCaseFiles> tests) throws IOException;

    /**
     * A local directory with {@code input/<n>.in} and {@code output/<n>.out} for the version. Only {@code input/} may
     * ever be shown to submitted programs.
     */
    Path materialize(long problemId, String version);

    /** The content version of the tests: SHA-256 over each test's number and the hashes of its two files. */
    static String version(List<TestCaseFiles> tests) {
        MessageDigest digest = sha256();
        for (TestCaseFiles test : tests) {
            digest.update((test.number() + ":" + hash(test.input()) + ":" + hash(test.output()) + "\n").getBytes());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hash(Path file) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStreamSink.INSTANCE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Discards bytes; only the digest matters. */
    final class OutputStreamSink extends java.io.OutputStream {
        static final OutputStreamSink INSTANCE = new OutputStreamSink();

        @Override
        public void write(int b) {}

        @Override
        public void write(byte[] b, int off, int len) {}
    }
}
