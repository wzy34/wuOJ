package dev.wuoj.problem;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The contract's {@code TestManifest}: the new ordered list of tests, each file kept from {@code baseVersion} or
 * uploaded. Fields are nullable because the manifest arrives as client JSON; {@link TestSetAssembler} checks it.
 */
public record TestManifest(@Nullable String baseVersion, @Nullable List<@Nullable Entry> tests) {

    public record Entry(@Nullable FileRef input, @Nullable FileRef output) {}

    /** Exactly one of {@code keep} (a test number of the base version) and {@code upload} (an index into files). */
    public record FileRef(@Nullable Integer keep, @Nullable Integer upload) {}
}
