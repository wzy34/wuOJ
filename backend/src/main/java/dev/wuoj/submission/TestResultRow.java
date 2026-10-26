package dev.wuoj.submission;

import dev.wuoj.common.domain.Verdict;

public record TestResultRow(int testNumber, Verdict verdict, int timeMs, int memoryKb) {}
