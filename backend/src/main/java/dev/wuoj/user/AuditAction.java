package dev.wuoj.user;

/** What an admin did to an account or a problem (the contract's {@code AuditAction}). */
public enum AuditAction {
    ROLE_CHANGED,
    DISABLED,
    ENABLED,
    PASSWORD_RESET,
    PROBLEM_CREATED,
    PROBLEM_UPDATED,
    TESTS_UPDATED;

    public boolean targetsProblem() {
        return this == PROBLEM_CREATED || this == PROBLEM_UPDATED || this == TESTS_UPDATED;
    }
}
