package dev.wuoj.problem;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.web.FieldErrorDto;
import org.junit.jupiter.api.Test;

class ProblemRulesTest {

    private static final ProblemData VALID = new ProblemData("1001", "a-plus-b", "A + B", Difficulty.EASY, 1000, 256,
            "Print $a + b$.", false);

    private static ProblemData with(String displayId, String slug, String title, Difficulty difficulty, int time,
            int memory, String statement) {
        return new ProblemData(displayId, slug, title, difficulty, time, memory, statement, false);
    }

    @Test
    void validContentHasNoErrors() {
        assertThat(ProblemRules.validate(VALID)).isEmpty();
        assertThat(ProblemRules.validate(with("A_b-9", "x", "T".repeat(200), Difficulty.HARD, 100, 16, "s")))
                .isEmpty();
        assertThat(ProblemRules.validate(with("1", "a".repeat(64), "t", Difficulty.MEDIUM, 10_000, 1024, "s")))
                .isEmpty();
    }

    @Test
    void displayId() {
        for (String id : new String[] {"", "a b", "x".repeat(33), "é", null}) {
            assertThat(ProblemRules.validate(with(id, "s", "t", Difficulty.EASY, 1000, 256, "s")))
                    .as(String.valueOf(id))
                    .extracting(FieldErrorDto::field).containsExactly("displayId");
        }
    }

    @Test
    void slug() {
        for (String slug : new String[] {"A-b", "a--b", "-a", "a-", "a_b", "", null}) {
            assertThat(ProblemRules.validate(with("1", slug, "t", Difficulty.EASY, 1000, 256, "s")))
                    .as(String.valueOf(slug))
                    .extracting(FieldErrorDto::field).containsExactly("slug");
        }
        assertThat(ProblemRules.validate(with("1", "a".repeat(65), "t", Difficulty.EASY, 1000, 256, "s")))
                .singleElement().satisfies(e -> assertThat(e.message()).contains("at most 64"));
    }

    @Test
    void title() {
        for (String title : new String[] {"", "  ", "T".repeat(201), null}) {
            assertThat(ProblemRules.validate(with("1", "s", title, Difficulty.EASY, 1000, 256, "s")))
                    .as(String.valueOf(title))
                    .extracting(FieldErrorDto::field).containsExactly("title");
        }
    }

    @Test
    void limits() {
        assertThat(ProblemRules.validate(with("1", "s", "t", Difficulty.EASY, 99, 1025, "s")))
                .extracting(FieldErrorDto::field).containsExactly("timeLimitMs", "memoryLimitMb");
        assertThat(ProblemRules.validate(with("1", "s", "t", Difficulty.EASY, 10_001, 15, "s")))
                .extracting(FieldErrorDto::field).containsExactly("timeLimitMs", "memoryLimitMb");
    }

    @Test
    void difficultyAndStatement() {
        assertThat(ProblemRules.validate(with("1", "s", "t", null, 1000, 256, " \n")))
                .extracting(FieldErrorDto::field).containsExactly("difficulty", "statement");
    }

    @Test
    void everyErrorIsReported() {
        assertThat(ProblemRules.validate(with("", "", "", null, 0, 0, "")))
                .extracting(FieldErrorDto::field)
                .containsExactly("displayId", "slug", "title", "difficulty", "timeLimitMs", "memoryLimitMb",
                        "statement");
    }
}
