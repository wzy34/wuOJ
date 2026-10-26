package dev.wuoj.sandbox;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * What {@code oj-runner run} reports about one run: facts only. {@code oomKilled} means the kernel killed it for lack
 * of memory. {@code error} is set when the runner could not start the program at all (e.g. the binary is missing),
 * which is an infrastructure failure, not a verdict.
 */
public record RunnerResult(int exitCode, int signal, long cpuMs, long wallMs, long maxRssKb, boolean wallTimedOut,
        boolean oomKilled, @Nullable String error) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Parses the runner's single JSON line; throws if the output is not a runner result. */
    public static RunnerResult parse(String output) {
        String line = output.strip();
        int start = line.lastIndexOf("{\"exitCode\"");
        if (start < 0) throw new SandboxException("The runner printed no result: " + abbreviate(line));
        try {
            return JSON.readValue(line.substring(start), RunnerResult.class);
        } catch (JacksonException e) {
            throw new SandboxException("Unreadable runner result: " + abbreviate(line), e);
        }
    }

    public boolean exitedCleanly() {
        return exitCode == 0 && signal == 0;
    }

    private static String abbreviate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
