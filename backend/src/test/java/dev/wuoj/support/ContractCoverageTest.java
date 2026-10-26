package dev.wuoj.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Runs after every other test class (see junit-platform.properties) and fails if a contract operation was never
 * exercised by the API tests. Skipped when only some tests run.
 */
@Order(Integer.MAX_VALUE)
class ContractCoverageTest {

    @Test
    void everyOperationIsExercised() {
        assumeTrue(Boolean.getBoolean("oj.contractCoverage"), "only checked when the whole test suite runs");
        Set<String> missing = new TreeSet<>(Contract.operationIds());
        missing.removeAll(Contract.exercised());
        System.out.println("Contract operations exercised: " + new TreeSet<>(Contract.exercised()));
        assertThat(missing).as("contract operations no test exercised").isEmpty();
    }
}
