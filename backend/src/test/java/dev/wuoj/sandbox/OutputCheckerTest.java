package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputCheckerTest {

    @TempDir
    Path temp;

    private boolean matches(String expected, String actual) throws IOException {
        Path e = Files.writeString(temp.resolve("expected"), expected);
        Path a = Files.writeString(temp.resolve("actual"), actual);
        return OutputChecker.matches(e, a);
    }

    @Test
    void identical() throws IOException {
        assertThat(matches("3\n", "3\n")).isTrue();
        assertThat(matches("", "")).isTrue();
    }

    @Test
    void trailingWhitespaceAndBlankLinesAreTolerated() throws IOException {
        assertThat(matches("3\n", "3   \n\n")).isTrue();
        assertThat(matches("3\n", "3")).isTrue();
        assertThat(matches("1 2\n3 4\n", "1 2\t\n3 4 \n\n\n")).isTrue();
        assertThat(matches("3\n\n\n", "3")).isTrue();
        assertThat(matches("3\r\n4\r\n", "3\n4\n")).isTrue();
        assertThat(matches("", "\n\n  \n")).isTrue();
    }

    @Test
    void innerWhitespaceMatters() throws IOException {
        assertThat(matches("1 2\n", "1  2\n")).isFalse();
        assertThat(matches("1 2\n", " 1 2\n")).isFalse();
    }

    @Test
    void contentAndCaseMatter() throws IOException {
        assertThat(matches("Yes\n", "yes\n")).isFalse();
        assertThat(matches("3\n", "4\n")).isFalse();
        assertThat(matches("3\n4\n", "3\n")).isFalse();
        assertThat(matches("3\n", "3\n4\n")).isFalse();
        assertThat(matches("3\n", "")).isFalse();
    }

    @Test
    void blankLinesInTheMiddleMatter() throws IOException {
        assertThat(matches("1\n2\n", "1\n\n2\n")).isFalse();
    }
}
