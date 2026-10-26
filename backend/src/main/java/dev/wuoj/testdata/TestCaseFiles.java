package dev.wuoj.testdata;

import java.nio.file.Path;

/** One test of a problem as files on disk: what the program reads and what it must print. */
public record TestCaseFiles(int number, Path input, Path output) {}
