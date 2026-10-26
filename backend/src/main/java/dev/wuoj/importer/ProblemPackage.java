package dev.wuoj.importer;

import java.nio.file.Path;
import java.util.List;

import dev.wuoj.problem.ProblemData;
import dev.wuoj.problem.Sample;
import dev.wuoj.testdata.TestCaseFiles;

/** A validated problem package: everything needed to create or update a problem. */
public record ProblemPackage(Path directory, ProblemData data, List<Sample> samples, List<TestCaseFiles> tests) {}
