package dev.wuoj.testdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.wuoj.common.config.OjProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemTestDataStoreTest {

    @TempDir
    Path temp;

    private FileSystemTestDataStore store(Path root) {
        return new FileSystemTestDataStore(new OjProperties(List.of(), new OjProperties.TestData(root),
                new OjProperties.Judge("i", null, null, List.of(), temp, false, Duration.ZERO),
                new OjProperties.RateLimit(3, Duration.ofSeconds(10), 2, Duration.ofSeconds(5), 30,
                        Duration.ofMinutes(1)),
                new OjProperties.LoginThrottle(5, 50, Duration.ofMinutes(5)),
                new OjProperties.Registration(true, 5, Duration.ofHours(1))));
    }

    private List<TestCaseFiles> tests(String dir, String... pairs) throws IOException {
        Path base = Files.createDirectories(temp.resolve(dir));
        List<TestCaseFiles> tests = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            int n = i / 2 + 1;
            Path in = Files.writeString(base.resolve(n + ".in"), pairs[i]);
            Path out = Files.writeString(base.resolve(n + ".out"), pairs[i + 1]);
            tests.add(new TestCaseFiles(n, in, out));
        }
        return tests;
    }

    @Test
    void identicalContentHasTheSameVersion() throws IOException {
        String a = TestDataStore.version(tests("a", "1 2\n", "3\n", "5 5\n", "10\n"));
        String b = TestDataStore.version(tests("b", "1 2\n", "3\n", "5 5\n", "10\n"));
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void oneByteChangesTheVersion() throws IOException {
        String a = TestDataStore.version(tests("a", "1 2\n", "3\n"));
        String b = TestDataStore.version(tests("b", "1 2\n", "4\n"));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void storesInputsAndOutputsSeparately() throws IOException {
        FileSystemTestDataStore store = store(temp.resolve("root"));
        var tests = tests("a", "1 2\n", "3\n", "5 5\n", "10\n");
        TestDataStore.Stored stored = store.store(7, TestDataStore.version(tests), tests);
        Path dir = store.materialize(7, stored.version());

        assertThat(stored.testCount()).isEqualTo(2);
        assertThat(stored.totalBytes()).isEqualTo(4 + 2 + 4 + 3);
        assertThat(dir.resolve("input/2.in")).hasContent("5 5");
        assertThat(dir.resolve("output/2.out")).hasContent("10");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("input/1.in"))))
                .isEqualTo("rw-r--r--");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("input"))))
                .isEqualTo("rwxr-xr-x");
    }

    @Test
    void storingTheSameVersionAgainIsANoOp() throws IOException {
        FileSystemTestDataStore store = store(temp.resolve("root"));
        var a = tests("a", "x\n", "y\n");
        var b = tests("b", "x\n", "y\n");
        String first = store.store(7, TestDataStore.version(a), a).version();
        String second = store.store(7, TestDataStore.version(b), b).version();
        assertThat(second).isEqualTo(first);
        try (var entries = Files.list(temp.resolve("root/7"))) {
            assertThat(entries.toList()).containsExactly(store.materialize(7, first));
        }
    }

    @Test
    void failedCopyMidwayLeavesNoPartialVersion() throws IOException {
        FileSystemTestDataStore store = store(temp.resolve("root"));
        List<TestCaseFiles> tests = new ArrayList<>(tests("a", "1\n", "1\n"));
        // Test 1 is copied, then test 2 fails: its input does not exist.
        tests.add(new TestCaseFiles(2, temp.resolve("a/missing.in"), temp.resolve("a/1.out")));

        assertThatThrownBy(() -> store.store(7, "abc123", tests)).isInstanceOf(NoSuchFileException.class);
        assertThat(store.materialize(7, "abc123")).doesNotExist();
        try (var entries = Files.list(temp.resolve("root/7"))) {
            assertThat(entries.toList()).isEmpty();
        }
    }
}
