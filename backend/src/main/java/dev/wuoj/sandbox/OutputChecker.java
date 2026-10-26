package dev.wuoj.sandbox;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Compares a program's output with the expected output line by line, ignoring trailing spaces, tabs and carriage
 * returns on each line and blank lines at the end. Every other difference counts, including case and spacing within
 * a line. Streams both files, so large outputs need no more memory than their longest line.
 */
public final class OutputChecker {

    private OutputChecker() {}

    public static boolean matches(Path expected, Path actual) throws IOException {
        try (Lines want = new Lines(Files.newInputStream(expected));
                Lines got = new Lines(Files.newInputStream(actual))) {
            while (true) {
                byte[] a = want.next();
                byte[] b = got.next();
                if (a == null && b == null) return true;
                if (a == null) return b.length == 0 && got.restIsBlank();
                if (b == null) return a.length == 0 && want.restIsBlank();
                if (!Arrays.equals(a, b)) return false;
            }
        }
    }

    /** Lines without their terminator and trailing whitespace; null at the end. */
    private static final class Lines implements AutoCloseable {
        private final InputStream in;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private boolean eof;

        Lines(InputStream in) {
            this.in = new BufferedInputStream(in, 64 * 1024);
        }

        byte[] next() throws IOException {
            if (eof) return null;
            line.reset();
            int c;
            while ((c = in.read()) != -1 && c != '\n') line.write(c);
            if (c == -1) {
                eof = true;
                if (line.size() == 0) return null;
            }
            byte[] bytes = line.toByteArray();
            int end = bytes.length;
            while (end > 0 && (bytes[end - 1] == ' ' || bytes[end - 1] == '\t' || bytes[end - 1] == '\r')) end--;
            return end == bytes.length ? bytes : Arrays.copyOf(bytes, end);
        }

        boolean restIsBlank() throws IOException {
            for (byte[] l = next(); l != null; l = next()) {
                if (l.length != 0) return false;
            }
            return true;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
