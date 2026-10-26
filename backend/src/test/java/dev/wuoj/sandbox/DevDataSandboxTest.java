package dev.wuoj.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.wuoj.common.domain.Verdict;
import dev.wuoj.importer.ProblemPackageReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every development problem's reference solution is accepted within the problem's own limits. */
@Tag("sandbox")
class DevDataSandboxTest {

    static final Path DEV_DATA = Path.of("../dev-data").toAbsolutePath().normalize();

    static Stream<String> problems() throws Exception {
        try (Stream<Path> dirs = Files.list(DEV_DATA.resolve("problems"))) {
            return dirs.filter(Files::isDirectory).map(d -> d.getFileName().toString()).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("problems")
    void referenceSolutionIsAccepted(String slug, @TempDir Path temp) throws Exception {
        var read = new ProblemPackageReader().read(DEV_DATA.resolve("problems").resolve(slug));
        assertThat(read.errors()).isEmpty();
        var pkg = read.problemPackage();
        String solution = Files.readString(DEV_DATA.resolve("solutions").resolve(slug + ".cpp"));
        try (SandboxHarness harness = new SandboxHarness(temp)) {
            var result = harness.judgeSource(solution, harness.problem(pkg), pkg.data().timeLimitMs(),
                    pkg.data().memoryLimitMb(), null);
            assertThat(result.verdict()).as(slug + " " + result.outcomes()).isEqualTo(Verdict.AC);
            assertThat(result.outcomes()).hasSize(pkg.tests().size());
        }
    }
}
