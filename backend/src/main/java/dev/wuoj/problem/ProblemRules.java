package dev.wuoj.problem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.wuoj.common.web.FieldErrorDto;

/**
 * What a problem's content must satisfy, whether it comes from a package or from the admin API. Uniqueness of display
 * ids and slugs needs the database and is checked by {@link ProblemEditor}.
 */
public final class ProblemRules {

    public static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    public static final String SLUG_RULE = "lowercase letters and digits separated by single hyphens";
    public static final int MAX_SLUG_LENGTH = 64;
    public static final Pattern DISPLAY_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    public static final String DISPLAY_ID_RULE = "1-32 letters, digits, '-' or '_'";
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MIN_TIME_LIMIT_MS = 100;
    public static final int MAX_TIME_LIMIT_MS = 10_000;
    public static final int MIN_MEMORY_LIMIT_MB = 16;
    public static final int MAX_MEMORY_LIMIT_MB = 1024;
    public static final long MAX_TEST_FILE_BYTES = 64L * 1024 * 1024;
    public static final int MAX_TESTS = 1000;

    private ProblemRules() {}

    /** Every rule the content breaks, one error per field, named as in the contract's {@code ProblemContent}. */
    public static List<FieldErrorDto> validate(ProblemData data) {
        List<FieldErrorDto> errors = new ArrayList<>();
        if (data.displayId() == null || !DISPLAY_ID.matcher(data.displayId()).matches()) {
            errors.add(new FieldErrorDto("displayId", "must be " + DISPLAY_ID_RULE));
        }
        if (data.slug() == null || !SLUG.matcher(data.slug()).matches()) {
            errors.add(new FieldErrorDto("slug", "must be " + SLUG_RULE));
        } else if (data.slug().length() > MAX_SLUG_LENGTH) {
            errors.add(new FieldErrorDto("slug", "must be at most " + MAX_SLUG_LENGTH + " characters"));
        }
        if (data.title() == null || data.title().isBlank()) {
            errors.add(new FieldErrorDto("title", "is required"));
        } else if (data.title().length() > MAX_TITLE_LENGTH) {
            errors.add(new FieldErrorDto("title", "must be at most " + MAX_TITLE_LENGTH + " characters"));
        }
        if (data.difficulty() == null) errors.add(new FieldErrorDto("difficulty", "is required"));
        if (data.timeLimitMs() < MIN_TIME_LIMIT_MS || data.timeLimitMs() > MAX_TIME_LIMIT_MS) {
            errors.add(new FieldErrorDto("timeLimitMs",
                    "must be between " + MIN_TIME_LIMIT_MS + " and " + MAX_TIME_LIMIT_MS));
        }
        if (data.memoryLimitMb() < MIN_MEMORY_LIMIT_MB || data.memoryLimitMb() > MAX_MEMORY_LIMIT_MB) {
            errors.add(new FieldErrorDto("memoryLimitMb",
                    "must be between " + MIN_MEMORY_LIMIT_MB + " and " + MAX_MEMORY_LIMIT_MB));
        }
        if (data.statement() == null || data.statement().isBlank()) {
            errors.add(new FieldErrorDto("statement", "is required"));
        }
        return errors;
    }
}
