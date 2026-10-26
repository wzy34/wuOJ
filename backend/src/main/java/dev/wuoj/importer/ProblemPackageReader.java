package dev.wuoj.importer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.problem.ProblemData;
import dev.wuoj.problem.Sample;
import dev.wuoj.testdata.TestCaseFiles;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads a problem package directory and checks all of it, collecting every problem found instead of stopping at the
 * first:
 *
 * <pre>
 * problem.yaml   displayId, slug, title, difficulty, timeLimitMs, memoryLimitMb, visible (optional, default false)
 * statement.md
 * samples/1.in, samples/1.out, ...   (optional)
 * tests/1.in, tests/1.out, ...       (at least one)
 * </pre>
 */
public class ProblemPackageReader {

    public static final long MAX_TEST_FILE_BYTES = 64L * 1024 * 1024;
    static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    static final Pattern DISPLAY_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern CASE_FILE = Pattern.compile("([1-9][0-9]*)\\.(in|out)");
    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /** Either a package or the list of problems that prevented reading one. */
    public record Result(@Nullable ProblemPackage problemPackage, List<String> errors) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public Result read(Path directory) {
        List<String> errors = new ArrayList<>();
        ProblemData data = readMetadata(directory, errors);
        List<Sample> samples = readSamples(directory, errors);
        List<TestCaseFiles> tests = readTests(directory, errors);
        if (!errors.isEmpty() || data == null) return new Result(null, List.copyOf(errors));
        return new Result(new ProblemPackage(directory, data, samples, tests), List.of());
    }

    private static @Nullable ProblemData readMetadata(Path directory, List<String> errors) {
        Path yamlFile = directory.resolve("problem.yaml");
        Map<String, Object> yaml = Map.of();
        if (!Files.isRegularFile(yamlFile)) {
            errors.add("problem.yaml: missing");
        } else {
            try {
                Object parsed = YAML.readValue(yamlFile.toFile(), Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fields = (Map<String, Object>) map;
                    yaml = fields;
                } else {
                    errors.add("problem.yaml: must be a mapping of fields");
                }
            } catch (JacksonException e) {
                errors.add("problem.yaml: not valid YAML (" + e.getOriginalMessage() + ")");
            }
        }

        Fields fields = new Fields(yaml, errors);
        String displayId = fields.string("displayId", DISPLAY_ID, "1-32 letters, digits, '-' or '_'");
        String slug = fields.string("slug", SLUG, "lowercase letters and digits separated by single hyphens");
        if (slug != null && slug.length() > 64) errors.add("problem.yaml: slug must be at most 64 characters");
        String title = fields.string("title", null, null);
        if (title != null && title.length() > 200) errors.add("problem.yaml: title must be at most 200 characters");
        Difficulty difficulty = fields.difficulty();
        Integer timeLimit = fields.integer("timeLimitMs", 100, 10_000);
        Integer memoryLimit = fields.integer("memoryLimitMb", 16, 1024);
        boolean visible = fields.bool("visible");

        String statement = readText(directory.resolve("statement.md"), "statement.md", errors);
        if (statement != null && statement.isBlank()) errors.add("statement.md: is empty");

        if (displayId == null || slug == null || title == null || difficulty == null || timeLimit == null
                || memoryLimit == null || statement == null) {
            return null;
        }
        return new ProblemData(displayId, slug, title, difficulty, timeLimit, memoryLimit, statement, visible);
    }

    private static List<Sample> readSamples(Path directory, List<String> errors) {
        Path dir = directory.resolve("samples");
        if (!Files.exists(dir)) return List.of();
        List<Sample> samples = new ArrayList<>();
        for (TestCaseFiles pair : pairs(dir, "samples", errors)) {
            String input = readText(pair.input(), "samples/" + pair.number() + ".in", errors);
            String output = readText(pair.output(), "samples/" + pair.number() + ".out", errors);
            if (input != null && output != null) samples.add(new Sample(input, output));
        }
        return samples;
    }

    private static List<TestCaseFiles> readTests(Path directory, List<String> errors) {
        Path dir = directory.resolve("tests");
        if (!Files.isDirectory(dir)) {
            errors.add("tests/: missing (at least one test is required)");
            return List.of();
        }
        List<TestCaseFiles> tests = pairs(dir, "tests", errors);
        if (tests.isEmpty() && errors.stream().noneMatch(e -> e.startsWith("tests/"))) {
            errors.add("tests/: at least one test is required");
        }
        for (TestCaseFiles test : tests) {
            checkSize(test.input(), "tests/" + test.number() + ".in", errors);
            checkSize(test.output(), "tests/" + test.number() + ".out", errors);
        }
        return tests;
    }

    /** The {@code <n>.in}/{@code <n>.out} pairs of a directory, numbered 1..n without gaps. */
    private static List<TestCaseFiles> pairs(Path dir, String label, List<String> errors) {
        TreeMap<Integer, Path> inputs = new TreeMap<>();
        TreeMap<Integer, Path> outputs = new TreeMap<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.sorted().toList()) {
                String name = entry.getFileName().toString();
                Matcher m = CASE_FILE.matcher(name);
                if (!m.matches() || !Files.isRegularFile(entry)) {
                    errors.add(label + "/" + name + ": not a test file (expected <n>.in or <n>.out)");
                    continue;
                }
                int number = Integer.parseInt(m.group(1));
                (m.group(2).equals("in") ? inputs : outputs).put(number, entry);
            }
        } catch (IOException e) {
            errors.add(label + "/: cannot be read (" + e.getMessage() + ")");
            return List.of();
        }

        List<TestCaseFiles> pairs = new ArrayList<>();
        int last = Math.max(inputs.isEmpty() ? 0 : inputs.lastKey(), outputs.isEmpty() ? 0 : outputs.lastKey());
        for (int n = 1; n <= last; n++) {
            Path in = inputs.get(n);
            Path out = outputs.get(n);
            if (in == null && out == null) {
                errors.add(label + "/" + n + ".in: missing (numbering must start at 1 and have no gaps)");
            } else if (in == null) {
                errors.add(label + "/" + n + ".in: missing (" + label + "/" + n + ".out has no input)");
            } else if (out == null) {
                errors.add(label + "/" + n + ".out: missing (" + label + "/" + n + ".in has no expected output)");
            } else {
                pairs.add(new TestCaseFiles(n, in, out));
            }
        }
        return pairs;
    }

    private static void checkSize(Path file, String label, List<String> errors) {
        try {
            if (Files.size(file) > MAX_TEST_FILE_BYTES) errors.add(label + ": larger than 64 MB");
        } catch (IOException e) {
            errors.add(label + ": cannot be read (" + e.getMessage() + ")");
        }
    }

    private static @Nullable String readText(Path file, String label, List<String> errors) {
        if (!Files.isRegularFile(file)) {
            errors.add(label + ": missing");
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            errors.add(label + ": is not UTF-8 text");
        } catch (IOException e) {
            errors.add(label + ": cannot be read (" + e.getMessage() + ")");
        }
        return null;
    }

    /** Typed access to {@code problem.yaml} fields that records an error instead of throwing. */
    private record Fields(Map<String, Object> yaml, List<String> errors) {

        @Nullable String string(String name, @Nullable Pattern pattern, @Nullable String rule) {
            Object value = yaml.get(name);
            if (value == null || value.toString().isBlank()) {
                errors.add("problem.yaml: " + name + " is required");
                return null;
            }
            String text = value.toString().strip();
            if (pattern != null && !pattern.matcher(text).matches()) {
                errors.add("problem.yaml: " + name + " '" + text + "' is invalid (" + rule + ")");
                return null;
            }
            return text;
        }

        @Nullable Integer integer(String name, int min, int max) {
            Object value = yaml.get(name);
            if (value == null) {
                errors.add("problem.yaml: " + name + " is required");
                return null;
            }
            if (!(value instanceof Integer number)) {
                errors.add("problem.yaml: " + name + " must be a whole number");
                return null;
            }
            if (number < min || number > max) {
                errors.add("problem.yaml: " + name + " must be between " + min + " and " + max + ", got " + number);
                return null;
            }
            return number;
        }

        @Nullable Difficulty difficulty() {
            String value = string("difficulty", null, null);
            if (value == null) return null;
            try {
                return Difficulty.valueOf(value);
            } catch (IllegalArgumentException e) {
                errors.add("problem.yaml: difficulty '" + value + "' is invalid (EASY, MEDIUM or HARD)");
                return null;
            }
        }

        boolean bool(String name) {
            Object value = yaml.get(name);
            if (value == null) return false;
            if (value instanceof Boolean b) return b;
            errors.add("problem.yaml: " + name + " must be true or false");
            return false;
        }
    }
}
